package io.github.nicksementsov.knightcombat.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Small helpers for building poses from directions. Poses are described the way a person would:
 * "the arm points forward and down, the blade points up". Vectors given as (right, up, forward)
 * are converted to the model space of Minecraft's humanoid models, where right is -X, up is -Y and forward is -Z.
 */
public final class PoseMath {
    /** In the space of a model arm the arm points along +Y and a held item's blade along this direction (vanilla grip). */
    public static final Vector3f ARM_AXIS = new Vector3f(0, 1, 0);
    public static final Vector3f GRIP_AXIS = new Vector3f(0, 0.17365f, -0.98481f);
    /** The point of the right hand that holds items, in pixels, in the space of the right arm. */
    public static final Vector3f RIGHT_GRIP = new Vector3f(-1, 10, -2);
    public static final Vector3f LEFT_GRIP = new Vector3f(1, 10, -2);

    private PoseMath() {
    }

    /** A direction given as (right, up, forward), in model space, normalized. */
    public static Vector3f dir(float right, float up, float forward) {
        return new Vector3f(-right, -up, -forward).normalize();
    }

    /**
     * Rotation of an arm so that it points along {@code arm} and the item in its hand points along {@code blade}
     * (as closely as the grip allows: the blade is kept perpendicular to the arm).
     */
    public static Quaternionf armFrame(Vector3f arm, Vector3f blade) {
        Vector3f a = new Vector3f(arm).normalize();
        Vector3f b = new Vector3f(blade).fma(-blade.dot(a), a);
        if (b.lengthSquared() < 1.0E-6f) {
            b = perpendicular(a);
        }
        b.normalize();
        // Local frame: arm axis (0,1,0) and the blade perpendicular (0,0,-1).
        return frame(new Vector3f(0, 1, 0), new Vector3f(0, 0, -1), a, b);
    }

    /**
     * Rotation that maps the orthonormal pair (localA, localB) onto the orthonormal pair (targetA, targetB).
     */
    public static Quaternionf frame(Vector3f localA, Vector3f localB, Vector3f targetA, Vector3f targetB) {
        Quaternionf toLocal = basis(localA, localB);
        Quaternionf toTarget = basis(targetA, targetB);
        return toTarget.mul(toLocal.conjugate());
    }

    /** Rotation from the standard basis to the basis (a, b, a x b). */
    private static Quaternionf basis(Vector3f a, Vector3f b) {
        Vector3f c = new Vector3f(a).cross(b);
        org.joml.Matrix3f m = new org.joml.Matrix3f(
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                c.x, c.y, c.z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** In item textures the blade goes from the bottom left to the top right corner and the flat side faces +Z. */
    private static final Vector3f SPRITE_BLADE = new Vector3f(1, 1, 0).normalize();
    private static final Vector3f SPRITE_FLAT = new Vector3f(0, 0, 1);

    /** Rotation that turns an item texture so its blade points along {@code blade} and its flat side faces {@code flat}. */
    public static Quaternionf spriteRotation(Vector3f blade, Vector3f flat) {
        Vector3f b = new Vector3f(blade).normalize();
        Vector3f f = new Vector3f(flat).fma(-flat.dot(b), b);
        if (f.lengthSquared() < 1.0E-6f) {
            f = perpendicular(b);
        }
        f.normalize();
        return frame(new Vector3f(SPRITE_BLADE), new Vector3f(SPRITE_FLAT), b, f);
    }

    /** Some unit vector perpendicular to {@code v}. */
    public static Vector3f perpendicular(Vector3f v) {
        Vector3f helper = Math.abs(v.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
        return helper.cross(v).normalize();
    }

    /**
     * Euler angles (x, y, z) such that {@code new Quaternionf().rotationZYX(z, y, x)} equals {@code q}, as model parts use them.
     * (Computed here because Quaternionf.getEulerAnglesZYX of the JOML version shipped with Minecraft 1.20.1 gets it wrong.)
     */
    public static Vector3f eulerZYX(Quaternionf q) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        float sinPitch = Math.max(-1f, Math.min(1f, 2 * (w * y - x * z)));
        return new Vector3f(
                (float) Math.atan2(2 * (y * z + w * x), 1 - 2 * (x * x + y * y)),
                (float) Math.asin(sinPitch),
                (float) Math.atan2(2 * (x * y + w * z), 1 - 2 * (y * y + z * z)));
    }

    /** Rotation about the vertical axis of the model by {@code angle} radians (positive turns towards the right side). */
    public static Quaternionf yaw(float angle) {
        return new Quaternionf().rotationY(angle);
    }

    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    public static float easeIn(float t) {
        t = clamp01(t);
        return t * t;
    }

    public static float easeOut(float t) {
        t = clamp01(t);
        return 1 - (1 - t) * (1 - t);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    public static Quaternionf slerp(Quaternionf a, Quaternionf b, float t) {
        return new Quaternionf(a).slerp(b, t);
    }
}
