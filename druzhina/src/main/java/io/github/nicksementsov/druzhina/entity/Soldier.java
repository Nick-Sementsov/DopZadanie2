package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.druzhina.compat.Helm;
import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.ai.BoardGoal;
import io.github.nicksementsov.druzhina.entity.ai.CommanderTargetGoal;
import io.github.nicksementsov.druzhina.entity.ai.DuelGoal;
import io.github.nicksementsov.druzhina.entity.ai.ElephantDriveGoal;
import io.github.nicksementsov.druzhina.entity.ai.LanceChargeGoal;
import io.github.nicksementsov.druzhina.entity.ai.RemountGoal;
import io.github.nicksementsov.druzhina.menu.SoldierMenu;
import io.github.nicksementsov.druzhina.entity.ai.FollowCommanderGoal;
import io.github.nicksementsov.druzhina.entity.ai.HoldPostGoal;
import io.github.nicksementsov.druzhina.entity.ai.PatrolGoal;
import io.github.nicksementsov.druzhina.api.Relations;
import io.github.nicksementsov.druzhina.registry.DruzhinaAttributes;
import io.github.nicksementsov.druzhina.entity.ai.VolleyGoal;
import io.github.nicksementsov.knightcombat.api.CombatFighter;
import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
import io.github.nicksementsov.knightcombat.combat.Lances;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SlotAccess;
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
import net.minecraft.world.entity.animal.horse.AbstractHorse;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

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
    private static final EntityDataAccessor<Integer> KILLS = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> KEEP_RANKS = SynchedEntityData.defineId(Soldier.class, EntityDataSerializers.BOOLEAN);
    public static final int SKINS = 8;
    public static final int BANDIT_SKINS = 4;
    public static final int NAMES = 40;
    /** Kills needed for each rank above recruit. */
    private static final int[] RANK_KILLS = {3, 10, 25};
    private static final int HEAL_DELAY = 200;
    private static final String MANAGED = "druzhina.managed";
    private static final String NAMED = "druzhina.named";
    /**
     * What he carries, as the equipment window shows it: head, body, legs, feet, the main and the other hand, the
     * spare weapon (a rider's sword, while he charges with the lance) and the armour of his horse.
     */
    public static final int GEAR = 8;
    public static final int MAIN = 4;
    public static final int OFF = 5;
    public static final int SPARE = 6;
    public static final int BARDING = 7;
    private static final EquipmentSlot[] GEAR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

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
    /** His horse or his elephant, or null for a man on foot. */
    @Nullable
    private UUID mount;
    /** A second weapon he carries: the sword of a lancer, for when the lance is no use. */
    private ItemStack spare = ItemStack.EMPTY;
    /** His own things, put aside while he uses what the commander gave him instead; he takes them back after. */
    private final ItemStack[] stowed = new ItemStack[GEAR];
    /** The ship he is on his way aboard (BoardGoal), or null. */
    @Nullable
    private Entity boardingTo;
    /** How many checks in a row his commander has been ashore while he is still aboard. */
    private int ashoreChecks;
    /** At the helm of a ship: how he sails her (made when he first takes the helm). */
    @Nullable
    private Helm helm;
    /** A ship he was sent aboard (with the baton or from the commander's view), and until when the order holds. */
    @Nullable
    private UUID boardShip;
    private long boardUntil;
    /** The march he is on (from the commander's view), or null. */
    @Nullable
    private March march;
    /** The one he was told to fall upon from the commander's view: he goes after him wherever he goes. */
    @Nullable
    private UUID hunt;

    public Soldier(EntityType<? extends Soldier> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
        java.util.Arrays.fill(this.stowed, ItemStack.EMPTY);
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
        this.entityData.define(KILLS, 0);
        this.entityData.define(KEEP_RANKS, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new RemountGoal(this));
        this.goalSelector.addGoal(1, new LanceChargeGoal(this));
        this.goalSelector.addGoal(1, new ElephantDriveGoal(this));
        this.goalSelector.addGoal(1, new DuelGoal(this));
        this.goalSelector.addGoal(2, new BoardGoal(this));
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
    public boolean isFoe(LivingEntity e) {
        if (!this.withinReach(e) || this.routed()) {
            return false;
        } else if (e instanceof Soldier other) {
            return this.isBandit() != other.isBandit()
                    || !this.isBandit() && Relations.between(this.getOwnerUUID(), other.getOwnerUUID()) == Relations.Stance.ENEMY;
        } else if (this.isBandit()) {
            return false;
        } else if (!(e instanceof Player) && Relations.hostile(this.getOwnerUUID(), e)) {
            // one who fights for a side at war with his commander's (a guard of an enemy realm's colony)
            return true;
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
        if (this.hunt != null && this.hunt.equals(e.getUUID())) {
            return true;
        }
        // archers shoot far, riders ride far
        double leash = this.shooter() ? 26 : this.isPassenger() ? 24 : 16;
        return switch (this.orders()) {
            case CHARGE -> true;
            case PATROL -> {
                double best = Double.MAX_VALUE;
                for (Vec3 p : this.patrol) {
                    best = Math.min(best, e.distanceToSqr(p));
                }
                yield best < (leash + 4) * (leash + 4);
            }
            case HOLD -> {
                if (this.march != null && this.march.moving()) {
                    // on the march: an attack on the move falls upon what it meets; a plain march only fights back
                    // (against whoever strikes him or goes for one of his comrades close by)
                    yield this.march.aggressive() ? e.distanceToSqr(this) < leash * leash
                            : e.distanceToSqr(this) < 8 * 8 && (e == this.getLastHurtByMob()
                                || e instanceof Mob m && m.getTarget() != null && (m.getTarget() == this || m.getTarget().isAlliedTo(this)));
                }
                if (this.post == null) {
                    yield true;
                }
                // keeping the ranks, a man with a blade fights only at his place
                double reach = this.keepRanks() && !this.shooter() ? 3.5 : leash;
                yield e.distanceToSqr(this.post) < reach * reach;
            }
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

    /** A bow or a crossbow in his hand: he fights from afar. */
    public boolean shooter() {
        Item item = this.getMainHandItem().getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    /** A weapon of the melee rules in his hand. */
    public boolean hasMeleeWeapon() {
        return KnightCombatApi.weapon(this.getMainHandItem()) != null;
    }

    /** A lance in his hand on a horse: he charges with it couched. */
    public boolean lancer() {
        return this.getVehicle() instanceof AbstractHorse && Lances.couchable(this.getMainHandItem());
    }

    /** He rides on a beast that someone else drives (the archer in an elephant's howdah): he does not walk. */
    public boolean isCrew() {
        Entity v = this.getVehicle();
        return v != null && !(v instanceof Mob m && m.getControllingPassenger() == this);
    }

    /** Sends him aboard {@code ship}: he goes to it and takes a seat, the first at her helm. */
    public void sendAboard(Entity ship) {
        this.boardShip = ship.getUUID();
        this.boardUntil = this.level().getGameTime() + 20 * 60;
    }

    /** The ship he was sent aboard, while the order holds. */
    @Nullable
    public Entity shipOrdered() {
        if (this.boardShip == null) {
            return null;
        }
        if (this.level().getGameTime() > this.boardUntil || !(this.level() instanceof ServerLevel server)) {
            this.boardShip = null;
            return null;
        }
        Entity e = server.getEntity(this.boardShip);
        if (e == null || !e.isAlive()) {
            this.boardShip = null;
        }
        return e;
    }

    public void shipBoarded() {
        this.boardShip = null;
    }

    /** At the helm, she can get no nearer to where he takes her. */
    public boolean helmStalled() {
        return this.helm != null && this.helm.stalled();
    }

    /** He may climb into a vessel at once, though he has just got off his horse. */
    public void readyToBoard() {
        this.boardingCooldown = 0;
    }

    /** Whether he is on his way aboard his commander's ship (then he does not go back to his horse). */
    public boolean boarding() {
        return this.boardingTo != null;
    }

    /** The vessel he is on his way to, or null. */
    @Nullable
    public Entity boardingTo() {
        return this.boardingTo;
    }

    public void setBoardingTo(@Nullable Entity vessel) {
        this.boardingTo = vessel;
    }

    /**
     * Somewhere free a few blocks from the commander, where he is moved at once (a man who fell far behind, or one who
     * comes ashore from a ship). Returns whether a place was found.
     */
    public boolean teleportNear(Player boss) {
        Mob steed = this.steed();
        BlockPos at = boss.blockPosition();
        Entity body = steed != null ? steed : this;
        int spread = steed != null ? 5 : 3;
        for (int i = 0; i < 12; i++) {
            int x = at.getX() + this.random.nextInt(2 * spread + 1) - spread;
            int z = at.getZ() + this.random.nextInt(2 * spread + 1) - spread;
            int y = at.getY() + this.random.nextInt(3) - 1;
            if (Math.abs(x - at.getX()) < 2 && Math.abs(z - at.getZ()) < 2) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (WalkNodeEvaluator.getBlockPathTypeStatic(this.level(), pos.mutable()) == BlockPathTypes.WALKABLE
                    && !(this.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
                    && this.level().noCollision(body, body.getBoundingBox().move(pos.getCenter().subtract(body.position()).add(0, -0.5, 0)))) {
                if (steed != null) {
                    this.relocate(x + 0.5, y, z + 0.5);
                } else {
                    this.moveTo(x + 0.5, y, z + 0.5, this.getYRot(), this.getXRot());
                }
                this.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    /** The one he drives: his horse or elephant when he sits on it in front, or null. */
    @Nullable
    public Mob steed() {
        return this.getVehicle() instanceof Mob v && v.getControllingPassenger() == this ? v : null;
    }

    @Nullable
    public UUID mountId() {
        return this.mount;
    }

    public void setMount(@Nullable UUID mount) {
        this.mount = mount;
    }

    /** His own horse or elephant, if it is alive in this world. */
    @Nullable
    public Mob mountEntity() {
        if (this.mount != null && this.level() instanceof ServerLevel server && server.getEntity(this.mount) instanceof Mob m && m.isAlive()) {
            return m;
        }
        return null;
    }

    /** Moves him at once, with the beast he drives. */
    public void relocate(double x, double y, double z) {
        Mob steed = this.steed();
        if (steed != null) {
            steed.teleportTo(x, y, z);
            steed.getNavigation().stop();
        } else if (!this.isPassenger()) {
            this.teleportTo(x, y, z);
        }
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
        return this.level().isClientSide ? this.entityData.get(KILLS) : this.kills;
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
        this.march = null;
        this.hunt = null;
        if (orders != Orders.PATROL) {
            this.patrol.clear();
        }
        if (orders != Orders.CHARGE) {
            this.setTarget(null);
        }
    }

    /** His place at the end of the present stage of his march (he stays on the march). */
    public void setPost(Vec3 post, float yaw) {
        this.post = post;
        this.postYaw = yaw;
    }

    @Nullable
    public March march() {
        return this.march;
    }

    public void joinMarch(March march) {
        this.march = march;
    }

    public void leaveMarch(March march) {
        if (this.march == march) {
            this.march = null;
        }
    }

    /** Fall upon {@code foe} and stay after him till he is down (then stand where the fight ended). */
    public void hunt(LivingEntity foe) {
        this.hunt = foe.getUUID();
        this.setTarget(foe);
    }

    /** Whether he keeps his place in the ranks (fights only there) rather than going out to fight. */
    public boolean keepRanks() {
        return this.entityData.get(KEEP_RANKS);
    }

    public void setKeepRanks(boolean keep) {
        this.entityData.set(KEEP_RANKS, keep);
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
        this.entityData.set(KILLS, this.kills);
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

        String pool = this.role().names();
        MutableComponent name = Component.translatable("druzhina.name." + (pool.isEmpty() ? "" : pool + ".") + this.nameIndex);
        name.append(" · ").append(Component.translatable("druzhina.role." + this.role().id()));
        if (this.rank() > 0) {
            name.append(" · ").append(Component.translatable("druzhina.rank." + this.rank()));
        }

        this.setCustomName(name.withStyle(this.isBandit() ? ChatFormatting.DARK_RED : ChatFormatting.WHITE));
        this.setCustomNameVisible(false);
    }

    // ------------------------------------------------------------------ friends and foes

    private static boolean alliedSide(UUID mine, @Nullable UUID side) {
        return side != null && Relations.between(mine, side) == Relations.Stance.ALLY;
    }

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
            } else if (mine != null && !(other instanceof Player) && alliedSide(mine, Relations.sideOf(other))) {
                // one who fights for his side or an allied one (a guard of an allied colony)
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
        this.entityData.set(KILLS, this.kills);
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
        if (!this.level().isClientSide && this.isAlive() && this.tickCount % 10 == 3) {
            this.tickArms();
            this.tickVoyage();
            this.tickHunt();
        }
        if (!this.level().isClientSide && this.isAlive() && this.tickCount % Helm.PERIOD == 0) {
            Entity vessel = Vessels.vesselOf(this);
            if (vessel != null && Vessels.isHelmsman(vessel, this) && this.getOwnerUUID() != null) {
                if (this.helm == null) {
                    this.helm = new Helm(this);
                }
                this.helm.tick(vessel);
            } else {
                this.helm = null;
            }
        }
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
            int gear = this.gearFor(held);
            if (held.isEmpty() && player.isShiftKeyDown() && !this.managed()) {
                // Shift and an empty hand, twice: he is let go
                long now = this.level().getGameTime();
                if (now - this.dismissAsked < 60) {
                    this.dismiss(player);
                } else {
                    this.dismissAsked = now;
                    player.displayClientMessage(Component.translatable("druzhina.msg.dismiss_confirm", this.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
                }
            } else if (held.isEmpty() && player instanceof ServerPlayer sp) {
                // an empty hand: his things, to look at and to change
                NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, p) -> new SoldierMenu(id, inv, this), this.getDisplayName()),
                        buf -> buf.writeVarInt(this.getId()));
            } else if (gear >= 0) {
                // a weapon, a shield or a piece of armour from the commander's hand
                ItemStack old = this.hand(gear, held.copyWithCount(1));
                held.shrink(1);
                if (!old.isEmpty() && !player.getInventory().add(old)) {
                    player.drop(old, false);
                }

                this.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8f, 1.0f);
                player.displayClientMessage(Component.translatable("druzhina.msg.equipped", this.plainName(), this.gear(gear).getHoverName()), true);
            } else {
                player.displayClientMessage(this.status(), false);
            }

            return InteractionResult.CONSUME;
        }
    }

    /** Lets him go home: what the commander gave him is left behind, and his horse goes with him. */
    private void dismiss(Player player) {
        this.dropGiven();
        player.displayClientMessage(Component.translatable("druzhina.msg.dismissed", this.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, this.getX(), this.getY() + 1, this.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.8f);
        Mob beast = this.mountEntity();
        if (beast != null && !(beast.getControllingPassenger() instanceof Player) && beast.getPassengers().stream().noneMatch(e -> e instanceof Player)) {
            beast.ejectPassengers();
            beast.discard();
        }
        this.discard();
    }

    /** Puts what the commander handed over on the ground. */
    private void dropGiven() {
        for (int i = 0; i < GEAR; i++) {
            if (this.given(i)) {
                ItemStack stack = this.gear(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(stack.copy());
                    this.setGear(i, ItemStack.EMPTY);
                }
            }
        }
        this.given = 0;
    }

    // ------------------------------------------------------------------ his things

    /** What he carries at {@code i} (see {@link #GEAR}). */
    public ItemStack gear(int i) {
        if (i < GEAR_SLOTS.length) {
            return this.getItemBySlot(GEAR_SLOTS[i]);
        } else if (i == SPARE) {
            return this.spare;
        } else {
            SlotAccess barding = this.barding();
            return barding == null ? ItemStack.EMPTY : barding.get();
        }
    }

    public void setGear(int i, ItemStack stack) {
        if (i < GEAR_SLOTS.length) {
            this.setItemSlot(GEAR_SLOTS[i], stack);
        } else if (i == SPARE) {
            this.spare = stack;
        } else {
            SlotAccess barding = this.barding();
            if (barding != null) {
                barding.set(stack);
            }
        }
    }

    /** The armour slot of the horse he rides, or null when he rides none that wears armour. */
    @Nullable
    private SlotAccess barding() {
        return this.horse() != null ? this.horse().getSlot(401) : null;
    }

    /** The horse he rides, if it can wear armour. */
    @Nullable
    public AbstractHorse horse() {
        return this.getVehicle() instanceof AbstractHorse horse && horse.canWearArmor() ? horse : null;
    }

    /** Whether the thing at {@code i} was handed over by the commander (and so may be taken back). */
    public boolean given(int i) {
        return (this.given & 1 << i) != 0;
    }

    public int givenMask() {
        return this.given;
    }

    /** What may be put at {@code i}. */
    public boolean fits(int i, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        return switch (i) {
            case MAIN, OFF -> true;
            case SPARE -> KnightCombatApi.weapon(stack) != null || Lances.isLance(stack) || stack.getItem() instanceof BowItem
                    || stack.getItem() instanceof CrossbowItem;
            case BARDING -> this.horse() != null && this.horse().isArmor(stack);
            default -> getEquipmentSlotForItem(stack) == GEAR_SLOTS[i];
        };
    }

    /**
     * Hands {@code stack} to him at {@code i}: what he had there of his own he puts aside, what the commander had
     * given him before comes back, and is returned.
     */
    public ItemStack hand(int i, ItemStack stack) {
        ItemStack old = this.gear(i);
        ItemStack back = ItemStack.EMPTY;
        if (!old.isEmpty()) {
            if (this.given(i)) {
                back = old;
            } else {
                this.stowed[i] = old;
            }
        }
        this.setGear(i, stack);
        this.given |= 1 << i;
        return back;
    }

    /** Takes back what the commander gave him at {@code i}; he takes up his own thing again. Returns what was taken. */
    public ItemStack takeBack(int i) {
        if (!this.given(i)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = this.gear(i);
        this.setGear(i, this.stowed[i]);
        this.stowed[i] = ItemStack.EMPTY;
        this.given &= ~(1 << i);
        return out;
    }

    /** Where an item handed over goes (see {@link #GEAR}), or -1 when it is nothing a soldier carries. */
    private int gearFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        } else if (stack.getItem() instanceof ArmorItem armor) {
            return switch (armor.getEquipmentSlot()) {
                case HEAD -> 0;
                case CHEST -> 1;
                case LEGS -> 2;
                default -> 3;
            };
        } else if (this.horse() != null && this.horse().isArmor(stack)) {
            return BARDING;
        } else if (stack.getItem() instanceof ShieldItem) {
            return OFF;
        } else if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || Lances.isLance(stack)
                || KnightCombatApi.weapon(stack) != null) {
            return MAIN;
        } else {
            return -1;
        }
    }

    /** Swaps the weapon in hand and the spare one. */
    private void switchArms() {
        ItemStack hand = this.getMainHandItem();
        this.setItemSlot(EquipmentSlot.MAINHAND, this.spare);
        this.spare = hand;
        boolean a = this.given(MAIN);
        boolean b = this.given(SPARE);
        this.given = this.given & ~(1 << MAIN | 1 << SPARE) | (b ? 1 << MAIN : 0) | (a ? 1 << SPARE : 0);
        ItemStack st = this.stowed[MAIN];
        this.stowed[MAIN] = this.stowed[SPARE];
        this.stowed[SPARE] = st;
        this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.1f);
    }

    /** A lancer takes the lance up on horseback and his sword on foot when his horse is lost. */
    private void tickArms() {
        if (this.spare.isEmpty() || this.isUsingItem() || KnightCombatApi.state(this).phase() != io.github.nicksementsov.knightcombat.combat.Phase.IDLE) {
            return;
        }
        ItemStack hand = this.getMainHandItem();
        boolean riding = this.getVehicle() instanceof AbstractHorse;
        if (riding && !Lances.couchable(hand) && Lances.isLance(this.spare)) {
            this.switchArms();
        } else if (!riding && Lances.isLance(hand) && KnightCombatApi.weapon(hand) == null && KnightCombatApi.weapon(this.spare) != null
                && (this.mountEntity() == null || this.mountEntity().distanceToSqr(this) > 32 * 32 || this.isPassenger()
                    || this.getTarget() != null && this.getTarget().distanceToSqr(this) < 6 * 6)) {
            this.switchArms();
        }
    }

    /** After the one he was told to fall upon; when he is down or gone, the man stands where the fight ended. */
    private void tickHunt() {
        if (this.hunt == null || !(this.level() instanceof ServerLevel server)) {
            return;
        }
        Entity e = server.getEntity(this.hunt);
        if (!(e instanceof LivingEntity foe) || !foe.isAlive() || foe.distanceToSqr(this) > 96 * 96) {
            this.hunt = null;
            if (this.orders() == Orders.HOLD && !this.isCrew()) {
                this.post = this.position();
                this.postYaw = this.getYRot();
            }
        } else if (this.getTarget() == null || !this.getTarget().isAlive()) {
            this.setTarget(foe);
        }
    }

    /**
     * At sea: he stays aboard while his commander is aboard (or while he was told to hold), goes ashore after him
     * when he lands, mans a cannon if he sits at one, and mends the ship. On land: Small Ships does not lead him off a deck he was
     * told to stand on.
     */
    private void tickVoyage() {
        if (this.isBandit() || this.getOwnerUUID() == null) {
            return;
        }
        this.addTag(Vessels.STAY_ON_DECK);
        Entity vessel = Vessels.vesselOf(this);
        if (vessel == null) {
            this.ashoreChecks = 0;
            return;
        }
        Player boss = this.commander();
        boolean following = this.orders() == Orders.FOLLOW || this.orders() == Orders.CHARGE;
        Soldier captain = Vessels.captainOf(vessel);
        // a ship one of ours steers: in a charge she fights (all stay aboard); following, she brings them to him
        boolean brought = captain != null && captain.isAlliedTo(this) && (this.orders() == Orders.CHARGE
                || vessel.distanceToSqr(boss == null ? this : boss) > 24 * 24 && !captain.helmStalled());
        if (following && boss != null && boss.level() == this.level() && Vessels.vesselOf(boss) == null && boss.distanceToSqr(this) < 64 * 64
                && !brought) {
            // the commander has gone ashore: after him (a moment later, not when he only changes his seat)
            if (++this.ashoreChecks >= 2) {
                this.ashoreChecks = 0;
                this.stopRiding();
                if (!boss.isInWater() && boss.distanceToSqr(this) < 32 * 32) {
                    this.teleportNear(boss);
                }
                return;
            }
        } else {
            this.ashoreChecks = 0;
        }
        Vessels.tickGunner(this, vessel);
        Vessels.tickCarpenter(this, vessel);
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

    /** He sits in the saddle as a player does. */
    @Override
    public double getMyRidingOffset() {
        return -0.35;
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
        tag.putInt("GivenGear", this.given);
        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Commander", owner);
        }

        if (this.leader != null) {
            tag.putUUID("Leader", this.leader);
        }
        tag.putBoolean("Managed", this.managed());
        tag.putBoolean("KeepRanks", this.keepRanks());
        if (this.mount != null) {
            tag.putUUID("Mount", this.mount);
        }
        if (!this.spare.isEmpty()) {
            tag.put("Spare", this.spare.save(new CompoundTag()));
        }
        ListTag stowedTag = new ListTag();
        for (int i = 0; i < GEAR; i++) {
            if (!this.stowed[i].isEmpty()) {
                CompoundTag t = this.stowed[i].save(new CompoundTag());
                t.putByte("Gear", (byte) i);
                stowedTag.add(t);
            }
        }
        if (!stowedTag.isEmpty()) {
            tag.put("Stowed", stowedTag);
        }
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
        if (tag.contains("GivenGear")) {
            this.given = tag.getInt("GivenGear");
        } else {
            // saved before 2.0: by equipment slot
            int old = tag.getInt("Given");
            this.given = 0;
            for (int i = 0; i < GEAR_SLOTS.length; i++) {
                if ((old & 1 << GEAR_SLOTS[i].ordinal()) != 0) {
                    this.given |= 1 << i;
                }
            }
        }
        this.entityData.set(OWNER, tag.hasUUID("Commander") ? Optional.of(tag.getUUID("Commander")) : Optional.empty());
        this.post = tag.contains("PostX") ? new Vec3(tag.getDouble("PostX"), tag.getDouble("PostY"), tag.getDouble("PostZ")) : null;
        this.postYaw = tag.getFloat("PostYaw");
        this.leader = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        this.entityData.set(MANAGED_DATA, tag.getBoolean("Managed") || this.getPersistentData().getBoolean(MANAGED));
        this.entityData.set(KEEP_RANKS, tag.getBoolean("KeepRanks"));
        this.patrol.clear();
        for (Tag t : tag.getList("Patrol", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            this.patrol.add(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")));
        }
        this.patrolIndex = tag.getInt("PatrolIndex");
        this.mount = tag.hasUUID("Mount") ? tag.getUUID("Mount") : null;
        this.spare = tag.contains("Spare") ? ItemStack.of(tag.getCompound("Spare")) : ItemStack.EMPTY;
        java.util.Arrays.fill(this.stowed, ItemStack.EMPTY);
        for (Tag t : tag.getList("Stowed", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int i = c.getByte("Gear");
            if (i >= 0 && i < GEAR) {
                this.stowed[i] = ItemStack.of(c);
            }
        }
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
