package io.github.nicksementsov.knightcombat.client;

import org.joml.Vector3f;

/**
 * Where the weapon is in the first person view: the point the hand holds (in blocks, x right, y up, z towards
 * the camera), the direction of the blade and the direction its flat side faces.
 */
public final class FirstPersonPose {
    final Vector3f hand;
    final Vector3f blade;
    final Vector3f flat;

    FirstPersonPose(Vector3f hand, Vector3f blade, Vector3f flat) {
        this.hand = hand;
        this.blade = new Vector3f(blade).normalize();
        this.flat = new Vector3f(flat).normalize();
    }

    static FirstPersonPose of(float hx, float hy, float hz, float bx, float by, float bz, float fx, float fy, float fz) {
        return new FirstPersonPose(new Vector3f(hx, hy, hz), new Vector3f(bx, by, bz), new Vector3f(fx, fy, fz));
    }

    static FirstPersonPose lerp(FirstPersonPose a, FirstPersonPose b, float t) {
        return new FirstPersonPose(PoseMath.lerp(a.hand, b.hand, t), PoseMath.lerp(a.blade, b.blade, t), PoseMath.lerp(a.flat, b.flat, t));
    }
}
