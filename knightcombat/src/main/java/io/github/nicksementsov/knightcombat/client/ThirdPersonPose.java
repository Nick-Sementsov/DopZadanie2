package io.github.nicksementsov.knightcombat.client;

import org.joml.Vector3f;

/**
 * Pose of a fighter's model for a right-handed player, relative to the body. Directions are in model space
 * (see {@link PoseMath#dir}): where the weapon arm points, where the blade points and which way its flat side
 * faces, where the free arm points, plus the twist of the body and the extras of kicks and battle cries.
 */
public final class ThirdPersonPose {
    final Vector3f arm;
    final Vector3f blade;
    final Vector3f flat;
    final Vector3f off;
    /** Twist of the upper body in radians, positive turns the weapon shoulder back. */
    float bodyYaw;
    /** How far the weapon shoulder moves forward, in pixels (stabs). */
    float lunge;
    /** Rotation of the kicking leg and how much it replaces the walking animation. */
    float leg;
    float legWeight;
    /** Added to the head pitch, negative looks up. */
    float head;

    ThirdPersonPose(Vector3f arm, Vector3f blade, Vector3f flat, Vector3f off, float bodyYaw) {
        this.arm = arm;
        this.blade = blade;
        this.flat = flat;
        this.off = off;
        this.bodyYaw = bodyYaw;
    }

    ThirdPersonPose lunge(float value) {
        lunge = value;
        return this;
    }

    ThirdPersonPose leg(float angle, float weight) {
        leg = angle;
        legWeight = weight;
        return this;
    }

    ThirdPersonPose head(float value) {
        head = value;
        return this;
    }

    static ThirdPersonPose lerp(ThirdPersonPose a, ThirdPersonPose b, float t) {
        ThirdPersonPose pose = new ThirdPersonPose(nlerp(a.arm, b.arm, t), nlerp(a.blade, b.blade, t), nlerp(a.flat, b.flat, t),
                nlerp(a.off, b.off, t), PoseMath.lerp(a.bodyYaw, b.bodyYaw, t));
        pose.lunge = PoseMath.lerp(a.lunge, b.lunge, t);
        pose.leg = PoseMath.lerp(a.leg, b.leg, t);
        pose.legWeight = PoseMath.lerp(a.legWeight, b.legWeight, t);
        pose.head = PoseMath.lerp(a.head, b.head, t);
        return pose;
    }

    private static Vector3f nlerp(Vector3f a, Vector3f b, float t) {
        Vector3f v = new Vector3f(a).lerp(b, t);
        return v.lengthSquared() < 1.0E-6f ? new Vector3f(b) : v.normalize();
    }
}
