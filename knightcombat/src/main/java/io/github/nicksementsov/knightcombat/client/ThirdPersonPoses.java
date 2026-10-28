package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import io.github.nicksementsov.knightcombat.combat.Phase;
import org.joml.Vector3f;

import static io.github.nicksementsov.knightcombat.client.PoseMath.backOut;
import static io.github.nicksementsov.knightcombat.client.PoseMath.dir;
import static io.github.nicksementsov.knightcombat.client.PoseMath.lerp;
import static io.github.nicksementsov.knightcombat.client.PoseMath.smooth;

/**
 * The poses of a fighter's model in every phase, as directions relative to the body (right, up, forward):
 * where the weapon arm points, where the blade points and which way its edge faces.
 * Slashes turn the blade around the body with the edge leading, overheads bring it over the head and down,
 * stabs push it forward with a lunge. Blocked attacks bounce back before the fighter returns to the guard.
 * Heavy attacks draw the weapon back further and tremble, finishers have their own wind-ups and swings,
 * throws draw the weapon over the shoulder and whip it forward, and a dodge steps the feet out.
 */
final class ThirdPersonPoses {
    private static final float DEG = (float) Math.PI / 180f;

    private ThirdPersonPoses() {
    }

    /** The pose to show now, blended from the pose shown when the phase started. */
    static ThirdPersonPose current(FighterAnim anim, Grip grip, float partialTick) {
        float time = ClientCombat.ticks() + partialTick;
        if (anim == null) {
            return breathe(guard(grip), time);
        }
        if (anim.tpVersion != anim.version) {
            anim.tpVersion = anim.version;
            anim.tpFrom = anim.tpShown != null ? anim.tpShown : guard(grip);
        }
        float p = anim.progress(partialTick);
        float elapsed = anim.elapsed(partialTick);
        ThirdPersonPose target = target(anim, grip, p, time);
        float blend = switch (anim.phase) {
            case WINDUP -> Math.max(2, anim.length * 0.6f);
            case RELEASE -> 1.5f;
            case BLOCK -> 2;
            case STAGGER -> 1;
            case IDLE -> 4;
            case KICK, CRY, THROW -> 3;
            case DODGE -> 1.5f;
            // Recoveries blend over their whole length, from wherever the attack ended or was stopped.
            case RECOVERY -> 0;
        };
        ThirdPersonPose shown = blend > 0 && elapsed < blend && anim.tpFrom != null
                ? ThirdPersonPose.lerp(anim.tpFrom, target, smooth(elapsed / blend))
                : target;
        if (anim.phase == Phase.IDLE) {
            // A weapon just taken in hand is drawn from the hip with a flourish.
            float draw = anim.drawProgress(partialTick);
            if (draw < 1) {
                shown = ThirdPersonPose.lerp(drawn(grip), shown, backOut(draw));
            }
        }
        anim.tpShown = shown;
        return anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK ? breathe(shown, time) : shown;
    }

    private static ThirdPersonPose target(FighterAnim anim, Grip grip, float p, float time) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        Combo combo = anim.combo;
        return switch (anim.phase) {
            case IDLE -> guard(grip);
            // Behind a raised shield the weapon stays ready to strike back.
            case BLOCK -> anim.has(CombatState.SHIELD) ? guard(grip) : block(grip);
            case WINDUP -> {
                if (anim.has(CombatState.HEAVY)) {
                    // The charge: drawn back further, the arms trembling with the strain.
                    ThirdPersonPose charged = ThirdPersonPose.lerp(windupEnd(anim.attack, grip, mirrored, combo, 0),
                            windupEnd(anim.attack, grip, mirrored, combo, 1), smooth(p));
                    yield tremble(charged, smooth(p), time);
                }
                if (combo == Combo.MILL) {
                    yield mill(grip, p);
                }
                // The wind-up carries a little past its end and settles there, like a heavy weapon brought back.
                yield ThirdPersonPose.lerp(guard(grip), windupEnd(anim.attack, grip, mirrored, combo, 0), backOut(p));
            }
            case RELEASE -> release(anim.attack, grip, mirrored, combo, p);
            case RECOVERY -> {
                if (anim.attack == AttackType.KICK && anim.has(CombatState.SHIELD_BASH)) {
                    yield ThirdPersonPose.lerp(shieldBash(grip, 1), guard(grip), smooth(p));
                }
                if (anim.attack == AttackType.KICK) {
                    yield guard(grip).leg(-1.35f * (1 - smooth(p * 1.3f)), 1 - smooth(p));
                }
                ThirdPersonPose from = anim.tpFrom != null ? anim.tpFrom : guard(grip);
                ThirdPersonPose back = ThirdPersonPose.lerp(from, guard(grip), PoseMath.backOut(p));
                if (!anim.has(CombatState.REBOUND)) {
                    yield back;
                }
                // The weapon bounces off the block or the wall: it jumps back the way it came, then returns to the guard.
                ThirdPersonPose recoil = ThirdPersonPose.lerp(from, windupEnd(anim.attack, grip, mirrored, Combo.NONE, 0), 0.45f);
                recoil.head = 0.12f;
                float jolt = p < 0.15f ? smooth(p / 0.15f) : 1 - smooth((p - 0.15f) / 0.85f);
                yield ThirdPersonPose.lerp(back, recoil, jolt);
            }
            case STAGGER -> {
                ThirdPersonPose hit = anim.has(CombatState.GUARD_BROKEN) ? guardBroken()
                        : anim.has(CombatState.FLINCHED) ? ThirdPersonPose.lerp(guard(grip), staggered(), 0.6f)
                        : staggered();
                yield p < 0.55f ? hit : ThirdPersonPose.lerp(hit, guard(grip), smooth((p - 0.55f) / 0.45f));
            }
            case KICK -> anim.has(CombatState.SHIELD_BASH) ? shieldBash(grip, p) : kick(grip, p);
            case CRY -> {
                ThirdPersonPose cry = battleCry(p);
                yield p < 0.75f ? cry : ThirdPersonPose.lerp(cry, guard(grip), smooth((p - 0.75f) / 0.25f));
            }
            case THROW -> throwing(grip, p);
            case DODGE -> dodge(grip, anim.flags, p);
        };
    }

    /**
     * How far a shield is thrust out in a bash, for the arm that holds it: pulled in a little at first, then out
     * (1 at full reach), and back in during the recovery. 0 when the fighter is not bashing.
     */
    static float bash(FighterAnim anim, float partialTick) {
        if (anim == null || anim.attack != AttackType.KICK || !anim.has(CombatState.SHIELD_BASH)) {
            return 0;
        }
        float p = anim.progress(partialTick);
        return switch (anim.phase) {
            case KICK -> thrust(p);
            case RECOVERY -> 1 - smooth(p);
            default -> 0;
        };
    }

    private static float thrust(float p) {
        return p < 0.4f ? -0.25f * smooth(p / 0.4f) : lerp(-0.25f, 1, PoseMath.easeOut((p - 0.4f) / 0.6f));
    }

    /** A bash: the shield shoulder drives forward with a step, the weapon held back. */
    private static ThirdPersonPose shieldBash(Grip grip, float p) {
        float t = thrust(p);
        ThirdPersonPose pose = guard(grip);
        pose.bodyYaw += 0.45f * t;
        return pose.lean(0.15f * Math.max(0, t)).step(0.4f * t);
    }

    /** The arms shake a little with the strain of a heavy wind-up, more as it grows. */
    private static ThirdPersonPose tremble(ThirdPersonPose pose, float strength, float time) {
        float a = 0.045f * strength * (float) Math.sin(time * 2.9f);
        float b = 0.045f * strength * (float) Math.sin(time * 3.7f + 1);
        ThirdPersonPose p = pose.copy();
        p.arm.add(a, b, 0).normalize();
        p.blade.add(b, a, 0).normalize();
        return p;
    }

    /** The weapon held low at the side, point down, as it comes out of the scabbard. */
    private static ThirdPersonPose drawn(Grip grip) {
        return pose(dir(0.35f, -0.95f, -0.05f), dir(0.1f, -0.9f, 0.4f), dir(0, 0.4f, 0.9f), dir(-0.3f, -0.9f, 0.3f), 0.2f)
                .head(0.1f);
    }

    /**
     * The wind-up of a mill: the blade swings down past the leg, behind the back and up over the shoulder in one
     * circle at the side of the body, ending raised for the chop.
     */
    private static ThirdPersonPose mill(Grip grip, float p) {
        float t = smooth(p);
        float theta = lerp(50, 170 - 360, t);
        float alpha = lerp(-35, 110, PoseMath.easeIn(t)) + 30 * (float) Math.sin(Math.PI * t);
        float out = 0.55f * (float) Math.sin(Math.PI * t);
        return overhead(grip, theta, alpha, lerp(0.1f, 0.35f, t), 0, 0.18f + out).lean(-0.1f * t).head(-0.1f * t).step(-0.15f * t);
    }

    /** Drawing the weapon over the shoulder (most of the throw), then whipping it forward until it leaves the hand. */
    private static ThirdPersonPose throwing(Grip grip, float p) {
        ThirdPersonPose back = pose(dir(0.35f, 0.75f, -0.45f), dir(0.15f, 0.35f, -1), dir(0, 1, 0.3f), dir(-0.25f, 0.35f, 1), 0.7f)
                .lean(-0.14f).head(-0.05f).step(-0.2f);
        if (p < 0.72f) {
            return ThirdPersonPose.lerp(guard(grip), back, backOut(p / 0.72f));
        }
        ThirdPersonPose out = pose(dir(0.05f, 0.25f, 1), dir(0, 0.15f, 1), dir(0, -1, 0.15f), dir(-0.35f, -0.6f, 0.4f), -0.45f)
                .lean(0.22f).lunge(3).step(0.45f);
        return ThirdPersonPose.lerp(back, out, smooth((p - 0.72f) / 0.28f));
    }

    /** A quick step: the feet go out that way and the weapon is pulled in; the whole body leans with it (see the animator). */
    private static ThirdPersonPose dodge(Grip grip, int flags, float p) {
        float k = (float) Math.sin(Math.PI * Math.min(1, p * 1.15f));
        ThirdPersonPose pose = guard(grip);
        if ((flags & CombatState.DODGE_FORWARD) != 0) {
            return pose.lean(0.18f * k).step(0.45f * k);
        }
        if ((flags & (CombatState.DODGE_LEFT | CombatState.DODGE_RIGHT)) != 0) {
            float side = (flags & CombatState.DODGE_LEFT) != 0 ? -1 : 1;
            pose.bodyYaw += side * 0.25f * k;
            return pose.step(0.2f * k);
        }
        return pose.lean(-0.12f * k).head(-0.1f * k).step(-0.45f * k);
    }

    private static ThirdPersonPose pose(Vector3f arm, Vector3f blade, Vector3f edge, Vector3f off, float bodyYaw) {
        return new ThirdPersonPose(arm, blade, edge, off, bodyYaw);
    }

    /** Slow breathing while standing in guard: the weapon rises and sinks a little. */
    private static ThirdPersonPose breathe(ThirdPersonPose pose, float time) {
        float b = (float) Math.sin(time * 0.1f) * 0.035f;
        ThirdPersonPose p = pose.copy();
        p.arm.y -= b;
        p.arm.normalize();
        p.blade.y -= b * 0.6f;
        p.blade.normalize();
        p.off.y -= b;
        p.off.normalize();
        return p;
    }

    // ---------------------------------------------------------------- stances

    /** Ready to fight: the weapon in front of the body, the edge towards the enemy. */
    static ThirdPersonPose guard(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> pose(dir(0.15f, -0.55f, 0.82f), dir(-0.15f, 0.8f, 0.58f), dir(0.1f, -0.58f, 0.8f),
                    dir(-0.3f, -0.9f, 0.3f), 0.1f);
            case TWO_HANDED -> pose(dir(-0.1f, -0.5f, 0.86f), dir(-0.2f, 0.78f, 0.6f), dir(0.05f, -0.6f, 0.78f),
                    dir(-0.3f, -0.9f, 0.3f), 0.15f);
            case POLEARM -> pose(dir(0.3f, -0.85f, 0.35f), dir(-0.2f, 0.5f, 0.95f), dir(0, -0.88f, 0.46f),
                    dir(-0.3f, -0.9f, 0.3f), 0.35f);
        };
    }

    /** The weapon held across the body, edge up, to catch the blow. */
    private static ThirdPersonPose block(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> pose(dir(0.2f, 0.15f, 0.97f), dir(-0.97f, 0.25f, 0.05f), dir(0.25f, 0.97f, 0),
                    dir(-0.25f, -0.6f, 0.6f), 0.25f);
            case TWO_HANDED -> pose(dir(0.05f, 0.1f, 1), dir(-0.9f, 0.42f, 0.05f), dir(0.42f, 0.9f, 0),
                    dir(-0.25f, -0.6f, 0.6f), 0.2f);
            case POLEARM -> pose(dir(0.25f, -0.2f, 0.95f), dir(-1, 0.12f, 0.05f), dir(0.12f, 1, -0.3f),
                    dir(-0.25f, -0.6f, 0.6f), 0.15f);
        };
    }

    private static ThirdPersonPose staggered() {
        return pose(dir(0.55f, -0.3f, -0.55f), dir(0.3f, 0.8f, -0.3f), dir(0, 0.35f, 1),
                dir(-0.5f, -0.6f, -0.3f), 0.35f).head(-0.2f).lean(-0.12f).step(-0.3f);
    }

    private static ThirdPersonPose guardBroken() {
        return pose(dir(0.25f, -0.95f, 0.15f), dir(0.05f, -0.35f, 0.95f), dir(0, -1, -0.37f),
                dir(-0.3f, -1, 0.1f), 0.4f).head(0.3f).lean(0.15f).step(-0.35f);
    }

    /** The weapon raised high, the free arm thrown up, the head back; the arms shake with the shout. */
    private static ThirdPersonPose battleCry(float p) {
        float shake = (float) Math.sin(p * 60) * 0.04f * (1 - p);
        return pose(dir(0.3f + shake, 0.95f, 0.1f), dir(shake, 1, 0.15f), dir(0, -0.15f, 1),
                dir(-0.45f, 0.85f, 0.15f - shake), 0).head(-0.5f).lean(-0.12f).step(0.15f);
    }

    private static ThirdPersonPose kick(Grip grip, float p) {
        float leg = p < 0.6f ? 0.35f * smooth(p / 0.6f) : lerp(0.35f, -1.35f, smooth((p - 0.6f) / 0.4f));
        ThirdPersonPose pose = guard(grip);
        pose.bodyYaw -= 0.25f * smooth(p);
        return pose.leg(leg, 1).lean(-0.12f * smooth(p));
    }

    // ---------------------------------------------------------------- attacks

    /**
     * The end of the wind-up: the weapon cocked back, the body turned to put weight into the blow. {@code charge}
     * (0 to 1) draws it back further, for heavy attacks; finishers are drawn back a little further too.
     */
    private static ThirdPersonPose windupEnd(AttackType attack, Grip grip, boolean mirrored, Combo combo, float charge) {
        float deep = 1.4f * charge + (combo != Combo.NONE && combo != Combo.KICK_FOLLOW ? 0.4f : 0);
        return switch (attack) {
            case SLASH -> {
                if (combo == Combo.HAMSTRING) {
                    // Low: the blade hangs back by the knee, the body crouched over it.
                    yield slash(grip, 125 + 20 * deep, -0.45f, -0.05f, 0.6f + 0.2f * deep, 0, false).lean(0.12f).step(0.1f).head(0.1f);
                }
                yield (mirrored
                        ? slash(grip, -130 - 25 * deep, 0.7f, 0.6f + 0.1f * deep, -0.55f - 0.3f * deep, 0, true)
                        : slash(grip, 125 + 25 * deep, 0.75f, 0.7f + 0.1f * deep, 0.55f + 0.3f * deep, 0, false))
                        .step(-0.05f - 0.12f * deep).lean(-0.05f * deep);
            }
            case OVERHEAD -> overhead(grip, 170 + 18 * deep, 110 + 18 * deep, 0.25f + 0.12f * deep, 0)
                    .lean(-0.1f - 0.08f * deep).head(-0.1f - 0.1f * deep).step(-0.15f - 0.12f * deep);
            case STAB -> stab(grip, -0.35f * deep);
            case KICK -> guard(grip);
        };
    }

    /** The pose {@code p} of the way through the release of an attack. */
    static ThirdPersonPose release(AttackType attack, Grip grip, boolean mirrored, float p) {
        return release(attack, grip, mirrored, Combo.NONE, p);
    }

    /** The pose {@code p} of the way through the release of an attack that may be a finisher. */
    static ThirdPersonPose release(AttackType attack, Grip grip, boolean mirrored, Combo combo, float p) {
        float t = smooth(p);
        // Weight goes forward through the middle of the swing.
        float push = (float) Math.sin(Math.PI * p);
        return switch (attack) {
            case SLASH -> {
                if (combo == Combo.HAMSTRING) {
                    // A low cut across the shins, the fighter dropping into it.
                    yield slash(grip, lerp(110, -95, t), lerp(-0.4f, -0.6f, t), lerp(-0.05f, -0.4f, t), lerp(0.55f, -0.6f, t), p, false)
                            .lean(0.12f + 0.15f * push).lunge(2 * push).step(0.15f + 0.3f * push).head(0.15f);
                }
                if (combo == Combo.WHIRLWIND) {
                    // A wide sweep, the arm out straight; the animator spins the whole body with it.
                    yield slash(grip, lerp(150, -140, t), lerp(0.2f, -0.1f, t), lerp(0.1f, -0.15f, t), lerp(0.7f, -0.7f, t), p, false)
                            .lean(0.12f * push).lunge(2 * push).step(0.25f * push);
                }
                yield (mirrored
                        ? slash(grip, lerp(-100, 80, t), lerp(0.25f, -0.2f, t), lerp(0.15f, -0.25f, t), lerp(-0.45f, 0.5f, t), p, true)
                        : slash(grip, lerp(95, -85, t), lerp(0.25f, -0.2f, t), lerp(0.15f, -0.25f, t), lerp(0.5f, -0.5f, t), p, false))
                        .lean(0.1f * push).lunge(1.5f * push).step(0.2f * push);
            }
            case OVERHEAD -> {
                if (combo == Combo.HAMMERFALL || combo == Combo.MILL) {
                    // Brought down from higher with the whole body behind it, finishing low.
                    yield overhead(grip, lerp(175, -60, t), lerp(120, -40, t), lerp(0.35f, -0.2f, t), p)
                            .lean(lerp(-0.18f, 0.34f, t)).head(lerp(-0.18f, 0.25f, t)).lunge(3 * push).step(lerp(-0.2f, 0.55f, t));
                }
                yield overhead(grip, lerp(150, -45, t), lerp(100, -30, t), lerp(0.25f, -0.15f, t), p)
                        .lean(lerp(-0.1f, 0.22f, t)).head(lerp(-0.1f, 0.15f, t)).lunge(2 * push).step(lerp(-0.15f, 0.4f, t));
            }
            case STAB -> {
                ThirdPersonPose stab = stab(grip, PoseMath.easeOut(p));
                if (combo == Combo.STING) {
                    // A deep lunge: the front foot far forward, the body low behind the point.
                    float out = PoseMath.easeOut(p);
                    stab.lunge(lerp(-2, 7, out)).step(lerp(-0.12f, 0.7f, out)).lean(lerp(-0.05f, 0.24f, out));
                }
                yield stab;
            }
            case KICK -> guard(grip);
        };
    }

    /**
     * A horizontal swing. {@code phi} is the direction of the blade around the body: 0 is forward, positive
     * angles to the right and back. The hand stays in front of the body and follows the blade, and the edge
     * faces the way the blade moves: right to left, or left to right when {@code mirrored}.
     */
    private static ThirdPersonPose slash(Grip grip, float phiDeg, float elevation, float armUp, float bodyYaw, float swing,
                                         boolean mirrored) {
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
        Vector3f edge = mirrored ? dir(cos, 0, -sin) : dir(-cos, 0, sin);
        Vector3f off = dir(lerp(-0.3f, -0.55f, swing), -0.5f, lerp(0.6f, -0.3f, swing));
        return pose(arm, blade, edge, off, bodyYaw);
    }

    /**
     * A vertical chop. {@code thetaDeg} is the direction of the blade in the plane in front of the body
     * (180 back, 90 up, 0 forward, negative down) and {@code alphaDeg} the direction of the arm in the same plane.
     * The edge faces down the arc.
     */
    private static ThirdPersonPose overhead(Grip grip, float thetaDeg, float alphaDeg, float bodyYaw, float swing) {
        return overhead(grip, thetaDeg, alphaDeg, bodyYaw, swing, grip == Grip.TWO_HANDED ? 0 : 0.18f);
    }

    /** A vertical chop with the arm held {@code side} out to the right of the body. */
    private static ThirdPersonPose overhead(Grip grip, float thetaDeg, float alphaDeg, float bodyYaw, float swing, float side) {
        float theta = thetaDeg * DEG;
        float alpha = alphaDeg * DEG;
        Vector3f arm = dir(side, (float) Math.sin(alpha), (float) Math.cos(alpha));
        Vector3f blade = dir(0.03f, (float) Math.sin(theta), (float) Math.cos(theta));
        Vector3f edge = dir(0, -(float) Math.cos(theta), (float) Math.sin(theta));
        Vector3f off = dir(-0.35f, lerp(0.3f, -0.6f, swing), lerp(0.6f, 0.3f, swing));
        return pose(arm, blade, edge, off, bodyYaw);
    }

    /** A thrust: {@code t} = 0 pulled back, 1 fully extended. The shoulder lunges forward with the body. */
    private static ThirdPersonPose stab(Grip grip, float t) {
        Vector3f arm = switch (grip) {
            case ONE_HANDED -> PoseMath.lerp(dir(0.45f, -0.6f, -0.45f), dir(0.1f, -0.15f, 0.98f), t).normalize();
            case TWO_HANDED -> PoseMath.lerp(dir(0.25f, -0.65f, -0.3f), dir(-0.05f, -0.2f, 0.98f), t).normalize();
            case POLEARM -> PoseMath.lerp(dir(0.4f, -0.85f, -0.3f), dir(0.15f, -0.45f, 0.88f), t).normalize();
        };
        boolean polearm = grip == Grip.POLEARM;
        Vector3f blade = polearm ? dir(-0.1f, 0.3f, 1) : dir(-0.05f, 0.06f, 1);
        Vector3f edge = polearm ? dir(0, -1, 0.3f) : dir(0, -1, 0.06f);
        Vector3f off = dir(-0.3f, -0.7f, lerp(0.5f, -0.2f, t));
        return pose(arm, blade, edge, off, lerp(0.45f, -0.3f, t)).lunge(lerp(-2f, 4.5f, t)).lean(lerp(-0.05f, 0.14f, t))
                .step(lerp(-0.12f, 0.45f, t));
    }
}
