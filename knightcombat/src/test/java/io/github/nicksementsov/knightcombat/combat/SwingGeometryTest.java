package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingGeometryTest {
    private static final double[] FORWARD = {0, 0, 1};
    private static final double[] UP = {0, 1, 0};
    private static final double[] RIGHT = {-1, 0, 0};

    @Test
    void slashSweepsFromRightToLeftAndBackWhenMirrored() {
        assertEquals(SwingGeometry.SLASH_ARC, SwingGeometry.yaw(AttackType.SLASH, false, 0), 1e-6);
        assertEquals(-SwingGeometry.SLASH_ARC, SwingGeometry.yaw(AttackType.SLASH, false, 1), 1e-6);
        assertEquals(-SwingGeometry.SLASH_ARC, SwingGeometry.yaw(AttackType.SLASH, true, 0), 1e-6);
        assertEquals(0, SwingGeometry.yaw(AttackType.SLASH, false, 0.5f), 1e-6);
        assertTrue(SwingGeometry.pitch(AttackType.SLASH, 0) < SwingGeometry.pitch(AttackType.SLASH, 1), "slashes go downwards");
    }

    @Test
    void overheadComesDownFromAboveTheHead() {
        assertEquals(SwingGeometry.OVERHEAD_START, SwingGeometry.pitch(AttackType.OVERHEAD, 0), 1e-6);
        assertEquals(SwingGeometry.OVERHEAD_END, SwingGeometry.pitch(AttackType.OVERHEAD, 1), 1e-6);
        assertTrue(SwingGeometry.OVERHEAD_START < 0 && SwingGeometry.OVERHEAD_END > 0);
    }

    @Test
    void stabReachesFurtherDuringTheRelease() {
        assertEquals(SwingGeometry.STAB_START, SwingGeometry.reachFactor(AttackType.STAB, 0), 1e-6);
        assertEquals(1, SwingGeometry.reachFactor(AttackType.STAB, 1), 1e-6);
        assertTrue(SwingGeometry.reachFactor(AttackType.STAB, 0.3f) < SwingGeometry.reachFactor(AttackType.STAB, 0.6f));
        assertEquals(1, SwingGeometry.reachFactor(AttackType.SLASH, 0), 1e-6);
    }

    @Test
    void raysAreAtMostFiveDegreesApart() {
        for (int length = 1; length <= 10; length++) {
            for (int tick = 0; tick < length; tick++) {
                float p0 = tick / (float) length;
                float p1 = (tick + 1) / (float) length;
                int samples = SwingGeometry.samples(AttackType.SLASH, p0, p1);
                float step = Math.abs(SwingGeometry.yaw(AttackType.SLASH, false, p1) - SwingGeometry.yaw(AttackType.SLASH, false, p0)) / samples;
                assertTrue(step <= 5.0001f, "step " + step + " for release of " + length);
            }
        }
    }

    @Test
    void directionFollowsTheViewFrame() {
        double[] out = new double[3];
        SwingGeometry.direction(FORWARD, UP, RIGHT, 0, 0, out);
        assertVector(0, 0, 1, out);
        SwingGeometry.direction(FORWARD, UP, RIGHT, 90, 0, out);
        assertVector(-1, 0, 0, out);
        SwingGeometry.direction(FORWARD, UP, RIGHT, 0, 90, out);
        assertVector(0, -1, 0, out);
        SwingGeometry.direction(FORWARD, UP, RIGHT, 37, -21, out);
        assertEquals(1, Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]), 1e-9);
    }

    @Test
    void wallsOnlyStopTheMiddleOfASlash() {
        assertFalse(SwingGeometry.wallStops(AttackType.SLASH, false, 0));
        assertTrue(SwingGeometry.wallStops(AttackType.SLASH, false, 0.5f));
        assertFalse(SwingGeometry.wallStops(AttackType.SLASH, true, 1));
        assertTrue(SwingGeometry.wallStops(AttackType.STAB, false, 0));
        assertTrue(SwingGeometry.wallStops(AttackType.OVERHEAD, false, 0));
    }

    private static void assertVector(double x, double y, double z, double[] actual) {
        assertEquals(x, actual[0], 1e-9);
        assertEquals(y, actual[1], 1e-9);
        assertEquals(z, actual[2], 1e-9);
    }
}
