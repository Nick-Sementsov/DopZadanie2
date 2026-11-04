package io.github.nicksementsov.druzhina.compat;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier at the helm of a ship of Small Ships sails her as a helmsman does: he sets and takes in sail, holds the
 * rudder, keeps clear of the shore and of shallows, and backs off when she runs aground. Where he takes her depends
 * on his orders:
 * <ul>
 * <li>holding with a place: there, and he stays there; holding without one: he lies to where she is;</li>
 * <li>following: after his commander's ship, or to the shore where his commander stands, and lies to there so that
 * the men go ashore (with his commander aboard he waits for a course);</li>
 * <li>charging: at the enemy, and with guns aboard he turns to bring the enemy abeam, on the side with the guns, at
 * a cannon's range.</li>
 * </ul>
 */
public final class Helm {
    /** Ticks between two thoughts of the helmsman. */
    public static final int PERIOD = 2;
    /** A broadside is given from this far. */
    private static final double BROADSIDE = 20;

    private final Soldier captain;
    @Nullable
    private CompoundTag profile;
    private int sailTimer;
    private int stillTicks;
    private int reverseTicks;
    private int reverseTurn;
    private int stalls;
    private int movingTicks;
    private boolean stalled;

    public Helm(Soldier captain) {
        this.captain = captain;
    }

    /** She can get no nearer to where she is to go (the shore, shallows): the men may go ashore from here. */
    public boolean stalled() {
        return this.stalled;
    }

    /** What to do now: a place to get to, or a heading to keep, and how much sail. */
    private record Plan(@Nullable Vec3 to, double arrive, @Nullable Float heading, int sail) {
        static final Plan STOP = new Plan(null, 0, null, 0);
    }

    public void tick(Entity ship) {
        this.repelBoarders(ship);
        if (!Vessels.canMakeWay(ship)) {
            Vessels.keys(ship, false, false, false, false);
            return;
        }
        if (this.profile == null) {
            this.profile = Vessels.profile(ship);
        }
        if (this.sailTimer > 0) {
            this.sailTimer -= PERIOD;
        }
        float speed = Vessels.speed(ship);

        // aground: back her off, the rudder over
        if (this.reverseTicks > 0) {
            this.reverseTicks -= PERIOD;
            this.setSail(ship, 0);
            Vessels.keys(ship, false, true, this.reverseTurn < 0, this.reverseTurn > 0);
            return;
        }

        Plan plan = this.plan(ship);
        float yaw = ship.getYRot();
        Float want = plan.heading();
        int sail = plan.sail();
        boolean landing = false;
        if (want == null) {
            if (plan.to() == null) {
                this.lieTo(ship);
                return;
            }
            double dx = plan.to().x - ship.getX();
            double dz = plan.to().z - ship.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < plan.arrive()) {
                this.stalled = !water(ship.level(), plan.to());
                this.lieTo(ship);
                return;
            }
            want = yawTo(dx, dz);
            sail = dist > 48 ? 4 : dist > 28 ? 3 : dist > 14 ? 2 : 1;
            // a place on land: as near as she can get, then lie to
            landing = !water(ship.level(), plan.to()) && dist < 28;
        }

        float look = this.halfLength(ship) + 3 + Math.abs(speed) * 25;
        Float course = this.clearCourse(ship, want, look);
        if (course == null) {
            if (landing) {
                this.stalled = true;
                this.lieTo(ship);
                return;
            }
            course = want;
            sail = 1;
        }
        if (Math.abs(Mth.wrapDegrees(course - yaw)) > 75) {
            // a sharp turn under little sail
            sail = Math.min(sail, 1);
        }

        // stuck under sail: back off
        if (sail > 0 && Math.abs(speed) < 0.01f) {
            this.stillTicks += PERIOD;
            if (this.stillTicks > 80) {
                this.stillTicks = 0;
                this.reverseTicks = 50;
                this.reverseTurn = this.captain.getRandom().nextBoolean() ? 1 : -1;
                if (++this.stalls >= 3) {
                    this.stalled = true;
                }
                return;
            }
        } else {
            this.stillTicks = 0;
        }
        if (Math.abs(speed) > 0.05f) {
            this.movingTicks += PERIOD;
            if (this.movingTicks > 100) {
                this.stalls = 0;
                this.stalled = false;
            }
        } else {
            this.movingTicks = 0;
        }

        // the rudder: aim at the course, easing off before she gets there
        float error = Mth.wrapDegrees(course - yaw);
        float predicted = error - Vessels.rotSpeed(ship) * 6;
        boolean right = predicted > 3;
        boolean left = predicted < -3;
        boolean[] sailKeys = this.setSail(ship, sail);
        Vessels.keys(ship, sailKeys[0], sailKeys[1], left, right);
    }

    /**
     * A ship that no player steers takes in any creature that bumps into her, as a boat does: whoever is not of her
     * side (an enemy, a stray beast) is thrown overboard. Players climb aboard as they like.
     */
    private void repelBoarders(Entity ship) {
        for (Entity p : ship.getPassengers()) {
            if (p != this.captain && !(p instanceof Player) && !this.captain.isAlliedTo(p)) {
                p.stopRiding();
                p.setDeltaMovement(p.getDeltaMovement().add((p.getX() - ship.getX()) * 0.15, 0.3, (p.getZ() - ship.getZ()) * 0.15));
            }
        }
    }

    /** No way on her: sail in, rudder amidships. */
    private void lieTo(Entity ship) {
        this.setSail(ship, 0);
        Vessels.keys(ship, false, false, false, false);
    }

    /** Brings the sail to {@code level} (0 to 4); returns the forward and back keys to hold for it (oars too). */
    private boolean[] setSail(Entity ship, int level) {
        CompoundTag p = this.profile == null ? new CompoundTag() : this.profile;
        boolean sails = p.getBoolean("HasSails");
        boolean oars = p.getBoolean("HasOars");
        boolean forward = false;
        boolean back = false;
        if (sails) {
            int state = Vessels.sailState(ship);
            if (level == 0 && state > 0 || level > 0 && state == 0) {
                if (this.sailTimer <= 0) {
                    Vessels.toggleSail(ship);
                    this.sailTimer = 20;
                }
            } else if (state > 0 && level > state) {
                forward = true;
            } else if (state > 0 && level < state) {
                back = true;
            }
        }
        if (oars && level > 0 && !back) {
            forward = true;
        }
        return new boolean[]{forward, back};
    }

    private Plan plan(Entity ship) {
        Soldier s = this.captain;
        Player boss = s.commander();
        return switch (s.orders()) {
            case HOLD, PATROL -> s.post() == null ? Plan.STOP : new Plan(s.post(), 6, null, 0);
            case FOLLOW -> this.follow(ship, boss);
            case CHARGE -> {
                LivingEntity t = this.enemy(ship);
                yield t == null ? this.follow(ship, boss) : this.attack(ship, t);
            }
        };
    }

    private Plan follow(Entity ship, @Nullable Player boss) {
        if (boss == null || boss.level() != ship.level()) {
            return Plan.STOP;
        }
        Entity his = Vessels.vesselOf(boss);
        if (his == ship) {
            // he is aboard: she waits for the course he gives
            return Plan.STOP;
        }
        if (his != null) {
            return new Plan(his.position(), 10 + ship.getBbWidth() + his.getBbWidth(), null, 0);
        }
        return new Plan(boss.position(), 8, null, 0);
    }

    /** The enemy to sail at: his own, or the nearest within sight of the ship. */
    @Nullable
    private LivingEntity enemy(Entity ship) {
        Soldier s = this.captain;
        LivingEntity t = s.getTarget();
        if (t != null && t.isAlive() && t.distanceToSqr(ship) < 80 * 80 && !ship.hasPassenger(t)) {
            return t;
        }
        LivingEntity best = null;
        double bestDist = 64 * 64;
        for (LivingEntity e : ship.level().getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(64),
                e -> e.isAlive() && !ship.hasPassenger(e) && s.isFoe(e))) {
            double d = e.distanceToSqr(ship);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private Plan attack(Entity ship, LivingEntity t) {
        CompoundTag p = this.profile == null ? new CompoundTag() : this.profile;
        int port = p.getInt("CannonsPort");
        int starboard = p.getInt("CannonsStarboard");
        double dx = t.getX() - ship.getX();
        double dz = t.getZ() - ship.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (port + starboard == 0 || dist > BROADSIDE + 12) {
            // no guns (the archers do the work), or still far: straight at him
            return new Plan(t.position(), port + starboard == 0 ? 7 : 0, null, 0);
        }
        // guns: bring him abeam, on the side with more of them, nearer the way she heads now
        float bearing = yawTo(dx, dz);
        float yaw = ship.getYRot();
        float toStarboard = bearing - 90;
        float toPort = bearing + 90;
        boolean useStarboard;
        if (starboard == 0 || port == 0) {
            useStarboard = starboard > 0;
        } else {
            useStarboard = Math.abs(Mth.wrapDegrees(toStarboard - yaw)) <= Math.abs(Mth.wrapDegrees(toPort - yaw));
        }
        float heading = useStarboard ? toStarboard : toPort;
        // too near: bear away; too far: close a little
        if (dist < BROADSIDE - 8) {
            heading += useStarboard ? -30 : 30;
        } else if (dist > BROADSIDE + 4) {
            heading += useStarboard ? 20 : -20;
        }
        return new Plan(null, 0, heading, 1);
    }

    /** The heading nearest to {@code want} that is clear water ahead, or null when she is boxed in. */
    @Nullable
    private Float clearCourse(Entity ship, float want, float look) {
        for (float off : new float[]{0, 20, -20, 40, -40, 65, -65, 90, -90, 125, -125}) {
            if (this.clear(ship, want + off, look)) {
                return want + off;
            }
        }
        return null;
    }

    /** Clear water ahead on {@code heading}, as wide as she is, for {@code look} blocks past her bow. */
    private boolean clear(Entity ship, float heading, float look) {
        Level level = ship.level();
        float h = heading * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(h);
        double fz = Mth.cos(h);
        double rx = -fz;
        double rz = fx;
        double half = this.halfLength(ship);
        double beam = this.profile == null || this.profile.getFloat("Beam") <= 0 ? ship.getBbWidth() / 2 : this.profile.getFloat("Beam") / 2;
        int depth = this.profile == null ? 1 : Math.max(1, Math.min(3, this.profile.getInt("RequiredDepth")));
        int y = Mth.floor(ship.getY() + 0.3);
        for (double d = 1; d <= look; d += 1.5) {
            for (double side : new double[]{-beam, 0, beam}) {
                double x = ship.getX() + fx * (half + d) + rx * side;
                double z = ship.getZ() + fz * (half + d) + rz * side;
                BlockPos pos = BlockPos.containing(x, y, z);
                for (int k = 0; k < depth; k++) {
                    if (!level.getFluidState(pos.below(k)).is(FluidTags.WATER)) {
                        return false;
                    }
                }
                if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private float halfLength(Entity ship) {
        float length = this.profile == null ? 0 : this.profile.getFloat("Length");
        return length > 0 ? length / 2 : ship.getBbWidth() / 2;
    }

    /** Water where {@code p} is (or just below it). */
    private static boolean water(Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        return level.getFluidState(pos).is(FluidTags.WATER) || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    /** The yaw that looks along (dx, dz). */
    private static float yawTo(double dx, double dz) {
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
    }
}
