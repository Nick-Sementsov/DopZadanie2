package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A siege tower: rolled up to a wall (the commander's order to batter it), it lets its bridge down onto the wall, and
 * the men about its foot climb up inside it and cross over, one after another, to fight on the wall. Hides over its
 * front keep off arrows and fire.
 */
public class SiegeTower extends SiegeEngine {
    public static final float SCALE = 1.3f;
    private static final EntityDataAccessor<Boolean> BRIDGE = SynchedEntityData.defineId(SiegeTower.class, EntityDataSerializers.BOOLEAN);
    /** Ticks between two men crossing. */
    private static final int CLIMB = 16;
    /** How far about its foot the men are who climb it. */
    private static final double GATHER = 12;

    private float bridgeO;
    private float bridgeNow;

    public SiegeTower(EntityType<? extends SiegeTower> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(220, 12, 0.16);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BRIDGE, false);
    }

    public boolean bridgeDown() {
        return this.entityData.get(BRIDGE);
    }

    private void setBridge(boolean down) {
        if (down != this.bridgeDown()) {
            this.entityData.set(BRIDGE, down);
            this.level().playSound(null, this.getX(), this.getY() + 6, this.getZ(), down ? SoundEvents.CHAIN_BREAK : SoundEvents.CHAIN_PLACE,
                    SoundSource.NEUTRAL, 1.4f, 0.6f);
        }
    }

    @Override
    public int reloadTicks() {
        return CLIMB;
    }

    @Override
    public double minRange() {
        return 0;
    }

    @Override
    public double maxRange() {
        return this.getBbWidth() / 2 + 2.6;
    }

    @Override
    protected boolean needsSight() {
        return false;
    }

    @Override
    public boolean closeIn() {
        return true;
    }

    @Override
    protected float turnSpeed() {
        return 1.5f;
    }

    @Override
    protected double crewBack() {
        return 2.4;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 0.3f;
        }
        return super.hurt(source, amount);
    }

    /** It works only on the wall it is sent to: it does not go after men. */
    @Nullable
    @Override
    protected Vec3 aim() {
        BlockPos point = this.siegePoint();
        if (point == null) {
            return null;
        }
        Vec3 at = Vec3.atCenterOf(point);
        Soldier crew = this.crew();
        if (crew == null || this.horizontal(at) > this.maxRange() + 6) {
            return null;
        }
        // the wall straight before the place it was set at (it may stand beside the gate the rams batter)
        float yaw = crew.postYaw() * Mth.DEG_TO_RAD;
        double reach = this.getBbWidth() / 2 + 1.5;
        return new Vec3(this.getX() - Mth.sin(yaw) * reach, at.y, this.getZ() + Mth.cos(yaw) * reach);
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        if (!this.bridgeDown()) {
            this.setBridge(true);
            return true;
        }
        Vec3 landing = this.landing();
        if (landing == null) {
            return false;
        }
        Soldier climber = null;
        double best = GATHER * GATHER;
        for (Soldier s : this.level().getEntitiesOfClass(Soldier.class, this.getBoundingBox().inflate(GATHER),
                s -> s.isAlive() && s != crew && !s.isPassenger() && !s.routed() && java.util.Objects.equals(s.getOwnerUUID(), this.getOwnerUUID())
                        && s.getY() < landing.y - 1.5)) {
            double d = s.distanceToSqr(this);
            if (d < best) {
                best = d;
                climber = s;
            }
        }
        if (climber == null) {
            return false;
        }
        // up the ladder inside, over the bridge, and onto the wall
        climber.teleportTo(landing.x + (this.random.nextDouble() - 0.5) * 0.6, landing.y, landing.z + (this.random.nextDouble() - 0.5) * 0.6);
        climber.order(Orders.CHARGE, null, 0);
        this.level().playSound(null, landing.x, landing.y, landing.z, SoundEvents.LADDER_STEP, SoundSource.NEUTRAL, 1.0f, 0.8f);
        this.level().playSound(null, landing.x, landing.y, landing.z, SoundEvents.WOOD_STEP, SoundSource.NEUTRAL, 1.0f, 0.7f);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, landing.x, landing.y + 0.2, landing.z, 4, 0.3, 0.1, 0.3, 0.01);
        }
        return true;
    }

    /** Where a man crossing the bridge sets foot on the wall: the top of the wall before it, else null. */
    @Nullable
    private Vec3 landing() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        double half = this.getBbWidth() / 2;
        for (double reach = half + 0.4; reach <= half + 3.6; reach += 0.5) {
            for (int side : new int[]{0, 1, -1}) {
                double x = this.getX() + fx * reach - fz * side;
                double z = this.getZ() + fz * reach + fx * side;
                for (int up = 9; up >= 2; up--) {
                    BlockPos pos = BlockPos.containing(x, this.getY() + up, z);
                    if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()
                            && this.level().getBlockState(pos.above()).getCollisionShape(this.level(), pos.above()).isEmpty()
                            && this.level().getBlockState(pos.above(2)).getCollisionShape(this.level(), pos.above(2)).isEmpty()) {
                        return new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.bridgeDown() && (this.siegePoint() == null || this.getDeltaMovement().horizontalDistanceSqr() > 0.004)) {
            // off the wall: the bridge goes up again
            this.setBridge(false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.bridgeO = this.bridgeNow;
        this.bridgeNow = Mth.approach(this.bridgeNow, this.bridgeDown() ? 1 : 0, 0.05f);
    }

    /** For the model: 0 the bridge stands up, 1 it lies on the wall. */
    public float bridge(float partialTick) {
        return Mth.lerp(partialTick, this.bridgeO, this.bridgeNow);
    }
}
