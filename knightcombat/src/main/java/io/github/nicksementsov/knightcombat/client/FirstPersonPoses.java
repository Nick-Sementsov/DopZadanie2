package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import io.github.nicksementsov.knightcombat.combat.Phase;
import org.joml.Vector3f;

import static io.github.nicksementsov.knightcombat.client.PoseMath.lerp;
import static io.github.nicksementsov.knightcombat.client.PoseMath.smooth;

/**
 * Where the weapon is in the first person view in every phase. Coordinates: x right, y up, z towards the camera.
 * The hands stay about an arm's length from the eyes, so the arms come into the view from its lower corners.
 * The flat of the blade is turned towards the camera, so the weapon is seen and not just its edge; the edge
 * (the side the head of an axe sticks out to) faces the way the blow goes.
 */
final class FirstPersonPoses {
    private static final float DEG = (float) Math.PI / 180f;
    /** Point ahead of the camera that thrusts aim at. */
    private static final Vector3f AIM = new Vector3f(0, 0, -3);
    /** The flat of the blade faces the camera, tilted up a little. */
    private static final Vector3f TO_CAMERA = new Vector3f(0.3f, 0.15f, 1);

    private FirstPersonPoses() {
    }

    static FirstPersonPose current(FighterAnim anim, Grip grip, float partialTick) {
        float time = ClientCombat.ticks() + partialTick;
        if (anim == null) {
            return breathe(guard(grip), time);
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
            case BLOCK -> 2;
            case STAGGER -> 1;
            case IDLE -> 4;
            case KICK, CRY -> 3;
            case RECOVERY -> 0;
        };
        FirstPersonPose shown = blend > 0 && elapsed < blend && anim.fpFrom != null
                ? FirstPersonPose.lerp(anim.fpFrom, target, smooth(elapsed / blend))
                : target;
        anim.fpShown = shown;
        return anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK ? breathe(shown, time) : shown;
    }

    private static FirstPersonPose target(FighterAnim anim, Grip grip, float p) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        return switch (anim.phase) {
            case IDLE -> guard(grip);
            case BLOCK -> block(grip);
            case WINDUP -> windup(anim.attack, grip, mirrored, p);
            case RELEASE -> release(anim.attack, grip, mirrored, p);
            case RECOVERY -> {
                FirstPersonPose from = anim.fpFrom != null ? anim.fpFrom : guard(grip);
                FirstPersonPose back = FirstPersonPose.lerp(from, guard(grip), PoseMath.backOut(p));
                if (!anim.has(CombatState.REBOUND) || anim.attack == AttackType.KICK) {
                    yield back;
                }
                // Bounced off a block or a wall: the weapon jumps back the way it came before returning to the guard.
                FirstPersonPose recoil = FirstPersonPose.lerp(from, windupEnd(anim.attack, grip, mirrored), 0.4f);
                float jolt = p < 0.15f ? smooth(p / 0.15f) : 1 - smooth((p - 0.15f) / 0.85f);
                yield FirstPersonPose.lerp(back, recoil, jolt);
            }
            case STAGGER -> {
                FirstPersonPose hit = anim.has(CombatState.GUARD_BROKEN)
                        ? facing(0.45f, -0.62f, -0.8f, -0.1f, -0.5f, -1f, new Vector3f(0.3f, 1, 0.2f), new Vector3f(-1, 0, 0))
                        : facing(0.6f, -0.4f, -0.7f, 0.35f, 0.75f, -0.3f, TO_CAMERA, new Vector3f(-1, 0, 0));
                if (anim.has(CombatState.FLINCHED)) {
                    hit = FirstPersonPose.lerp(guard(grip), hit, 0.6f);
                }
                yield p < 0.55f ? hit : FirstPersonPose.lerp(hit, guard(grip), smooth((p - 0.55f) / 0.45f));
            }
            case KICK -> {
                // The weapon is pulled aside and up while the leg goes out.
                float t = p < 0.5f ? smooth(p * 2) : 1;
                yield FirstPersonPose.lerp(guard(grip),
                        facing(0.55f, -0.42f, -0.72f, -0.25f, 0.75f, -0.5f, TO_CAMERA, new Vector3f(-1, 0, 0)), t);
            }
            case CRY -> {
                // Raised high and shaken with the shout.
                float shake = (float) Math.sin(p * 60) * 0.02f * (1 - p);
                FirstPersonPose cry = facing(0.36f + shake, -0.02f + shake, -0.85f, -0.1f, 1, -0.3f, TO_CAMERA, new Vector3f(-1, 0, 0));
                yield p < 0.75f ? cry : FirstPersonPose.lerp(cry, guard(grip), smooth((p - 0.75f) / 0.25f));
            }
        };
    }

    /** The weapon rises and sinks a little with the breath. */
    private static FirstPersonPose breathe(FirstPersonPose pose, float time) {
        float b = (float) Math.sin(time * 0.1f);
        Vector3f hand = new Vector3f(pose.hand).add(0, b * 0.008f, 0);
        Vector3f blade = new Vector3f(pose.blade).add(b * 0.012f, 0, 0);
        return new FirstPersonPose(hand, blade, pose.edge);
    }

    static FirstPersonPose guard(Grip grip) {
        Vector3f lead = new Vector3f(-1, 0, -0.3f);
        return switch (grip) {
            // Upright, right of the crosshair, so the enemy in front stays in sight.
            case ONE_HANDED -> facing(0.42f, -0.3f, -0.8f, -0.12f, 0.9f, -0.4f, TO_CAMERA, lead);
            case TWO_HANDED -> facing(0.36f, -0.26f, -0.82f, -0.14f, 0.85f, -0.5f, TO_CAMERA, lead);
            case POLEARM -> aimed(0.36f, -0.38f, -0.72f, 1.0f);
        };
    }

    private static FirstPersonPose block(Grip grip) {
        // Raised across the upper half of the view, edge up, so the enemy stays visible below the blade.
        float y = grip == Grip.POLEARM ? -0.02f : 0.06f;
        return facing(0.3f, y, -0.8f, -1f, 0.32f, -0.25f, new Vector3f(0, 0.25f, 1), new Vector3f(0, 1, 0));
    }

    /**
     * From the guard to the end of the wind-up along a curve: the hand rises on the way and the blade turns back,
     * carrying a little past the end and settling there.
     */
    private static FirstPersonPose windup(AttackType attack, Grip grip, boolean mirrored, float p) {
        FirstPersonPose pose = FirstPersonPose.lerp(guard(grip), windupEnd(attack, grip, mirrored), PoseMath.backOut(p));
        if (attack == AttackType.SLASH || attack == AttackType.OVERHEAD) {
            pose.hand.y += 0.06f * (float) Math.sin(Math.PI * smooth(p));
        }
        return pose;
    }

    private static FirstPersonPose windupEnd(AttackType attack, Grip grip, boolean mirrored) {
        return switch (attack) {
            // The blade is raised beside the head and cocked back, edge towards the side the swing goes to.
            case SLASH -> mirrored
                    ? facing(-0.48f, 0.0f, -0.8f, -0.35f, 0.9f, 0.25f, new Vector3f(0.4f, 0, 1), new Vector3f(1, 0, 0))
                    : facing(0.6f, 0.02f, -0.8f, 0.3f, 0.9f, 0.25f, new Vector3f(-0.4f, 0, 1), new Vector3f(-1, 0, 0));
            case OVERHEAD -> facing(0.3f, 0.2f, -0.85f, 0.12f, 0.85f, 0.5f, new Vector3f(1, 0, 0), new Vector3f(0, 0.8f, -0.6f));
            case STAB -> aimed(0.42f, -0.42f, -0.62f, 0);
            case KICK -> guard(grip);
        };
    }

    /** The pose {@code p} of the way through the release of an attack. */
    static FirstPersonPose release(AttackType attack, Grip grip, boolean mirrored, float p) {
        float t = smooth(p);
        return switch (attack) {
            case SLASH -> slash(grip, mirrored, t);
            case OVERHEAD -> {
                // The chop comes down slightly diagonally, from the upper right to the middle, so the blade stays in view.
                float theta = lerp(10, 148, t) * DEG;
                float sin = (float) Math.sin(theta);
                float cos = (float) Math.cos(theta);
                Vector3f hand = new Vector3f(lerp(0.3f, 0.1f, t), lerp(0.26f, -0.32f, t), lerp(-0.8f, -0.9f, t) - 0.1f * (float) Math.sin(Math.PI * t));
                Vector3f blade = new Vector3f(-0.45f * sin, cos, -0.85f * sin);
                Vector3f motion = new Vector3f(-0.45f * cos, -sin, -0.85f * cos);
                yield facing(hand, blade, new Vector3f(0.4f, 0.2f, 1), motion);
            }
            case STAB -> {
                // Out fast, held for a moment at full reach.
                float out = PoseMath.easeOut(Math.min(1, p * 1.3f));
                yield aimed(lerp(0.42f, 0.2f, out), lerp(-0.42f, -0.26f, out), lerp(-0.62f, -1.05f, out), 0);
            }
            case KICK -> guard(grip);
        };
    }

    /**
     * The hand crosses the view on a curve that bows away from the camera and the blade leads: it points ahead and
     * towards the side the swing goes to, turning further to that side as the swing ends. The swing falls a little
     * from the shoulder to the other hip, so it is not seen edge-on from the eyes.
     */
    private static FirstPersonPose slash(Grip grip, boolean mirrored, float t) {
        float side = mirrored ? -1 : 1;
        float reach = grip == Grip.ONE_HANDED ? 0.62f : 0.5f;
        float bow = (float) Math.sin(t * Math.PI);
        Vector3f hand = new Vector3f(side * lerp(reach, -reach, t), lerp(0.02f, -0.32f, t) + 0.04f * bow,
                lerp(-0.78f, -0.86f, t) - 0.16f * bow);
        float psi = lerp(-25, -120, t) * DEG;
        float sin = (float) Math.sin(psi);
        float cos = (float) Math.cos(psi);
        Vector3f blade = new Vector3f(side * sin, lerp(0.32f, -0.28f, t), -cos);
        Vector3f motion = new Vector3f(-side * cos, 0, -sin);
        return facing(hand, blade, new Vector3f(0, 1, 0.9f), motion);
    }

    /** A weapon held at the given point and pointing at the middle of the view, a little above it by {@code lift}. */
    private static FirstPersonPose aimed(float x, float y, float z, float lift) {
        Vector3f hand = new Vector3f(x, y, z);
        Vector3f blade = new Vector3f(AIM).add(0, lift, 0).sub(hand);
        return facing(hand, blade, new Vector3f(0.2f, 1, 0.1f), new Vector3f(-1, 0, 0));
    }

    private static FirstPersonPose facing(float hx, float hy, float hz, float bx, float by, float bz, Vector3f face, Vector3f lead) {
        return facing(new Vector3f(hx, hy, hz), new Vector3f(bx, by, bz), face, lead);
    }

    /**
     * A pose whose blade shows its flat towards {@code face}: the edge lies across the blade, square to that
     * direction, on the side of {@code lead} (the way the blow goes, where the head of an axe looks).
     */
    private static FirstPersonPose facing(Vector3f hand, Vector3f blade, Vector3f face, Vector3f lead) {
        Vector3f edge = new Vector3f(face).cross(blade);
        if (edge.lengthSquared() < 1.0E-6f) {
            edge.set(lead);
        } else if (edge.dot(lead) < 0) {
            edge.negate();
        }
        return new FirstPersonPose(hand, blade, edge);
    }
}
