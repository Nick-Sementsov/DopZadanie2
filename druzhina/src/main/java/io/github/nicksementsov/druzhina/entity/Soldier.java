package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.druzhina.entity.ai.CommanderTargetGoal;
import io.github.nicksementsov.druzhina.entity.ai.DuelGoal;
import io.github.nicksementsov.druzhina.entity.ai.FollowCommanderGoal;
import io.github.nicksementsov.druzhina.entity.ai.HoldPostGoal;
import io.github.nicksementsov.druzhina.entity.ai.PatrolGoal;
import io.github.nicksementsov.druzhina.api.Relations;
import io.github.nicksementsov.druzhina.registry.DruzhinaAttributes;
import io.github.nicksementsov.druzhina.entity.ai.VolleyGoal;
import io.github.nicksementsov.knightcombat.api.CombatFighter;
import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier: a man with a weapon who fights by the rules of Knightly Combat, as a player does — wind-ups, blocks,
 * parries, combos, kicks, stamina. A hired one serves the player who hired him and does what he is told; a bandit
 * serves nobody. Soldiers who live through fights become veterans and fight better.
 */
public class Soldier extends PathfinderMob implements CombatFighter, OwnableEntity, RangedAttackMob {
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORDERS = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> MANAGED_DATA = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.BOOLEAN);
    public static final int SKINS = 8;
    public static final int BANDIT_SKINS = 4;
    public static final int NAMES = 40;
    /** Kills needed for each rank above recruit. */
    private static final int[] RANK_KILLS = {3, 10, 25};
    private static final int HEAL_DELAY = 200;
    private static final String MANAGED = "druzhina.managed";
    private static final String NAMED = "druzhina.named";

    @Nullable
    private Vec3 post;
    private float postYaw;
    private int kills;
    private int nameIndex = -1;
    /** Slots holding items the commander handed over: they fall to the ground when the soldier dies. */
    private int given;
    private long lastFight;
    private long dismissAsked = Long.MIN_VALUE / 2;
    /** Whom he follows when it is not his commander (a guard captain's rally, a lord's escort), or null. */
    @Nullable
    private UUID leader;
    /** Places he walks between, back and forth, when he patrols. */
    private final List<Vec3> patrol = new ArrayList<>();
    private int patrolIndex;
    private int patrolStep = 1;

    public Soldier(EntityType<? extends Soldier> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            // like a player's bare hand: the weapon adds the rest
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ARMOR, 0.0)
            .add(DruzhinaAttributes.RANGED_DAMAGE.get());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ROLE, 0);
        this.entityData.define(SKIN, 0);
        this.entityData.define(ORDERS, Orders.FOLLOW.ordinal());
        this.entityData.define(RANK, 0);
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(MANAGED_DATA, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DuelGoal(this));
        this.goalSelector.addGoal(1, new VolleyGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(3, new HoldPostGoal(this));
        this.goalSelector.addGoal(3, new PatrolGoal(this));
        this.goalSelector.addGoal(4, new FollowCommanderGoal(this));
        this.goalSelector.addGoal(6, new BanditStrollGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new CommanderTargetGoal(this));
        // monsters for the hired, bandits for everyone else
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, this::isFoe));
        // bandits hate everyone; soldiers hate the commanders their own commander is at war with, and his outlaws
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> this.withinReach(p) && (this.isBandit() || !this.isBandit() && this.getOwnerUUID() != null
                        && Relations.between(this.getOwnerUUID(), p.getUUID()) == Relations.Stance.ENEMY)));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, 10, true, false, v -> this.isBandit()));
    }

    /** Whom this soldier attacks on sight. */
    private boolean isFoe(LivingEntity e) {
        if (!this.withinReach(e) || this.routed()) {
            return false;
        } else if (e instanceof Soldier other) {
            return this.isBandit() != other.isBandit()
                    || !this.isBandit() && Relations.between(this.getOwnerUUID(), other.getOwnerUUID()) == Relations.Stance.ENEMY;
        } else if (this.isBandit()) {
            return false;
        } else {
            return e instanceof Enemy && !(e instanceof Creeper);
        }
    }

    /**
     * Whether he may go after {@code e} under his orders: a soldier holding a post does not leave it far behind, one
     * following his commander does not run off from him; charging soldiers and bandits go after anyone.
     */
    public boolean withinReach(LivingEntity e) {
        if (this.isBandit() || this.getOwnerUUID() == null) {
            return true;
        }
        double leash = this.role().ranged ? 26 : 16;
        return switch (this.orders()) {
            case CHARGE -> true;
            case PATROL -> {
                double best = Double.MAX_VALUE;
                for (Vec3 p : this.patrol) {
                    best = Math.min(best, e.distanceToSqr(p));
                }
                yield best < (leash + 4) * (leash + 4);
            }
            case HOLD -> this.post == null || e.distanceToSqr(this.post) < leash * leash;
            case FOLLOW -> {
                Player boss = this.commander();
                yield boss == null ? e.distanceToSqr(this) < leash * leash : e.distanceToSqr(boss) < (leash + 4) * (leash + 4);
            }
        };
    }

    // ------------------------------------------------------------------ who he is

    public Role role() {
        return Role.byId(this.entityData.get(ROLE));
    }

    public boolean isBandit() {
        return this.role().bandit;
    }

    public int skin() {
        return this.entityData.get(SKIN);
    }

    public Orders orders() {
        return Orders.byId(this.entityData.get(ORDERS));
    }

    public int rank() {
        return this.entityData.get(RANK);
    }

    public int kills() {
        return this.kills;
    }

    /** How well he fights: his role's skill and a tenth more for every rank. */
    public float skill() {
        return Math.min(0.95f, this.role().skill + 0.1f * this.rank());
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    /** Whom he follows: the one he was told to follow, or else his commander. */
    @Nullable
    public Player commander() {
        UUID id = this.leader != null ? this.leader : this.getOwnerUUID();
        return id == null ? null : this.level().getPlayerByUUID(id);
    }

    /** The player who hired him (or whose realm he serves). */
    @Nullable
    public Player owner() {
        UUID id = this.getOwnerUUID();
        return id == null ? null : this.level().getPlayerByUUID(id);
    }

    public List<Vec3> patrol() {
        return this.patrol;
    }

    /** The next place of his round, and turns back at the ends of it. */
    @Nullable
    public Vec3 nextWaypoint(boolean reached) {
        if (this.patrol.isEmpty()) {
            return null;
        }
        if (reached) {
            if (this.patrol.size() == 1) {
                this.patrolIndex = 0;
            } else {
                if (this.patrolIndex + this.patrolStep < 0 || this.patrolIndex + this.patrolStep >= this.patrol.size()) {
                    this.patrolStep = -this.patrolStep;
                }
                this.patrolIndex += this.patrolStep;
            }
        }
        this.patrolIndex = Math.max(0, Math.min(this.patrol.size() - 1, this.patrolIndex));
        return this.patrol.get(this.patrolIndex);
    }

    /** Kept by another mod (a realm's garrison): the baton does not move him and he cannot be sent home. */
    public boolean managed() {
        return this.entityData.get(MANAGED_DATA);
    }

    public void setManaged(boolean managed) {
        this.getPersistentData().putBoolean(MANAGED, managed);
        this.entityData.set(MANAGED_DATA, managed);
    }

    /** Gives him a name of another mod's choosing, kept through promotions. */
    public void setOwnName(Component name) {
        this.getPersistentData().putBoolean(NAMED, true);
        this.setCustomName(name);
        this.setCustomNameVisible(false);
    }

    /** His spirit is broken: he runs instead of fighting. */
    public boolean routed() {
        return Relations.routed(this);
    }

    @Nullable
    public Vec3 post() {
        return this.post;
    }

    public float postYaw() {
        return this.postYaw;
    }

    /** Sets up a new soldier of {@code role}, serving {@code owner} (or nobody: a bandit). */
    public void enlist(Role role, @Nullable UUID owner, RandomSource rnd) {
        this.enlist(role, owner, role.grade, rnd);
    }

    /** Sets up a new soldier of {@code role} with a kit of the given grade (see {@link Kits}). */
    public void enlist(Role role, @Nullable UUID owner, int grade, RandomSource rnd) {
        this.entityData.set(ROLE, role.ordinal());
        this.entityData.set(SKIN, rnd.nextInt(role.bandit ? BANDIT_SKINS : SKINS));
        this.entityData.set(OWNER, Optional.ofNullable(owner));
        this.nameIndex = rnd.nextInt(NAMES);
        // Knightly Combat draws the blows from the right hand
        this.setLeftHanded(false);
        Kits.equip(this, role, grade, rnd);
        if (!role.bandit) {
            // a soldier stays in the world; bandits who are left far behind go their way
            this.setPersistenceRequired();
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // what a soldier comes with is his own and stays with him
            this.setDropChance(slot, role.bandit ? 0.08f : 0.0f);
        }

        this.applyRank();
        this.setHealth(this.getMaxHealth());
        this.rename();
    }

    public void order(Orders orders, @Nullable Vec3 post, float yaw) {
        this.entityData.set(ORDERS, orders.ordinal());
        this.post = post;
        this.postYaw = yaw;
        this.leader = null;
        if (orders != Orders.PATROL) {
            this.patrol.clear();
        }
        if (orders != Orders.CHARGE) {
            this.setTarget(null);
        }
    }

    /** Follow {@code leader} (null: his commander). */
    public void follow(@Nullable UUID leader) {
        this.order(Orders.FOLLOW, null, 0);
        this.leader = leader;
    }

    /** Walk the round through {@code points} and back, and fight what comes near it. */
    public void patrol(List<Vec3> points) {
        if (points.isEmpty()) {
            return;
        }
        this.order(Orders.PATROL, points.get(0), 0);
        this.patrol.clear();
        this.patrol.addAll(points);
        this.patrolIndex = 0;
        this.patrolStep = 1;
    }

    /** One rank up (no further than the highest), with what a rank gives. */
    public void promote() {
        int rank = this.rank();
        if (rank < RANK_KILLS.length) {
            this.entityData.set(RANK, rank + 1);
            this.kills = Math.max(this.kills, RANK_KILLS[rank]);
            this.applyRank();
            this.heal(4.0f);
            this.rename();
        }
    }

    /** Sets his rank outright (0 to 3), as for a veteran raised from a seasoned garrison. */
    public void setRank(int rank) {
        int r = Math.max(0, Math.min(RANK_KILLS.length, rank));
        this.entityData.set(RANK, r);
        this.kills = r == 0 ? 0 : Math.max(this.kills, RANK_KILLS[r - 1]);
        this.applyRank();
        this.setHealth(this.getMaxHealth());
        this.rename();
    }

    public static int maxRank() {
        return RANK_KILLS.length;
    }

    private void applyRank() {
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(this.role().health + 2.0 * this.rank());
        }
    }

    /** Just his name. */
    public Component plainName() {
        return Component.translatable("druzhina.name." + Math.max(0, this.nameIndex));
    }

    /** The name over his head: his own, his role and, once he has earned it, his rank. */
    private void rename() {
        if (this.getPersistentData().getBoolean(NAMED) && this.hasCustomName()) {
            // named by someone else (a conscript keeps the name he had at home)
            return;
        }
        if (this.nameIndex < 0) {
            this.nameIndex = this.random.nextInt(NAMES);
        }

        MutableComponent name = Component.translatable("druzhina.name." + this.nameIndex);
        name.append(" · ").append(Component.translatable("druzhina.role." + this.role().id()));
        if (this.rank() > 0) {
            name.append(" · ").append(Component.translatable("druzhina.rank." + this.rank()));
        }

        this.setCustomName(name.withStyle(this.isBandit() ? ChatFormatting.DARK_RED : ChatFormatting.WHITE));
        this.setCustomNameVisible(false);
    }

    // ------------------------------------------------------------------ friends and foes

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == this) {
            return true;
        } else if (other instanceof Soldier s) {
            if (this.isBandit() || s.isBandit()) {
                return this.isBandit() && s.isBandit();
            }

            // men of the same commander (or men of nobody, the free companies) stand together, and so do allies
            return Relations.between(this.getOwnerUUID(), s.getOwnerUUID()) == Relations.Stance.ALLY || super.isAlliedTo(other);
        } else {
            UUID mine = this.getOwnerUUID();
            if (mine != null && other instanceof Player && Relations.between(mine, other.getUUID()) == Relations.Stance.ALLY) {
                return true;
            } else if (mine != null && other instanceof OwnableEntity pet && mine.equals(pet.getOwnerUUID())) {
                return true;
            } else {
                return super.isAlliedTo(other);
            }
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isAlliedTo(target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        // arrows and stray blows of his own side do not hurt him
        if (by != null && by != this && this.isAlliedTo(by)) {
            return false;
        }

        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            this.lastFight = this.level().getGameTime();
        }

        return hurt;
    }

    /** Called when someone he fought dies by his hand: enough kills and he rises in rank. */
    public void scoredKill(LivingEntity victim) {
        this.lastFight = this.level().getGameTime();
        // a man or a real monster, not a chicken or a little slime
        if (!(victim instanceof Player || victim instanceof Soldier || victim instanceof Enemy && victim.getMaxHealth() >= 8)) {
            return;
        }

        this.kills++;
        int rank = this.rank();
        if (rank < RANK_KILLS.length && this.kills >= RANK_KILLS[rank]) {
            this.entityData.set(RANK, rank + 1);
            this.applyRank();
            this.heal(4.0f);
            this.rename();
            this.playSound(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
            Player boss = this.owner();
            if (boss != null) {
                boss.displayClientMessage(Component.translatable("druzhina.msg.promoted", this.plainName(), Component.translatable("druzhina.rank." + (rank + 1))).withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    public boolean fightingRecently() {
        return this.getTarget() != null || this.level().getGameTime() - this.lastFight < HEAL_DELAY;
    }

    // ------------------------------------------------------------------ life

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.nameIndex < 0) {
            // summoned without being set up (a command with data, a spawner): he gets his arms now
            this.enlist(this.role(), this.getOwnerUUID(), this.random);
        }
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive() && this.tickCount % 40 == 0 && !this.fightingRecently() && this.getHealth() < this.getMaxHealth()) {
            // wounds close out of the fight
            this.heal(1.0f);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide) {
            Player boss = this.owner();
            if (boss != null) {
                boss.displayClientMessage(Component.translatable("druzhina.msg.fallen", this.getDisplayName()).withStyle(ChatFormatting.RED), false);
            }

            // what the commander handed over is left on the ground
            this.dropGiven();
        }

        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return this.isBandit() && !this.hasCustomRank() && distance > 96 * 96;
    }

    private boolean hasCustomRank() {
        return this.rank() > 0;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        if (this.isBandit()) {
            // the band's purse: gold to hire men with
            int gold = this.role() == Role.CHIEFTAIN ? 5 + this.random.nextInt(5 + looting) : 1 + this.random.nextInt(2 + looting);
            if (gold > 0) {
                this.spawnAtLocation(new ItemStack(Items.GOLD_INGOT, gold));
            }
        }
    }

    // ------------------------------------------------------------------ the commander's hand

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getUUID().equals(this.getOwnerUUID())) {
            return super.mobInteract(player, hand);
        } else if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            ItemStack held = player.getItemInHand(hand);
            EquipmentSlot slot = slotFor(held);
            if (held.isEmpty() && player.isShiftKeyDown() && !this.managed()) {
                // Shift and an empty hand, twice: he is let go
                long now = this.level().getGameTime();
                if (now - this.dismissAsked < 60) {
                    this.dismiss(player);
                } else {
                    this.dismissAsked = now;
                    player.displayClientMessage(Component.translatable("druzhina.msg.dismiss_confirm", this.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
                }
            } else if (slot != null) {
                // a weapon, a shield or a piece of armour from the commander's hand
                ItemStack old = this.getItemBySlot(slot);
                this.setItemSlot(slot, held.copyWithCount(1));
                held.shrink(1);
                this.given |= 1 << slot.ordinal();
                if (!old.isEmpty() && !player.getInventory().add(old)) {
                    player.drop(old, false);
                }

                this.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8f, 1.0f);
                player.displayClientMessage(Component.translatable("druzhina.msg.equipped", this.plainName(), this.getItemBySlot(slot).getHoverName()), true);
            } else {
                player.displayClientMessage(this.status(), false);
            }

            return InteractionResult.CONSUME;
        }
    }

    /** Lets him go home: what the commander gave him is left behind. */
    private void dismiss(Player player) {
        this.dropGiven();
        player.displayClientMessage(Component.translatable("druzhina.msg.dismissed", this.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, this.getX(), this.getY() + 1, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.8f);
        this.discard();
    }

    /** Puts what the commander handed over on the ground. */
    private void dropGiven() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if ((this.given & 1 << slot.ordinal()) != 0) {
                ItemStack stack = this.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack.copy());
                    this.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        this.given = 0;
    }

    /** Where an item handed over goes, or null when it is nothing a soldier carries. */
    @Nullable
    private EquipmentSlot slotFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        } else if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot();
        } else if (stack.getItem() instanceof ShieldItem && !this.role().ranged) {
            return EquipmentSlot.OFFHAND;
        } else if (this.role() == Role.CROSSBOWMAN ? stack.getItem() instanceof CrossbowItem
                : this.role().ranged ? stack.getItem() instanceof BowItem : KnightCombatApi.weapon(stack) != null) {
            return EquipmentSlot.MAINHAND;
        } else {
            return null;
        }
    }

    /** What he says when asked: who he is, how he is, what he does. */
    public Component status() {
        return Component.translatable(
                "druzhina.msg.status",
                this.getDisplayName(),
                Mth.ceil(this.getHealth()),
                Mth.ceil(this.getMaxHealth()),
                this.kills,
                Component.translatable("druzhina.orders." + this.orders().id())
            )
            .withStyle(ChatFormatting.GOLD);
    }

    // ------------------------------------------------------------------ bows

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        boolean crossbow = this.getMainHandItem().getItem() instanceof CrossbowItem;
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, power);
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        // a steadier hand with every rank
        float spread = Math.max(2.0f, 10.0f - 8.0f * this.skill());
        float speed = crossbow ? 2.6f : 1.6f;
        arrow.shoot(dx, dy + d * (crossbow ? 0.08 : 0.2), dz, speed, spread * (crossbow ? 0.7f : 1.0f));
        // a bolt goes through mail; laws and good spirit make the shots tell (an attribute others may raise)
        arrow.setBaseDamage(arrow.getBaseDamage() + (crossbow ? 1.5 : 0) + this.getAttributeValue(DruzhinaAttributes.RANGED_DAMAGE.get()));
        if (crossbow) {
            arrow.setShotFromCrossbow(true);
        }
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        this.playSound(crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        this.level().addFreshEntity(arrow);
    }

    // ------------------------------------------------------------------ sounds: men, not monsters

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.9f + this.random.nextFloat() * 0.15f;
    }

    // ------------------------------------------------------------------ spawning and saving

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (this.nameIndex < 0) {
            this.enlist(this.role(), this.getOwnerUUID(), level.getRandom());
        }

        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", this.role().id());
        tag.putInt("Skin", this.skin());
        tag.putString("Orders", this.orders().id());
        tag.putInt("Rank", this.rank());
        tag.putInt("Kills", this.kills);
        tag.putInt("NameIndex", this.nameIndex);
        tag.putInt("Given", this.given);
        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Commander", owner);
        }

        if (this.leader != null) {
            tag.putUUID("Leader", this.leader);
        }
        tag.putBoolean("Managed", this.managed());
        if (!this.patrol.isEmpty()) {
            ListTag round = new ListTag();
            for (Vec3 p : this.patrol) {
                CompoundTag t = new CompoundTag();
                t.putDouble("X", p.x);
                t.putDouble("Y", p.y);
                t.putDouble("Z", p.z);
                round.add(t);
            }
            tag.put("Patrol", round);
            tag.putInt("PatrolIndex", this.patrolIndex);
        }
        if (this.post != null) {
            tag.putDouble("PostX", this.post.x);
            tag.putDouble("PostY", this.post.y);
            tag.putDouble("PostZ", this.post.z);
            tag.putFloat("PostYaw", this.postYaw);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Role role = Role.byName(tag.getString("Role"));
        this.entityData.set(ROLE, (role == null ? Role.SWORDSMAN : role).ordinal());
        this.entityData.set(SKIN, tag.getInt("Skin"));
        for (Orders o : Orders.values()) {
            if (o.id().equals(tag.getString("Orders"))) {
                this.entityData.set(ORDERS, o.ordinal());
            }
        }

        this.entityData.set(RANK, tag.getInt("Rank"));
        this.kills = tag.getInt("Kills");
        this.nameIndex = tag.contains("NameIndex") ? tag.getInt("NameIndex") : -1;
        this.given = tag.getInt("Given");
        this.entityData.set(OWNER, tag.hasUUID("Commander") ? Optional.of(tag.getUUID("Commander")) : Optional.empty());
        this.post = tag.contains("PostX") ? new Vec3(tag.getDouble("PostX"), tag.getDouble("PostY"), tag.getDouble("PostZ")) : null;
        this.postYaw = tag.getFloat("PostYaw");
        this.leader = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        this.entityData.set(MANAGED_DATA, tag.getBoolean("Managed") || this.getPersistentData().getBoolean(MANAGED));
        this.patrol.clear();
        for (Tag t : tag.getList("Patrol", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            this.patrol.add(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")));
        }
        this.patrolIndex = tag.getInt("PatrolIndex");
        this.applyRank();
        if (this.nameIndex >= 0) {
            this.rename();
        }
    }

    // ------------------------------------------------------------------ bandits wander

    /** Bandits without a quarry roam; soldiers stand where they were told. */
    private static final class BanditStrollGoal extends WaterAvoidingRandomStrollGoal {
        private final Soldier soldier;

        BanditStrollGoal(Soldier soldier) {
            super(soldier, 0.8);
            this.soldier = soldier;
        }

        @Override
        public boolean canUse() {
            return this.soldier.isBandit() && super.canUse();
        }
    }
}
