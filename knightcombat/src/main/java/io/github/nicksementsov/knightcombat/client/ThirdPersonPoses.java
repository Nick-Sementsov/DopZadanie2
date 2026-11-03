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

    /**
     * The pose to show now, blended from the pose shown when the phase started. A {@code lance} is held upright
     * when it is not couched.
     */
    static ThirdPersonPose current(FighterAnim anim, Grip grip, boolean lance, float partialTick) {
        float time = ClientCombat.ticks() + partialTick;
        if (anim == null) {
            return breathe(lance ? lanceRaised() : guard(grip), time);
        }
        if (anim.tpVersion != anim.version) {
            anim.tpVersion = anim.version;
            anim.tpFrom = anim.tpShown != null ? anim.tpShown : guard(grip);
        }
        float p = anim.progress(partialTick);
        float elapsed = anim.elapsed(partialTick);
        ThirdPersonPose target = target(anim, grip, lance, p, time);
        float blend = switch (anim.phase) {
            case WINDUP -> Math.max(2, anim.length * 0.6f);
            case RELEASE -> 1.5f;
            case BLOCK -> 2;
            case STAGGER -> 1;
            case IDLE -> 4;
            case KICK, CRY, THROW -> 3;
            case DODGE -> 1.5f;
            // Lowering a lance takes a moment.
            case COUCH -> 5;
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
            if (!lance) {
                float run = anim.sprint(partialTick);
                if (run > 0) {
                    shown = ThirdPersonPose.lerp(shown, running(grip), run);
                }
                float inspect = anim.inspectWeight(partialTick);
                if (inspect > 0) {
                    shown = ThirdPersonPose.lerp(shown, inspect(grip, anim.inspectProgress(partialTick)), inspect);
                }
            }
        }
        if (anim.phase == Phase.BLOCK) {
            shown = guardReaction(shown, anim.has(CombatState.SHIELD), anim.guardJolt(partialTick), anim.parryPush(partialTick));
        }
        float air = anim.air(partialTick);
        if (air > 0 && (anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK)) {
            shown = airborne(shown, air);
        }
        anim.tpShown = shown;
        return anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK ? breathe(shown, time) : shown;
    }

    private static ThirdPersonPose target(FighterAnim anim, Grip grip, boolean lance, float p, float time) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        Combo combo = anim.combo;
        return switch (anim.phase) {
            case IDLE -> lance ? lanceRaised() : guard(grip);
            case COUCH -> couched();
            // Behind a raised shield the weapon stays ready to strike back.
            case BLOCK -> anim.has(CombatState.SHIELD) ? shieldGuard(grip) : block(grip);
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
                if (anim.has(CombatState.LANCE)) {
                    // The lance struck: its tip is thrown up and the shoulder back, then it comes down again.
                    float jolt = p < 0.2f ? smooth(p / 0.2f) : 1 - smooth((p - 0.2f) / 0.8f);
                    yield ThirdPersonPose.lerp(couched(), lanceThrownUp(), jolt);
                }
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

    /** A lance held upright at the side, the other hand on the reins. */
    static ThirdPersonPose lanceRaised() {
        return pose(dir(0.3f, -0.75f, 0.45f), dir(0.04f, 1, 0.15f), dir(0, 0, 1), dir(-0.25f, -0.7f, 0.5f), 0.1f);
    }

    /** A couched lance: tucked under the arm, pointing ahead and a little across, the rider leaning into the charge. */
    static ThirdPersonPose couched() {
        return pose(dir(0.12f, -0.7f, 0.62f), dir(-0.1f, -0.03f, 1), dir(0, 1, 0), dir(-0.15f, -0.55f, 0.8f), 0.2f)
                .lean(0.15f).head(0.05f);
    }

    private static ThirdPersonPose lanceThrownUp() {
        return pose(dir(0.2f, -0.55f, 0.6f), dir(-0.05f, 0.6f, 0.8f), dir(0, 0.8f, -0.6f), dir(-0.15f, -0.55f, 0.8f), 0.4f)
                .lean(-0.1f).head(-0.12f);
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

    /**
     * Behind a raised shield: the shield shoulder turned to the enemy, the body bent a little behind it, a one-handed
     * weapon held high at the side with its point forward over the shield, ready to thrust.
     */
    private static ThirdPersonPose shieldGuard(Grip grip) {
        if (grip != Grip.ONE_HANDED) {
            return guard(grip).lean(0.06f);
        }
        return pose(dir(0.42f, 0.22f, 0.8f), dir(-0.12f, 0.1f, 1), dir(0, -1, 0.1f), dir(-0.3f, -0.9f, 0.3f), 0.35f)
                .lean(0.08f).head(0.1f).step(0.08f);
    }

    /**
     * A blow on the guard drives it back against the body and rocks the fighter back (behind a shield the weapon only
     * shakes); a parry sweeps the blade out and up against the blow and turns the shoulders with it.
     */
    private static ThirdPersonPose guardReaction(ThirdPersonPose pose, boolean shield, float jolt, float parry) {
        if (jolt == 0 && parry == 0) {
            return pose;
        }
        ThirdPersonPose p = pose.copy();
        float k = shield ? 0.35f : 1;
        p.arm.add(0, -0.3f * jolt * k, -0.7f * jolt * k).normalize();
        p.blade.add(0, 0.3f * jolt * k, -0.4f * jolt * k).normalize();
        p.lean -= 0.1f * jolt;
        p.head -= 0.1f * jolt;
        p.step -= 0.18f * jolt;
        if (!shield) {
            p.arm.add(0.3f * parry, 0.15f * parry, 0).normalize();
            p.blade.add(0.9f * parry, 0.7f * parry, 0.3f * parry).normalize();
            p.bodyYaw -= 0.3f * parry;
        }
        return p;
    }

    /** Running: the weapon carried low at the side, point forward (two-handed ones across the body), leaning into the run. */
    private static ThirdPersonPose running(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> pose(dir(0.22f, -0.95f, 0.1f), dir(0.05f, -0.45f, 0.9f), dir(0, -0.9f, -0.45f),
                    dir(-0.2f, -0.9f, -0.3f), 0.05f).lean(0.12f);
            case TWO_HANDED -> pose(dir(0.05f, -0.75f, 0.65f), dir(-0.55f, 0.8f, 0.25f), dir(0.3f, 0.45f, -0.85f),
                    dir(-0.3f, -0.9f, 0.3f), 0.15f).lean(0.1f);
            case POLEARM -> pose(dir(0.35f, -0.92f, 0.15f), dir(-0.05f, 0.25f, 1), dir(0, -1, 0.25f),
                    dir(-0.3f, -0.9f, 0.3f), 0.1f).lean(0.12f);
        };
    }

    /** Off the ground: the weapon arm lifts a little and the free arm goes out for balance. */
    private static ThirdPersonPose airborne(ThirdPersonPose pose, float air) {
        ThirdPersonPose p = pose.copy();
        p.arm.add(0, 0.18f * air, 0).normalize();
        p.off.set(PoseMath.lerp(p.off, dir(-0.75f, 0.15f, 0.25f), 0.6f * air)).normalize();
        p.head -= 0.06f * air;
        return p;
    }

    /**
     * Looking the weapon over, {@code t} of the way through: a one-handed weapon is raised before the face, turned
     * from the flat to the edge and twirled at the side; a two-handed one is rested on the shoulder; a polearm is
     * grounded upright and leant on.
     */
    static ThirdPersonPose inspect(Grip grip, float t) {
        ThirdPersonPose guard = guard(grip);
        return switch (grip) {
            case ONE_HANDED -> {
                ThirdPersonPose flat = pose(dir(0.12f, 0.05f, 1), dir(-0.2f, 1, 0.15f), dir(1, 0, 0), dir(-0.3f, -0.9f, 0.3f), 0.05f)
                        .head(0.12f);
                ThirdPersonPose edge = pose(dir(0.12f, 0.05f, 1), dir(-0.2f, 1, 0.15f), dir(0, 0.15f, -1), dir(-0.3f, -0.9f, 0.3f), 0.05f)
                        .head(0.12f);
                if (t < 0.2f) {
                    yield ThirdPersonPose.lerp(guard, flat, backOut(t / 0.2f));
                }
                if (t < 0.38f) {
                    yield flat;
                }
                if (t < 0.52f) {
                    yield ThirdPersonPose.lerp(flat, edge, smooth((t - 0.38f) / 0.14f));
                }
                if (t < 0.62f) {
                    yield edge;
                }
                ThirdPersonPose twirlStart = twirl(0);
                if (t < 0.7f) {
                    yield ThirdPersonPose.lerp(edge, twirlStart, smooth((t - 0.62f) / 0.08f));
                }
                if (t < 0.88f) {
                    yield twirl(smooth((t - 0.7f) / 0.18f));
                }
                yield ThirdPersonPose.lerp(twirlStart, guard, smooth((t - 0.88f) / 0.12f));
            }
            case TWO_HANDED -> {
                float shrug = t > 0.4f && t < 0.6f ? (float) Math.sin((t - 0.4f) / 0.2f * Math.PI) : 0;
                ThirdPersonPose shoulder = pose(dir(0.5f, 0.05f + 0.08f * shrug, 0.3f), dir(0.2f, 0.4f, -0.9f), dir(0, 1, 0.35f),
                        dir(-0.3f, -0.9f, 0.2f), 0.1f).head(-0.05f * shrug);
                yield t < 0.22f ? ThirdPersonPose.lerp(guard, shoulder, backOut(t / 0.22f))
                        : t < 0.78f ? shoulder : ThirdPersonPose.lerp(shoulder, guard, smooth((t - 0.78f) / 0.22f));
            }
            case POLEARM -> {
                ThirdPersonPose planted = pose(dir(0.5f, -0.8f, 0.35f), dir(0.05f, 1, 0.08f), dir(0, 0, 1), dir(-0.3f, -0.9f, 0.2f), 0.05f)
                        .lean(-0.04f).step(-0.1f);
                yield t < 0.22f ? ThirdPersonPose.lerp(guard, planted, smooth(t / 0.22f))
                        : t < 0.8f ? planted : ThirdPersonPose.lerp(planted, guard, smooth((t - 0.8f) / 0.2f));
            }
        };
    }

    /** A twirl of the blade at the side, {@code u} of one turn: it spins forward, down, back and up again. */
    private static ThirdPersonPose twirl(float u) {
        float theta = (90 - 360 * u) * DEG;
        float sin = (float) Math.sin(theta);
        float cos = (float) Math.cos(theta);
        return pose(dir(0.38f, -0.85f, 0.3f), dir(0.06f, sin, cos), dir(0, -cos, sin), dir(-0.3f, -0.9f, 0.3f), 0.1f);
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
