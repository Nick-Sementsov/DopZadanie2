package io.github.nicksementsov.knightcombat.client;

import org.joml.Vector3f;

/**
 * Where the weapon is in the first person view: the point the hand holds (in blocks, x right, y up, z towards
 * the camera), the direction of the blade and the direction its cutting edge faces (kept square to the blade).
 */
public final class FirstPersonPose {
    final Vector3f hand;
    final Vector3f blade;
    final Vector3f edge;

    FirstPersonPose(Vector3f hand, Vector3f blade, Vector3f edge) {
        this.hand = hand;
        this.blade = blade.lengthSquared() < 1.0E-6f ? new Vector3f(0, 1, 0) : new Vector3f(blade).normalize();
        Vector3f e = new Vector3f(edge).fma(-edge.dot(this.blade), this.blade);
        this.edge = e.lengthSquared() < 1.0E-6f ? PoseMath.perpendicular(this.blade) : e.normalize();
    }

    static FirstPersonPose of(float hx, float hy, float hz, float bx, float by, float bz, float ex, float ey, float ez) {
        return new FirstPersonPose(new Vector3f(hx, hy, hz), new Vector3f(bx, by, bz), new Vector3f(ex, ey, ez));
    }

    static FirstPersonPose lerp(FirstPersonPose a, FirstPersonPose b, float t) {
        return new FirstPersonPose(PoseMath.lerp(a.hand, b.hand, t), PoseMath.lerp(a.blade, b.blade, t), PoseMath.lerp(a.edge, b.edge, t));
    }
}
