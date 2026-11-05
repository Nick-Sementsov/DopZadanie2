package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A bombard: a great gun of iron bars bound with hoops, on a wheeled carriage. It fires a ball nearly flat at what it
 * sees, six to fifty-six blocks off, with a roar, a flash and a cloud of smoke; the ball breaks walls as nothing else
 * does. It is slow to load (twenty seconds).
 */
public class Bombard extends SiegeEngine {
    public static final float SCALE = 1.3f;
    public static final int RELOAD = 400;
    private static final float SPEED = 2.6f;
    private static final double MUZZLE_UP = 1.7;
    private static final double MUZZLE_FORWARD = 2.0;
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(Bombard.class, EntityDataSerializers.FLOAT);

    public Bombard(EntityType<? extends Bombard> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(100, 10, 0.16);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PITCH, 0.0f);
    }

    public float aimPitch() {
        return this.entityData.get(PITCH);
    }

    @Override
    public int reloadTicks() {
        return RELOAD;
    }

    @Override
    public double minRange() {
        return 6;
    }

    @Override
    public double maxRange() {
        return 56;
    }

    @Override
    protected boolean needsSight() {
        return true;
    }

    @Override
    protected float turnSpeed() {
        return 1.5f;
    }

    @Override
    protected double crewBack() {
        return 1.6;
    }

    @Override
    protected double crewSide() {
        return 1.0;
    }

    private Vec3 muzzle(Vec3 at) {
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(this.getX() + dx / d * MUZZLE_FORWARD, this.getY() + MUZZLE_UP, this.getZ() + dz / d * MUZZLE_FORWARD);
    }

    private double angleTo(Vec3 at) {
        Vec3 from = this.muzzle(at);
        double dx = at.x - from.x;
        double dz = at.z - from.z;
        return launchAngle(Math.sqrt(dx * dx + dz * dz), at.y - from.y, SPEED, Hurled.GRAVITY, false);
    }

    @Override
    protected void aimAt(Vec3 at) {
        double angle = this.angleTo(at);
        if (!Double.isNaN(angle)) {
            float now = this.aimPitch();
            float next = Mth.approach(now, (float) Mth.clamp(angle, -15, 40), 1.5f);
            if (Math.abs(next - now) > 0.2f) {
                this.entityData.set(PITCH, next);
            }
        }
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        double angle = this.angleTo(at);
        if (Double.isNaN(angle) || Math.abs(angle - this.aimPitch()) > 2) {
            return false;
        }
        Vec3 from = this.muzzle(at);
        double yaw = Mth.atan2(at.z - from.z, at.x - from.x) + (this.random.nextDouble() - 0.5) * 0.02;
        double a = (angle + (this.random.nextDouble() - 0.5) * 0.8) * Mth.DEG_TO_RAD;
        CannonBall ball = new CannonBall(this.level(), crew);
        ball.setPos(from.x, from.y, from.z);
        ball.setDeltaMovement(Math.cos(yaw) * Math.cos(a) * SPEED, Math.sin(a) * SPEED, Math.sin(yaw) * Math.cos(a) * SPEED);
        this.level().addFreshEntity(ball);
        if (this.level() instanceof ServerLevel level) {
            Vec3 dir = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 14, 0.2, 0.2, 0.2, 0.08);
            level.sendParticles(ParticleTypes.EXPLOSION, from.x + dir.x * 0.6, from.y, from.z + dir.z * 0.6, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x + dir.x, from.y, from.z + dir.z, 40, 0.8, 0.5, 0.8, 0.04);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, from.x + dir.x, from.y + 0.3, from.z + dir.z, 8, 0.6, 0.3, 0.6, 0.01);
        }
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6.0f, 0.55f);
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2.0f, 1.6f);
        return true;
    }

    // ------------------------------------------------------------------ for the model

    /** How far the barrel has kicked back: 1 just after the shot, back to 0 as they run it up again. */
    public float recoil(float partialTick) {
        if (this.loaded()) {
            return 0;
        }
        float t = this.sinceShot(partialTick);
        if (t < 2) {
            return t / 2;
        }
        return Mth.clamp(1 - (t - 2) / 30, 0, 1);
    }
}
