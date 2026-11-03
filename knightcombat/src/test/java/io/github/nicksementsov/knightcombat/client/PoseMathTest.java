package io.github.nicksementsov.knightcombat.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoseMathTest {
    @Test
    void directionsAreConvertedToModelSpace() {
        assertVector(new Vector3f(-1, 0, 0), PoseMath.dir(1, 0, 0));
        assertVector(new Vector3f(0, -1, 0), PoseMath.dir(0, 1, 0));
        assertVector(new Vector3f(0, 0, -1), PoseMath.dir(0, 0, 1));
    }

    @Test
    void armFramePointsTheArmAndTheBlade() {
        Vector3f arm = PoseMath.dir(0.3f, -0.8f, 0.5f);
        Vector3f blade = PoseMath.dir(-0.2f, 0.7f, 0.6f);
        Quaternionf q = PoseMath.armFrame(arm, blade);
        assertVector(arm, q.transform(new Vector3f(0, 1, 0)));
        Vector3f perpendicular = new Vector3f(blade).fma(-blade.dot(arm), arm).normalize();
        assertVector(perpendicular, q.transform(new Vector3f(0, 0, -1)));
    }

    @Test
    void eulerAnglesRebuildTheSameRotation() {
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            Quaternionf q = new Quaternionf().rotationXYZ(random.nextFloat() * 6 - 3, random.nextFloat() * 3 - 1.5f, random.nextFloat() * 6 - 3);
            Vector3f euler = PoseMath.eulerZYX(q);
            Quaternionf rebuilt = new Quaternionf().rotationZYX(euler.z, euler.y, euler.x);
            for (Vector3f v : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                assertVector(q.transform(new Vector3f(v)), rebuilt.transform(new Vector3f(v)));
            }
        }
    }

    @Test
    void frameMapsOnePairOfAxesOntoAnother() {
        Vector3f a = new Vector3f(1, 1, 0).normalize();
        Vector3f b = new Vector3f(0, 0, 1);
        Vector3f ta = new Vector3f(0.2f, -0.5f, 0.8f).normalize();
        Vector3f tb = PoseMath.perpendicular(ta);
        Quaternionf q = PoseMath.frame(a, b, ta, tb);
        assertVector(ta, q.transform(new Vector3f(a)));
        assertVector(tb, q.transform(new Vector3f(b)));
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-4);
        assertEquals(expected.y, actual.y, 1e-4);
        assertEquals(expected.z, actual.z, 1e-4);
    }
}
