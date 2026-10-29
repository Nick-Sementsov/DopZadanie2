package io.github.nicksementsov.knightcombat.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MountTrackerTest {
    private static final float NORTH_TO_SOUTH = 0;

    @Test
    void movesThatArriveInBatchesStillGiveTheSpeed() {
        MountTracker tracker = new MountTracker();
        double z = 0;
        // The rider's client sends a move every tick, but the server takes them in up to three at once.
        int[] batches = {1, 0, 2, 1, 3, 0, 0, 1, 2, 0, 1, 1, 0, 2, 1, 0, 3, 0, 0, 1};
        for (int batch : batches) {
            z += 0.7 * batch;
            tracker.track(new Vec3(0, 64, z));
        }
        // Over the last ten ticks the mount moved 10 moves of 0.7 blocks.
        assertEquals(0.7, tracker.speedAlong(NORTH_TO_SOUTH), 0.15);
    }

    @Test
    void onlyTheRunAlongTheLanceCounts() {
        MountTracker tracker = new MountTracker();
        for (int i = 0; i <= 10; i++) {
            tracker.track(new Vec3(0.5 * i, 64, 0));
        }
        assertEquals(0, tracker.speedAlong(NORTH_TO_SOUTH), 1.0E-6, "running sideways does not drive the lance in");
        // Facing east (yaw -90 degrees) the run is straight along the lance.
        assertEquals(0.5, tracker.speedAlong((float) Math.toRadians(-90)), 1.0E-6);
        assertEquals(0, tracker.speedAlong((float) Math.toRadians(90)), 1.0E-6, "nor running backwards");
    }

    @Test
    void aTeleportOrADismountStartsOver() {
        MountTracker tracker = new MountTracker();
        for (int i = 0; i <= 10; i++) {
            tracker.track(new Vec3(0, 64, 0.6 * i));
        }
        tracker.track(new Vec3(0, 64, 100));
        assertEquals(0, tracker.motion().length(), 1.0E-6);
        tracker.track(new Vec3(0, 64, 100.3));
        assertEquals(0.3, tracker.motion().z, 1.0E-6);
        tracker.track(null);
        assertEquals(0, tracker.motion().length(), 1.0E-6);
    }
}
