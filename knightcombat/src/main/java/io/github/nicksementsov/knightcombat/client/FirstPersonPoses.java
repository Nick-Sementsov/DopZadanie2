package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import org.joml.Vector3f;

import static io.github.nicksementsov.knightcombat.client.PoseMath.lerp;
import static io.github.nicksementsov.knightcombat.client.PoseMath.smooth;

/**
 * Where the weapon is in the first person view in every phase. Coordinates: x right, y up, z towards the camera.
 * The flat side of the blade faces the camera wherever possible, so the weapon is seen and not just its edge.
 */
final class FirstPersonPoses {
    private static final float DEG = (float) Math.PI / 180f;
    /** Point ahead of the camera that thrusts aim at. */
    private static final Vector3f AIM = new Vector3f(0, 0, -3);

    private FirstPersonPoses() {
    }

    static FirstPersonPose current(FighterAnim anim, Grip grip, float partialTick) {
        if (anim == null) {
            return guard(grip);
        }
        if (anim.fpVersion != anim.version) {
            anim.fpVersion = anim.version;
            anim.fpFrom = anim.fpShown != null ? anim.fpShown : guard(grip);
        }
        float p = anim.progress(partialTick);
        float elapsed = anim.elapsed(partialTick);
        FirstPersonPose target = target(anim, grip, p);
        float blend = switch (anim.phase) {
            case WINDUP -> Math.max(2, anim.length * 0.6f);
            case RELEASE -> 1.5f;
            case BLOCK, STAGGER -> 2;
            case IDLE -> 4;
            case KICK, CRY -> 3;
            case RECOVERY -> 0;
        };
        FirstPersonPose shown = blend > 0 && elapsed < blend && anim.fpFrom != null
                ? FirstPersonPose.lerp(anim.fpFrom, target, smooth(elapsed / blend))
                : target;
        anim.fpShown = shown;
        return shown;
    }

    private static FirstPersonPose target(FighterAnim anim, Grip grip, float p) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        return switch (anim.phase) {
            case IDLE -> guard(grip);
            case BLOCK -> block(grip);
            case WINDUP -> FirstPersonPose.lerp(guard(grip), windupEnd(anim.attack, grip, mirrored), smooth(p));
            case RELEASE -> release(anim.attack, grip, mirrored, p);
            case RECOVERY -> {
                FirstPersonPose from = anim.fpFrom != null ? anim.fpFrom : guard(grip);
                yield FirstPersonPose.lerp(from, guard(grip), smooth(p));
            }
            case STAGGER -> {
                FirstPersonPose hit = anim.has(CombatState.GUARD_BROKEN)
                        ? FirstPersonPose.of(0.5f, -0.75f, -0.6f, -0.1f, -0.5f, -1f, 0.3f, 1, 0)
                        : FirstPersonPose.of(0.65f, -0.55f, -0.5f, 0.35f, 0.75f, -0.3f, 0.2f, 0, 1);
                yield p < 0.6f ? hit : FirstPersonPose.lerp(hit, guard(grip), smooth((p - 0.6f) / 0.4f));
            }
            case KICK -> FirstPersonPose.lerp(guard(grip),
                    FirstPersonPose.of(0.55f, -0.62f, -0.6f, -0.25f, 0.75f, -0.5f, 0.4f, 0.1f, 1), smooth(p * 2));
            case CRY -> {
                FirstPersonPose cry = FirstPersonPose.of(0.42f, -0.12f, -0.7f, -0.1f, 1, -0.3f, 0.3f, 0, 1);
                yield p < 0.7f ? cry : FirstPersonPose.lerp(cry, guard(grip), smooth((p - 0.7f) / 0.3f));
            }
        };
    }

    static FirstPersonPose guard(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> FirstPersonPose.of(0.5f, -0.5f, -0.6f, -0.3f, 0.8f, -0.45f, 0.4f, 0.1f, 1);
            case TWO_HANDED -> FirstPersonPose.of(0.3f, -0.5f, -0.62f, -0.3f, 0.75f, -0.6f, 0.4f, 0.1f, 1);
            case POLEARM -> aimed(0.4f, -0.45f, -0.5f, 1.0f);
        };
    }

    private static FirstPersonPose block(Grip grip) {
        // Raised across the upper half of the view, so the enemy stays visible below the blade.
        return FirstPersonPose.of(0.32f, 0.12f, -0.6f, -1f, 0.32f, -0.25f, 0, 0.25f, 1);
    }

    private static FirstPersonPose windupEnd(AttackType attack, Grip grip, boolean mirrored) {
        return switch (attack) {
            case SLASH -> mirrored
                    ? FirstPersonPose.of(-0.5f, 0.0f, -0.5f, -0.45f, 0.75f, 0.45f, 0.3f, 0, 1)
                    : FirstPersonPose.of(0.7f, 0.05f, -0.5f, 0.4f, 0.75f, 0.5f, -0.3f, 0, 1);
            case OVERHEAD -> FirstPersonPose.of(0.3f, 0.5f, -0.4f, 0.1f, 0.6f, 0.8f, 0.3f, 0, 1);
            case STAB -> aimed(0.45f, -0.42f, -0.35f, 0);
            case KICK -> guard(grip);
        };
    }

    private static FirstPersonPose release(AttackType attack, Grip grip, boolean mirrored, float p) {
        float t = smooth(p);
        return switch (attack) {
            case SLASH -> slash(grip, mirrored, t);
            case OVERHEAD -> {
                // The chop comes down slightly diagonally, from the upper right to the middle, so the blade stays in view.
                float theta = lerp(10, 145, t) * DEG;
                float sin = (float) Math.sin(theta);
                float cos = (float) Math.cos(theta);
                yield new FirstPersonPose(new Vector3f(lerp(0.32f, 0.12f, t), lerp(0.3f, -0.35f, t), lerp(-0.5f, -0.68f, t)),
                        new Vector3f(-0.45f * sin, cos, -0.85f * sin), new Vector3f(0.4f, 0.2f, 1));
            }
            case STAB -> aimed(lerp(0.45f, 0.25f, PoseMath.easeOut(p)), lerp(-0.42f, -0.28f, PoseMath.easeOut(p)),
                    lerp(-0.35f, -0.85f, PoseMath.easeOut(p)), 0);
            case KICK -> guard(grip);
        };
    }

    /**
     * The hand crosses the view and the blade leads: it points ahead and towards the side the swing goes to,
     * turning further to that side as the swing ends.
     */
    private static FirstPersonPose slash(Grip grip, boolean mirrored, float t) {
        float side = mirrored ? -1 : 1;
        float reach = grip == Grip.ONE_HANDED ? 0.7f : 0.55f;
        Vector3f hand = new Vector3f(side * lerp(reach, -reach, t), lerp(-0.12f, -0.36f, t), lerp(-0.5f, -0.62f, t) - 0.12f * (float) Math.sin(t * Math.PI));
        float psi = lerp(-25, -115, t) * DEG;
        Vector3f blade = new Vector3f(side * (float) Math.sin(psi), lerp(0.12f, -0.12f, t), -(float) Math.cos(psi));
        return new FirstPersonPose(hand, blade, new Vector3f(0, 1, 0.35f));
    }

    /** A weapon held at the given point and pointing at the middle of the view, a little above it by {@code lift}. */
    private static FirstPersonPose aimed(float x, float y, float z, float lift) {
        Vector3f hand = new Vector3f(x, y, z);
        Vector3f blade = new Vector3f(AIM).add(0, lift, 0).sub(hand);
        return new FirstPersonPose(hand, blade, new Vector3f(0.2f, 1, 0.1f));
    }
}
