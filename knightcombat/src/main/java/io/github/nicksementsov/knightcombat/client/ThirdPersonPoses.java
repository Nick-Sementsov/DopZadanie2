package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import org.joml.Vector3f;

import static io.github.nicksementsov.knightcombat.client.PoseMath.dir;
import static io.github.nicksementsov.knightcombat.client.PoseMath.lerp;
import static io.github.nicksementsov.knightcombat.client.PoseMath.smooth;

/**
 * The poses of a fighter's model in every phase, as directions relative to the body (right, up, forward):
 * where the weapon arm points, where the blade points and which way its flat side faces.
 * Slashes turn the blade around the body, overheads turn it over the head and down, stabs push it forward.
 */
final class ThirdPersonPoses {
    private static final float DEG = (float) Math.PI / 180f;

    private ThirdPersonPoses() {
    }

    /** The pose to show now, blended from the pose shown when the phase started. */
    static ThirdPersonPose current(FighterAnim anim, Grip grip, float partialTick) {
        if (anim == null) {
            return guard(grip);
        }
        if (anim.tpVersion != anim.version) {
            anim.tpVersion = anim.version;
            anim.tpFrom = anim.tpShown != null ? anim.tpShown : guard(grip);
        }
        float p = anim.progress(partialTick);
        float elapsed = anim.elapsed(partialTick);
        ThirdPersonPose target = target(anim, grip, p);
        float blend = switch (anim.phase) {
            case WINDUP -> Math.max(2, anim.length * 0.6f);
            case RELEASE -> 1.5f;
            case BLOCK, STAGGER -> 2;
            case IDLE -> 4;
            case KICK, CRY -> 3;
            // Recoveries blend over their whole length, from wherever the attack ended or was stopped.
            case RECOVERY -> 0;
        };
        ThirdPersonPose shown = blend > 0 && elapsed < blend && anim.tpFrom != null
                ? ThirdPersonPose.lerp(anim.tpFrom, target, smooth(elapsed / blend))
                : target;
        anim.tpShown = shown;
        return shown;
    }

    private static ThirdPersonPose target(FighterAnim anim, Grip grip, float p) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        return switch (anim.phase) {
            case IDLE -> guard(grip);
            case BLOCK -> block(grip);
            case WINDUP -> ThirdPersonPose.lerp(guard(grip), windupEnd(anim.attack, grip, mirrored), smooth(p));
            case RELEASE -> release(anim.attack, grip, mirrored, p);
            case RECOVERY -> {
                if (anim.attack == AttackType.KICK) {
                    yield guard(grip).leg(-1.35f * (1 - smooth(p * 1.3f)), 1 - smooth(p));
                }
                ThirdPersonPose from = anim.tpFrom != null ? anim.tpFrom : guard(grip);
                yield ThirdPersonPose.lerp(from, guard(grip), smooth(p));
            }
            case STAGGER -> {
                ThirdPersonPose hit = anim.has(CombatState.GUARD_BROKEN) ? guardBroken() : staggered();
                yield p < 0.6f ? hit : ThirdPersonPose.lerp(hit, guard(grip), smooth((p - 0.6f) / 0.4f));
            }
            case KICK -> kick(grip, p);
            case CRY -> {
                ThirdPersonPose cry = battleCry(grip);
                yield p < 0.7f ? cry : ThirdPersonPose.lerp(cry, guard(grip), smooth((p - 0.7f) / 0.3f));
            }
        };
    }

    private static ThirdPersonPose pose(Vector3f arm, Vector3f blade, Vector3f flat, Vector3f off, float bodyYaw) {
        return new ThirdPersonPose(arm, blade, flat, off, bodyYaw);
    }

    // ---------------------------------------------------------------- stances

    static ThirdPersonPose guard(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> pose(dir(0.15f, -0.55f, 0.82f), dir(-0.15f, 0.8f, 0.58f), dir(0.8f, 0, 0.6f),
                    dir(-0.3f, -0.9f, 0.3f), 0.1f);
            case TWO_HANDED -> pose(dir(-0.1f, -0.5f, 0.86f), dir(-0.2f, 0.78f, 0.6f), dir(0.8f, 0, 0.6f),
                    dir(-0.3f, -0.9f, 0.3f), 0.15f);
            case POLEARM -> pose(dir(0.3f, -0.85f, 0.35f), dir(-0.2f, 0.5f, 0.95f), dir(0.3f, 1, 0),
                    dir(-0.3f, -0.9f, 0.3f), 0.35f);
        };
    }

    private static ThirdPersonPose block(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> pose(dir(0.2f, 0.15f, 0.97f), dir(-0.97f, 0.25f, 0.05f), dir(0, 0, 1),
                    dir(-0.25f, -0.6f, 0.6f), 0.25f);
            case TWO_HANDED -> pose(dir(0.05f, 0.1f, 1), dir(-0.9f, 0.42f, 0.05f), dir(0, 0, 1),
                    dir(-0.25f, -0.6f, 0.6f), 0.2f);
            case POLEARM -> pose(dir(0.25f, -0.2f, 0.95f), dir(-1, 0.12f, 0.05f), dir(0, 0.3f, 1),
                    dir(-0.25f, -0.6f, 0.6f), 0.15f);
        };
    }

    private static ThirdPersonPose staggered() {
        return pose(dir(0.55f, -0.3f, -0.55f), dir(0.3f, 0.8f, -0.3f), dir(1, 0, 0),
                dir(-0.5f, -0.6f, -0.3f), 0.35f).head(-0.15f);
    }

    private static ThirdPersonPose guardBroken() {
        return pose(dir(0.25f, -0.95f, 0.15f), dir(0.05f, -0.35f, 0.95f), dir(1, 0, 0),
                dir(-0.3f, -1, 0.1f), 0.4f).head(0.3f);
    }

    private static ThirdPersonPose battleCry(Grip grip) {
        return pose(dir(0.3f, 0.95f, 0.1f), dir(0, 1, 0.15f), dir(1, 0, 0),
                dir(-0.4f, 0.9f, 0.15f), 0).head(-0.45f);
    }

    private static ThirdPersonPose kick(Grip grip, float p) {
        float leg = p < 0.6f ? 0.35f * smooth(p / 0.6f) : lerp(0.35f, -1.35f, smooth((p - 0.6f) / 0.4f));
        ThirdPersonPose pose = guard(grip);
        pose.bodyYaw -= 0.25f * smooth(p);
        return pose.leg(leg, 1);
    }

    // ---------------------------------------------------------------- attacks

    private static ThirdPersonPose windupEnd(AttackType attack, Grip grip, boolean mirrored) {
        return switch (attack) {
            case SLASH -> mirrored
                    ? slash(grip, -130, 0.7f, 0.6f, -0.5f, 0)
                    : slash(grip, 125, 0.75f, 0.7f, 0.5f, 0);
            case OVERHEAD -> overhead(grip, 165, 105, 0.2f, 0);
            case STAB -> stab(grip, 0);
            case KICK -> guard(grip);
        };
    }

    private static ThirdPersonPose release(AttackType attack, Grip grip, boolean mirrored, float p) {
        float t = smooth(p);
        return switch (attack) {
            case SLASH -> mirrored
                    ? slash(grip, lerp(-100, 75, t), lerp(0.25f, -0.2f, t), lerp(0.15f, -0.25f, t), lerp(-0.4f, 0.45f, t), p)
                    : slash(grip, lerp(95, -80, t), lerp(0.25f, -0.2f, t), lerp(0.15f, -0.25f, t), lerp(0.45f, -0.45f, t), p);
            case OVERHEAD -> overhead(grip, lerp(150, -45, t), lerp(95, -30, t), lerp(0.2f, -0.15f, t), p);
            case STAB -> stab(grip, PoseMath.easeOut(p));
            case KICK -> guard(grip);
        };
    }

    /**
     * A horizontal swing. {@code phi} is the direction of the blade around the body: 0 is forward, positive
     * angles to the right and back. The hand stays in front of the body and follows the blade.
     */
    private static ThirdPersonPose slash(Grip grip, float phiDeg, float elevation, float armUp, float bodyYaw, float swing) {
        float phi = phiDeg * DEG;
        float sin = (float) Math.sin(phi);
        float cos = (float) Math.cos(phi);
        float side = switch (grip) {
            case ONE_HANDED -> 0.75f;
            case TWO_HANDED -> 0.55f;
            case POLEARM -> 0.7f;
        };
        float forward = grip == Grip.TWO_HANDED ? 0.9f : 0.6f;
        Vector3f arm = dir(side * sin, armUp, forward + 0.4f * cos);
        Vector3f blade = dir(sin, grip == Grip.POLEARM ? elevation + 0.2f : elevation, cos);
        // The flat side faces up and outwards, so the edge leads the swing.
        Vector3f flat = dir(0.6f * cos, 1, -0.6f * sin);
        Vector3f off = dir(lerp(-0.3f, -0.55f, swing), -0.5f, lerp(0.6f, -0.3f, swing));
        return pose(arm, blade, flat, off, bodyYaw);
    }

    /**
     * A vertical chop. {@code thetaDeg} is the direction of the blade in the plane in front of the body
     * (180 back, 90 up, 0 forward, negative down) and {@code alphaDeg} the direction of the arm in the same plane.
     */
    private static ThirdPersonPose overhead(Grip grip, float thetaDeg, float alphaDeg, float bodyYaw, float swing) {
        float theta = thetaDeg * DEG;
        float alpha = alphaDeg * DEG;
        float side = grip == Grip.TWO_HANDED ? 0 : 0.18f;
        Vector3f arm = dir(side, (float) Math.sin(alpha), (float) Math.cos(alpha));
        Vector3f blade = dir(0.03f, (float) Math.sin(theta), (float) Math.cos(theta));
        Vector3f off = dir(-0.35f, lerp(0.3f, -0.6f, swing), lerp(0.6f, 0.3f, swing));
        return pose(arm, blade, dir(1, 0, 0), off, bodyYaw);
    }

    /** A thrust: {@code t} = 0 pulled back, 1 fully extended. The shoulder lunges forward with the body. */
    private static ThirdPersonPose stab(Grip grip, float t) {
        Vector3f arm = switch (grip) {
            case ONE_HANDED -> PoseMath.lerp(dir(0.45f, -0.6f, -0.45f), dir(0.1f, -0.15f, 0.98f), t).normalize();
            case TWO_HANDED -> PoseMath.lerp(dir(0.25f, -0.65f, -0.3f), dir(-0.05f, -0.2f, 0.98f), t).normalize();
            case POLEARM -> PoseMath.lerp(dir(0.4f, -0.85f, -0.3f), dir(0.15f, -0.45f, 0.88f), t).normalize();
        };
        Vector3f blade = grip == Grip.POLEARM ? dir(-0.1f, 0.3f, 1) : dir(-0.05f, 0.06f, 1);
        Vector3f off = dir(-0.3f, -0.7f, lerp(0.5f, -0.2f, t));
        return pose(arm, blade, dir(1, 0.3f, 0), off, lerp(0.45f, -0.3f, t)).lunge(lerp(-2f, 4f, t));
    }
}
