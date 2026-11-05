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
 * A trebuchet on wheels: a counterweight lifts a long arm that hurls a stone in a high arc, over walls, at places
 * twelve to sixty-four blocks away. Slow to wind back (ten seconds), slow to move; the stone crushes the men where it
 * falls and cracks the walls.
 */
public class Trebuchet extends SiegeEngine {
    /** How much larger it is drawn than its model (the model is in pixels of a block). */
    public static final float SCALE = 1.6f;
    /** Ticks from the release of the weight to the stone leaving the sling: the arm swings up first. */
    public static final int RELEASE = 7;
    public static final int RELOAD = 200;
    private static final double SPEED = 1.8;
    /** The sling's height above the ground and its reach forward when the stone leaves it. */
    private static final double SLING_UP = 9.0;
    private static final double SLING_FORWARD = 3.6;

    private int launchIn;
    @Nullable
    private Vec3 launchAt;

    public Trebuchet(EntityType<? extends Trebuchet> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(140, 8, 0.15);
    }

    @Override
    public int reloadTicks() {
        return RELOAD;
    }

    @Override
    public double minRange() {
        return 12;
    }

    @Override
    public double maxRange() {
        return 64;
    }

    @Override
    protected boolean needsSight() {
        return false;
    }

    @Override
    protected float turnSpeed() {
        return 1.5f;
    }

    @Override
    protected double crewBack() {
        return 1.0;
    }

    /** Beside the winch, clear of the arm as it swings down. */
    @Override
    protected double crewSide() {
        return 2.7;
    }

    /** The throw it makes: a stone leaves the sling at about fifty degrees, faster the farther it goes. */
    private static final double THROW = 50 * Mth.DEG_TO_RAD;

    /**
     * How to throw at a place {@code x} away and {@code y} up: {angle in radians, speed}. The usual throw at fifty
     * degrees; when that would want more speed than the arm gives, the highest arc it can make; null when nothing
     * reaches.
     */
    @Nullable
    static double[] aim(double x, double y) {
        double c = Math.cos(THROW);
        double rise = x * Math.tan(THROW) - y;
        if (rise > 0) {
            double v = Math.sqrt(SiegeStone.GRAVITY * x * x / (2 * c * c * rise));
            if (v <= SPEED) {
                return new double[]{THROW, v};
            }
        }
        double angle = launchAngle(x, y, SPEED, SiegeStone.GRAVITY, true);
        return Double.isNaN(angle) ? null : new double[]{angle * Mth.DEG_TO_RAD, SPEED};
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        Vec3 from = this.sling(at);
        if (aim(this.horizontal(at), at.y - from.y) == null) {
            return false;
        }
        // the weight drops; the stone flies when the arm is up
        this.launchAt = at;
        this.launchIn = RELEASE;
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.2f, 0.45f);
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
        if (r > RELOAD * 0.15 && r < RELOAD - 30 && r % 22 == 0) {
            // the windlass creaks as they wind the arm down
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.NEUTRAL, 0.7f, 0.4f);
        }
    }

    /** Where the stone leaves the sling, thrown toward {@code at}. */
    private Vec3 sling(Vec3 at) {
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        return new Vec3(this.getX() + dx / d * SLING_FORWARD, this.getY() + SLING_UP, this.getZ() + dz / d * SLING_FORWARD);
    }

    private void launch(Vec3 at) {
        Soldier crew = this.crew();
        if (crew == null) {
            return;
        }
        Vec3 from = this.sling(at);
        double dx = at.x - from.x;
        double dz = at.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double[] how = aim(dist, at.y - from.y);
        if (how == null) {
            how = new double[]{Math.PI / 4, SPEED};
        }
        double a = how[0];
        // no engine throws twice alike
        double speed = how[1] * (1 + (this.random.nextDouble() - 0.5) * 0.03);
        double yaw = Mth.atan2(dz, dx) + (this.random.nextDouble() - 0.5) * 0.03;
        Vec3 v = new Vec3(Math.cos(yaw) * Math.cos(a) * speed, Math.sin(a) * speed, Math.sin(yaw) * Math.cos(a) * speed);
        SiegeStone stone = new SiegeStone(this.level(), crew);
        stone.setPos(from.x, from.y, from.z);
        stone.setDeltaMovement(v);
        this.level().addFreshEntity(stone);
        this.level().playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.6f, 0.45f);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 1.4f, 0.5f);
    }

    // ------------------------------------------------------------------ for the model

    /** The arm's turn about its axle, degrees: -35 wound down (the sling at the back), 140 thrown over the front. */
    public float armAngle(float partialTick) {
        if (this.loaded()) {
            return -35;
        }
        float t = this.sinceShot(partialTick);
        if (t < RELEASE + 1) {
            // the weight falls: the arm whips up and over
            float k = t / (RELEASE + 1);
            return -35 + 175 * k * k;
        }
        if (t < RELEASE + 14) {
            // it rocks on its axle
            float k = (t - RELEASE - 1) / 13f;
            return 140 - 18 * Mth.sin(k * Mth.PI) * (1 - k);
        }
        float wind = Mth.clamp((t - RELEASE - 14) / (RELOAD * 0.8f - RELEASE - 14), 0, 1);
        return 140 - 175 * wind;
    }

    /** Whether a stone lies in the sling. */
    public boolean stoneShown() {
        return this.loaded() || this.sinceShot(0) > RELOAD * 0.85f || this.sinceShot(0) < RELEASE;
    }
}
