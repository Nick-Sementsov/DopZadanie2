package io.github.nicksementsov.knightcombat.client;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeldModelTest {
    /** An axe drawn along a diagonal: a handle from the hand to the tip and a head sticking out to one side near the tip. */
    @Test
    void bladeGoesToTheFarEndAndEdgeToTheHead() {
        Vector3f along = new Vector3f(1, 1, 0).normalize();
        Vector3f side = new Vector3f(-1, 1, 0).normalize();
        List<Vector3f> points = new ArrayList<>();
        for (int i = -2; i <= 20; i++) {
            float t = i / 20f;
            points.add(new Vector3f(along).mul(t).fma(0.02f, side));
            points.add(new Vector3f(along).mul(t).fma(-0.02f, side));
        }
        for (int i = 15; i <= 20; i++) {
            for (int j = 0; j <= 6; j++) {
                points.add(new Vector3f(along).mul(i / 20f).fma(j / 20f, side));
            }
        }
        HeldModel.Axes axes = HeldModel.principalAxes(points);
        assertTrue(axes.blade().dot(along) > 0.95f, "blade " + axes.blade());
        assertTrue(axes.edge().dot(side) > 0.9f, "edge " + axes.edge());
        assertEquals(0, axes.blade().dot(axes.edge()), 1e-4);
        assertEquals(1.0f, axes.length(), 0.08f);
        Vector3f tip = axes.point(1);
        assertTrue(tip.distance(along) < 0.15f, "tip " + tip);
    }

    /** A spear held in the middle points towards its longer end. */
    @Test
    void bladePointsAwayFromTheShortEnd() {
        Vector3f along = new Vector3f(0, 0.3f, -1).normalize();
        List<Vector3f> points = new ArrayList<>();
        for (int i = -6; i <= 20; i++) {
            float t = i / 10f;
            points.add(new Vector3f(along).mul(t).add(0.01f, 0, 0));
            points.add(new Vector3f(along).mul(t).add(-0.01f, 0, 0));
        }
        HeldModel.Axes axes = HeldModel.principalAxes(points);
        assertTrue(axes.blade().dot(along) > 0.99f, "blade " + axes.blade());
        assertEquals(2.0f, axes.length(), 0.05f);
    }
}
