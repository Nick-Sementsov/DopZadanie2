package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A ballista on a cart: a great crossbow of twisted sinew that shoots heavy bolts, nearly flat, at what it sees up to
 * forty-eight blocks away; a bolt goes through two or three men in a row. Quick to wind (three seconds), quick to move.
 */
public class Ballista extends SiegeEngine {
    public static final int RELOAD = 60;
    private static final float SPEED = 3.2f;
    private static final double MUZZLE_UP = 1.15;
    private static final double MUZZLE_FORWARD = 0.9;
    /** How high it aims, degrees (its head follows). */
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(Ballista.class, EntityDataSerializers.FLOAT);

    public Ballista(EntityType<? extends Ballista> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(60, 6, 0.2);
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
        return 3;
    }

    @Override
    public double maxRange() {
        return 48;
    }

    @Override
    protected boolean needsSight() {
        return true;
    }

    @Override
    protected float turnSpeed() {
        return 4.0f;
    }

    @Override
    protected double crewBack() {
        return 1.35;
    }

    private Vec3 muzzle(Vec3 at) {
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(this.getX() + dx / d * MUZZLE_FORWARD, this.getY() + MUZZLE_UP, this.getZ() + dz / d * MUZZLE_FORWARD);
    }

    /** The angle up to shoot at {@code at}: the low arc, a little higher for the air that slows the bolt. */
    private double angleTo(Vec3 at) {
        Vec3 from = this.muzzle(at);
        double dx = at.x - from.x;
        double dz = at.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return launchAngle(dist * (1 + dist / 300), at.y - from.y, SPEED, 0.05, false);
    }

    @Override
    protected void aimAt(Vec3 at) {
        double angle = this.angleTo(at);
        if (!Double.isNaN(angle)) {
            float now = this.aimPitch();
            float want = (float) Mth.clamp(angle, -30, 45);
            float next = Mth.approach(now, want, 3);
            if (Math.abs(next - now) > 0.2f) {
                this.entityData.set(PITCH, next);
            }
        }
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        double angle = this.angleTo(at);
        if (Double.isNaN(angle) || Math.abs(angle - this.aimPitch()) > 3) {
            return false;
        }
        Vec3 from = this.muzzle(at);
        double yaw = Mth.atan2(at.z - from.z, at.x - from.x);
        double a = angle * Mth.DEG_TO_RAD;
        BallistaBolt bolt = new BallistaBolt(this.level(), crew);
        bolt.setPos(from.x, from.y, from.z);
        bolt.shoot(Math.cos(yaw) * Math.cos(a), Math.sin(a), Math.sin(yaw) * Math.cos(a), SPEED, 1.2f);
        this.level().addFreshEntity(bolt);
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 1.6f, 0.5f);
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 0.7f);
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        int r = this.reload();
        if (r > 10 && r < RELOAD - 8 && r % 12 == 0) {
            // the winch clicks as they wind it
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.NEUTRAL, 0.6f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ for the model

    /** How far it is drawn: 0 just shot (the string forward), 1 drawn back and ready. */
    public float drawn(float partialTick) {
        if (this.loaded()) {
            return 1;
        }
        float t = this.sinceShot(partialTick);
        return Mth.clamp((t - 6) / (RELOAD * 0.75f - 6), 0, 1);
    }

    /** Whether a bolt lies in its groove. */
    public boolean boltShown() {
        return this.loaded() || this.sinceShot(0) > RELOAD * 0.8f;
    }
}
