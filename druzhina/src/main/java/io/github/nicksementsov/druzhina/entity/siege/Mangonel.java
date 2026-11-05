package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A mangonel: a skein of twisted rope drives its arm up against a padded crossbar, and the pot of burning pitch in its
 * cup flies on, ten to forty-four blocks, to burst in flames among the enemy and set fire to what burns. Quicker than a
 * trebuchet (five seconds), it does little to stone.
 */
public class Mangonel extends SiegeEngine {
    public static final float SCALE = 1.4f;
    public static final int RELEASE = 4;
    public static final int RELOAD = 100;
    private static final double MAX_SPEED = 1.45;
    private static final double CUP_UP = 3.5;
    private static final double CUP_FORWARD = 0.35;

    private int launchIn;
    @Nullable
    private Vec3 launchAt;

    public Mangonel(EntityType<? extends Mangonel> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(90, 6, 0.17);
    }

    @Override
    public int reloadTicks() {
        return RELOAD;
    }

    @Override
    public double minRange() {
        return 10;
    }

    @Override
    public double maxRange() {
        return 44;
    }

    @Override
    protected boolean needsSight() {
        return false;
    }

    @Override
    protected float turnSpeed() {
        return 2.5f;
    }

    @Override
    protected double crewBack() {
        return 1.0;
    }

    /** Beside the winch, clear of the arm wound down behind. */
    @Override
    protected double crewSide() {
        return 1.6;
    }

    private Vec3 cup(Vec3 at) {
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(this.getX() + dx / d * CUP_FORWARD, this.getY() + CUP_UP, this.getZ() + dz / d * CUP_FORWARD);
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        Vec3 from = this.cup(at);
        double v = speedFor(this.horizontal(at), at.y - from.y, Math.PI / 4);
        if (Double.isNaN(v) || v > MAX_SPEED) {
            return false;
        }
        this.launchAt = at;
        this.launchIn = RELEASE;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 1.4f, 0.4f);
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.launchIn > 0 && --this.launchIn == 0 && this.launchAt != null) {
            this.launch(this.launchAt);
            this.launchAt = null;
        }
        int r = this.reload();
        if (r > RELOAD * 0.25 && r < RELOAD - 16 && r % 14 == 0) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.NEUTRAL, 0.6f, 0.5f);
        }
    }

    private void launch(Vec3 at) {
        Soldier crew = this.crew();
        if (crew == null) {
            return;
        }
        Vec3 from = this.cup(at);
        double dx = at.x - from.x;
        double dz = at.z - from.z;
        double v = speedFor(Math.sqrt(dx * dx + dz * dz), at.y - from.y, Math.PI / 4);
        if (Double.isNaN(v)) {
            v = MAX_SPEED;
        }
        v = Math.min(MAX_SPEED, v) * (1 + (this.random.nextDouble() - 0.5) * 0.05);
        double yaw = Mth.atan2(dz, dx) + (this.random.nextDouble() - 0.5) * 0.05;
        double c = Math.cos(Math.PI / 4);
        FirePot pot = new FirePot(this.level(), crew);
        pot.setPos(from.x, from.y, from.z);
        pot.setDeltaMovement(Math.cos(yaw) * c * v, Math.sin(Math.PI / 4) * v, Math.sin(yaw) * c * v);
        this.level().addFreshEntity(pot);
        this.level().playSound(null, this.getX(), this.getY() + 2, this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.3f, 0.6f);
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.potShown() && this.random.nextInt(3) == 0) {
            // the pitch in the cup smoulders
            float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
            double back = 2.85;
            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, this.getX() + Mth.sin(yaw) * back,
                    this.getY() + 1.6, this.getZ() - Mth.cos(yaw) * back, 0, 0.01, 0);
        }
    }

    // ------------------------------------------------------------------ for the model

    /** The arm's turn, degrees: -78 wound back lying, 14 up against the crossbar after the throw. */
    public float armAngle(float partialTick) {
        if (this.loaded()) {
            return -78;
        }
        float t = this.sinceShot(partialTick);
        if (t < RELEASE + 1) {
            float k = t / (RELEASE + 1);
            return -78 + 92 * k * k;
        }
        if (t < RELEASE + 10) {
            return 14 - 4 * Mth.sin((t - RELEASE - 1) * 1.2f) * (1 - (t - RELEASE - 1) / 9);
        }
        float wind = Mth.clamp((t - RELEASE - 10) / (RELOAD * 0.75f - RELEASE - 10), 0, 1);
        return 14 - 92 * wind;
    }

    /** Whether a pot lies in the cup. */
    public boolean potShown() {
        return this.loaded() || this.sinceShot(0) > RELOAD * 0.8f || this.sinceShot(0) < RELEASE;
    }
}
