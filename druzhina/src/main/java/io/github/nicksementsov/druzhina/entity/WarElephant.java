package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.druzhina.api.Relations;
import io.github.nicksementsov.druzhina.registry.DruzhinaSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * A war elephant: a driver on its neck and an archer in the howdah on its back. Driven at the enemy it tramples
 * whatever stands in its way and tosses men on its tusks; arrows and blades do little against its hide. Without its
 * driver its master may ride it himself.
 */
public class WarElephant extends PathfinderMob implements OwnableEntity, PlayerRideable {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(WarElephant.class, EntityDataSerializers.OPTIONAL_UUID);
    /** The model is drawn this much larger than its pixels. */
    public static final float SCALE = 1.4f;
    /** Where the driver sits (on the neck) and the archer (in the howdah): blocks forward and up. */
    private static final float DRIVER_FORWARD = 0.95f;
    private static final float DRIVER_UP = 2.45f;
    private static final float HOWDAH_FORWARD = -0.2f;
    private static final float HOWDAH_UP = 2.65f;
    private static final int ATTACK_COOLDOWN = 30;
    /** The toss of the head when it strikes, in ticks. */
    public static final int TOSS = 12;

    private int attackCooldown;
    /** Client: ticks of the toss still to show. */
    private int tossTicks;
    private final Map<Integer, Long> trampled = new HashMap<>();

    public WarElephant(EntityType<? extends WarElephant> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.1f);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 160.0)
            .add(Attributes.MOVEMENT_SPEED, 0.17)
            .add(Attributes.ATTACK_DAMAGE, 13.0)
            .add(Attributes.ATTACK_KNOCKBACK, 1.6)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.ARMOR_TOUGHNESS, 2.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // these move it only when nobody drives it
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            protected double getAttackReachSqr(LivingEntity t) {
                double r = WarElephant.this.getBbWidth() / 2 + t.getBbWidth() / 2 + 1.4;
                return r * r;
            }
        });
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() {
                return WarElephant.this.getRandom().nextInt(4) == 0 && super.canUse();
            }
        });
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    // ------------------------------------------------------------------ whose it is

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        this.entityData.set(OWNER, Optional.ofNullable(owner));
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        UUID owner = this.getOwnerUUID();
        if (other == this || this.hasPassenger(other)) {
            return true;
        }
        if (owner != null) {
            if (owner.equals(other.getUUID())) {
                return true;
            } else if (other instanceof Soldier s) {
                return !s.isBandit() && Relations.between(owner, s.getOwnerUUID()) == Relations.Stance.ALLY;
            } else if (other instanceof OwnableEntity pet && owner.equals(pet.getOwnerUUID())) {
                return true;
            } else if (other instanceof Player p && Relations.between(owner, p.getUUID()) == Relations.Stance.ALLY) {
                return true;
            }
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !this.isAlliedTo(target) && super.canAttack(target);
    }

    /** Whom it tramples and gores: its driver's enemies, or whoever has hurt it. */
    public boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || this.isAlliedTo(e) || e.isPassengerOfSameVehicle(this)) {
            return false;
        }
        LivingEntity driver = this.getControllingPassenger();
        if (driver instanceof Soldier s) {
            return e == s.getTarget() || e == this.getTarget() || s.isFoe(e) || e instanceof Mob m && m.getTarget() == s;
        } else if (driver instanceof Player p) {
            return e instanceof Enemy && !(e instanceof Creeper) || e instanceof Soldier s && s.isBandit() || e == p.getLastHurtMob()
                    || e instanceof Mob m && m.getTarget() == p;
        }
        return e == this.getTarget();
    }

    // ------------------------------------------------------------------ riders

    /** The one on its neck: its driver, or its master, or whoever is left on it. */
    @Nullable
    public Entity driver() {
        Entity first = null;
        for (Entity e : this.getPassengers()) {
            if (e instanceof Soldier s && s.role().mount == Role.Mount.ELEPHANT) {
                return e;
            }
            if (first == null) {
                first = e;
            }
        }
        for (Entity e : this.getPassengers()) {
            if (e instanceof Player) {
                return e;
            }
        }
        return first;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (this.isNoAi()) {
            return null;
        }
        Entity d = this.driver();
        return d instanceof Mob || d instanceof Player ? (LivingEntity) d : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2;
    }

    /** A place for {@code s} on it. */
    public boolean hasSeatFor(Soldier s) {
        return this.getPassengers().size() < 2;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        boolean front = passenger == this.driver();
        float forward = front ? DRIVER_FORWARD : HOWDAH_FORWARD;
        float up = front ? DRIVER_UP : HOWDAH_UP;
        // the back rises and falls with the stride
        up += this.stride(1.0f) * 0.06f;
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        move.accept(passenger, this.getX() - Mth.sin(yaw) * forward, this.getY() + up + passenger.getMyRidingOffset(), this.getZ() + Mth.cos(yaw) * forward);
    }

    /** -1 to 1 with the stride, for the riders' bob and the model's. */
    public float stride(float partialTick) {
        float pos = this.walkAnimation.position(partialTick);
        float speed = Math.min(1, this.walkAnimation.speed(partialTick));
        return Mth.cos(pos * 0.45f * 2) * speed;
    }

    /** Turns it, a little at a time, toward {@code t}. */
    public void turnTo(LivingEntity t) {
        float toward = (float) (Mth.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90;
        float yaw = Mth.approachDegrees(this.getYRot(), toward, 5);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.setYHeadRot(yaw);
    }

    @Override
    public double getPassengersRidingOffset() {
        return DRIVER_UP;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double side = this.getBbWidth() / 2 + passenger.getBbWidth() / 2 + 0.4;
        for (int sign : new int[]{1, -1}) {
            double x = this.getX() + Mth.cos(yaw) * side * sign;
            double z = this.getZ() + Mth.sin(yaw) * side * sign;
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
                Vec3 at = new Vec3(x, pos.getY(), z);
                if (this.level().getBlockState(pos.below()).isFaceSturdy(this.level(), pos.below(), net.minecraft.core.Direction.UP)
                        && this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(at))) {
                    return at;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (this.getOwnerUUID() == null && this.isFood(held)) {
            // a wild one, fed, takes the one who fed it for its master
            if (!this.level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.playSound(SoundEvents.HORSE_EAT, 1.0f, 0.5f);
                if (this.random.nextInt(3) == 0 || held.is(Items.HAY_BLOCK)) {
                    this.setOwnerUUID(player.getUUID());
                    this.setPersistenceRequired();
                    this.setTarget(null);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                    this.playSound(DruzhinaSounds.ELEPHANT_AMBIENT.get(), 1.6f, 1.0f);
                } else {
                    this.level().broadcastEntityEvent(this, (byte) 6);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        boolean mine = player.getUUID().equals(this.getOwnerUUID());
        if (mine && this.isFood(held) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide) {
                this.heal(held.is(Items.HAY_BLOCK) ? 20 : 6);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.playSound(SoundEvents.HORSE_EAT, 1.0f, 0.5f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (mine && held.isEmpty() && !player.isShiftKeyDown() && this.getPassengers().size() < 2) {
            // up on its neck, or into the howdah behind its driver
            if (!this.level().isClientSide) {
                player.setYRot(this.getYRot());
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private boolean isFood(ItemStack stack) {
        return stack.is(Items.HAY_BLOCK) || stack.is(Items.WHEAT) || stack.is(Items.APPLE) || stack.is(Items.SUGAR_CANE) || stack.is(ItemTags.LEAVES);
    }

    // ------------------------------------------------------------------ the master on its neck

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        Vec2 rot = new Vec2(player.getXRot() * 0.5f, player.getYRot());
        // a great beast turns slowly
        float yaw = Mth.approachDegrees(this.getYRot(), rot.y, 6);
        this.setRot(yaw, rot.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        float forward = player.zza;
        if (forward <= 0) {
            forward *= 0.3f;
        }
        return new Vec3(player.xxa * 0.2f, 0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * (player.isSprinting() ? 1.35f : 1.0f);
    }

    // ------------------------------------------------------------------ life

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        LivingEntity driver = this.getControllingPassenger();
        if (driver instanceof Mob && this.attackCooldown == 0) {
            // driven: the beast strikes at its driver's enemy, or at any other enemy in front of it
            LivingEntity t = driver instanceof Soldier s ? s.getTarget() : null;
            if (t == null || !this.inReach(t) || !this.isFoe(t)) {
                t = null;
                for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.0), e -> this.isFoe(e) && this.inReach(e))) {
                    t = e;
                    break;
                }
            }
            if (t != null) {
                this.getLookControl().setLookAt(t, 30, 30);
                this.doHurtTarget(t);
            }
        }
        if (this.getControllingPassenger() != null) {
            this.trample();
        }
        if (this.tickCount % 60 == 0 && this.getHealth() < this.getMaxHealth() && this.getLastHurtByMobTimestamp() + 200 < this.tickCount) {
            this.heal(1.0f);
        }
    }

    /** Close in front of it, within reach of the tusks. */
    private boolean inReach(LivingEntity t) {
        double dx = t.getX() - this.getX();
        double dz = t.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz) - this.getBbWidth() / 2 - t.getBbWidth() / 2;
        if (d > 1.6 || t.getY() > this.getY() + 3 || t.getY() + t.getBbHeight() < this.getY() - 1) {
            return false;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double len = Math.sqrt(dx * dx + dz * dz);
        return len < 1.0E-3 || (dx * fx + dz * fz) / len > 0.2;
    }

    /** Running, it knocks down and treads on the enemies in its way. */
    private void trample() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.horizontalDistanceSqr() < 0.1 * 0.1) {
            return;
        }
        long now = this.level().getGameTime();
        Vec3 fwd = new Vec3(motion.x, 0, motion.z).normalize();
        AABB box = this.getBoundingBox().inflate(0.3, 0, 0.3).move(fwd.scale(0.6));
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.getBbHeight() < 2.6 && this.isFoe(e))) {
            Long last = this.trampled.get(e.getId());
            if (last != null && now - last < 20) {
                continue;
            }
            this.trampled.put(e.getId(), now);
            if (e.hurt(this.damageSources().mobAttack(this), 7.0f)) {
                e.knockback(1.3, -fwd.x, -fwd.z);
                if (e.isPassenger() && this.random.nextInt(3) == 0) {
                    // a horse goes down, its rider with it
                    e.stopRiding();
                }
                this.playSound(SoundEvents.RAVAGER_STEP, 1.2f, 0.6f);
            }
        }
        if (this.trampled.size() > 32) {
            this.trampled.values().removeIf(t -> now - t > 40);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        this.attackCooldown = ATTACK_COOLDOWN;
        this.level().broadcastEntityEvent(this, (byte) 4);
        this.playSound(DruzhinaSounds.ELEPHANT_ATTACK.get(), 1.6f, 0.9f + this.random.nextFloat() * 0.2f);
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            // tossed on the tusks
            le.setDeltaMovement(le.getDeltaMovement().add(0, 0.5, 0));
            le.hurtMarked = true;
            if (le.isPassenger() && this.random.nextInt(2) == 0) {
                le.stopRiding();
            }
        }
        return hit;
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 4) {
            this.tossTicks = TOSS;
        } else if (event == 6 || event == 7) {
            // tamed: hearts; not yet: smoke
            for (int i = 0; i < 7; i++) {
                this.level().addParticle(event == 7 ? ParticleTypes.HEART : ParticleTypes.SMOKE, this.getRandomX(1.0), this.getRandomY() + 0.5,
                        this.getRandomZ(1.0), 0, 0.02, 0);
            }
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tossTicks > 0) {
            this.tossTicks--;
        }
    }

    /** 0 to 1: how far through the toss of the head it is (client). */
    public float toss(float partialTick) {
        return this.tossTicks <= 0 ? 0 : (TOSS - this.tossTicks + partialTick) / TOSS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        // its own men's arrows and blades do not hurt it
        if (by != null && by != this && this.isAlliedTo(by)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Fallen, it is drawn from as far as when it stood (its box is low then). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double size = 2.4 * 64 * getViewScale();
        return this.isDeadOrDying() ? distance < size * size : super.shouldRenderAtSqrDistance(distance);
    }

    /** Fallen, it lies on its side: drawn while any of it is in sight (its box is small then). */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return this.isDeadOrDying() ? new net.minecraft.world.phys.AABB(this.blockPosition()).inflate(4, 2, 4) : super.getBoundingBoxForCulling();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions size) {
        return size.height * 0.85f;
    }

    // ------------------------------------------------------------------ sounds

    @Override
    protected SoundEvent getAmbientSound() {
        return DruzhinaSounds.ELEPHANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return DruzhinaSounds.ELEPHANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return DruzhinaSounds.ELEPHANT_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.RAVAGER_STEP, 0.6f, 0.55f + this.random.nextFloat() * 0.1f);
        if (this.level() instanceof ServerLevel server && this.random.nextInt(3) == 0 && !state.isAir()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), this.getX(), this.getY() + 0.1, this.getZ(), 6,
                    this.getBbWidth() / 3, 0.05, this.getBbWidth() / 3, 0.1);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
    }
}
