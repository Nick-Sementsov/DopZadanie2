package io.github.nicksementsov.knightcombat.combat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * Follows how fast a mount runs, for the force of a couched lance. A ridden mount is moved by its rider's client, and
 * the moves reach the server one, none or several to a tick, so the speed is taken over half a second, not one tick.
 */
public final class MountTracker {
    /** Ticks the speed is averaged over. */
    static final int WINDOW = 10;
    /** A step longer than this, in blocks, is a teleport, not a ride. */
    private static final double TELEPORT = 4;

    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();

    /** Records where the mount is this tick; {@code null} when the rider is on foot. */
    public void track(@Nullable Vec3 at) {
        if (at == null) {
            trail.clear();
            return;
        }
        Vec3 last = trail.peekLast();
        if (last != null && last.distanceToSqr(at) > TELEPORT * TELEPORT) {
            trail.clear();
        }
        trail.addLast(at);
        while (trail.size() > WINDOW + 1) {
            trail.removeFirst();
        }
    }

    /** The average motion of the mount over the last ticks, in blocks per tick. */
    public Vec3 motion() {
        if (trail.size() < 2) {
            return Vec3.ZERO;
        }
        return trail.peekLast().subtract(trail.peekFirst()).scale(1.0 / (trail.size() - 1));
    }

    /** Speed of the mount along a direction given by its yaw in radians (as Minecraft turns entities); never negative. */
    public float speedAlong(float yaw) {
        Vec3 motion = motion();
        return (float) Math.max(0, -motion.x * Math.sin(yaw) + motion.z * Math.cos(yaw));
    }
}
