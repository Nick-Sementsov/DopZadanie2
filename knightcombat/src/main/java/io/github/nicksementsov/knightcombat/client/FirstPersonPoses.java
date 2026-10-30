package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Grip;
import io.github.nicksementsov.knightcombat.combat.Phase;
import org.joml.Vector3f;

import static io.github.nicksementsov.knightcombat.client.PoseMath.backOut;
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

    static FirstPersonPose current(FighterAnim anim, Grip grip, boolean lance, float partialTick) {
        float time = ClientCombat.ticks() + partialTick;
        if (anim == null) {
            return breathe(lance ? lanceRaised() : guard(grip), time);
        }
        if (anim.fpVersion != anim.version) {
            anim.fpVersion = anim.version;
            anim.fpFrom = anim.fpShown != null ? anim.fpShown : guard(grip);
        }
        float p = anim.progress(partialTick);
        float elapsed = anim.elapsed(partialTick);
        FirstPersonPose target = target(anim, grip, lance, p);
        float blend = switch (anim.phase) {
            case WINDUP -> Math.max(2, anim.length * 0.6f);
            case RELEASE -> 1.5f;
            case BLOCK -> 2;
            case STAGGER -> 1;
            case IDLE -> 4;
            case KICK, CRY, THROW -> 3;
            case DODGE -> 1.5f;
            case COUCH -> 5;
            case RECOVERY -> 0;
        };
        FirstPersonPose shown = blend > 0 && elapsed < blend && anim.fpFrom != null
                ? FirstPersonPose.lerp(anim.fpFrom, target, smooth(elapsed / blend))
                : target;
        if (anim.phase == Phase.IDLE) {
            // A weapon just taken in hand comes up from the hip with a flourish.
            float draw = anim.drawProgress(partialTick);
            if (draw < 1) {
                shown = FirstPersonPose.lerp(drawn(), shown, backOut(draw));
            }
            if (!lance) {
                float run = anim.sprint(partialTick);
                if (run > 0) {
                    shown = FirstPersonPose.lerp(shown, running(grip), run);
                }
                float inspect = anim.inspectWeight(partialTick);
                if (inspect > 0) {
                    shown = FirstPersonPose.lerp(shown, inspect(grip, anim.inspectProgress(partialTick)), inspect);
                }
            }
        }
        if (anim.phase == Phase.BLOCK) {
            shown = guardReaction(shown, anim.has(CombatState.SHIELD), anim.guardJolt(partialTick), anim.parryPush(partialTick));
        }
        float air = anim.air(partialTick);
        if (air > 0 && (anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK)) {
            // Off the ground the weapon lifts a little and tips back.
            shown = new FirstPersonPose(new Vector3f(shown.hand).add(0, 0.04f * air, 0.02f * air),
                    new Vector3f(shown.blade).add(0, 0, 0.12f * air), shown.edge);
        }
        anim.fpShown = shown;
        return anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK ? breathe(shown, time) : shown;
    }

    private static FirstPersonPose target(FighterAnim anim, Grip grip, boolean lance, float p) {
        boolean mirrored = anim.has(CombatState.MIRRORED);
        Combo combo = anim.combo;
        return switch (anim.phase) {
            case IDLE -> lance ? lanceRaised() : guard(grip);
            case COUCH -> couched();
            case BLOCK -> anim.has(CombatState.SHIELD) ? shieldGuard(grip) : block(grip);
            case WINDUP -> {
                if (anim.has(CombatState.HEAVY)) {
                    // The charge: drawn back further (the weight of the weapon makes it tremble, see the animator).
                    yield FirstPersonPose.lerp(windupEnd(anim.attack, grip, mirrored, combo, 0), windupEnd(anim.attack, grip, mirrored, combo, 1),
                            smooth(p));
                }
                yield combo == Combo.MILL ? mill(grip, p) : windup(anim.attack, grip, mirrored, combo, p);
            }
            case RELEASE -> release(anim.attack, grip, mirrored, combo, p);
            case RECOVERY -> {
                if (anim.has(CombatState.LANCE)) {
                    float jolt = p < 0.2f ? smooth(p / 0.2f) : 1 - smooth((p - 0.2f) / 0.8f);
                    yield FirstPersonPose.lerp(couched(), lanceThrownUp(), jolt);
                }
                FirstPersonPose from = anim.fpFrom != null ? anim.fpFrom : guard(grip);
                FirstPersonPose back = FirstPersonPose.lerp(from, guard(grip), PoseMath.backOut(p));
                if (!anim.has(CombatState.REBOUND) || anim.attack == AttackType.KICK) {
                    yield back;
                }
                // Bounced off a block or a wall: the weapon jumps back the way it came before returning to the guard.
                FirstPersonPose recoil = FirstPersonPose.lerp(from, windupEnd(anim.attack, grip, mirrored, Combo.NONE, 0), 0.4f);
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
            case THROW -> throwing(grip, p);
            case DODGE -> dodge(grip, anim.flags, p);
        };
    }

    /** A lance held upright at the right edge of the view, leaning a little forward. */
    private static FirstPersonPose lanceRaised() {
        return facing(0.78f, -0.62f, -1.0f, 0.12f, 1, -0.1f, TO_CAMERA, new Vector3f(0, 0, -1));
    }

    /** A couched lance: the hand low at the right, the lance running ahead into the middle of the view. */
    private static FirstPersonPose couched() {
        Vector3f hand = new Vector3f(0.34f, -0.42f, -0.5f);
        Vector3f blade = new Vector3f(-0.1f, -0.3f, -6).sub(hand);
        return facing(hand, blade, new Vector3f(0, 1, 0.1f), new Vector3f(0, 1, 0));
    }

    /** The lance just struck: its tip thrown up. */
    private static FirstPersonPose lanceThrownUp() {
        Vector3f hand = new Vector3f(0.38f, -0.34f, -0.48f);
        Vector3f blade = new Vector3f(0, 2, -5).sub(hand);
        return facing(hand, blade, new Vector3f(0, 1, 0.1f), new Vector3f(0, 1, 0));
    }

    /** Low at the side, point down: the weapon coming out of the scabbard. */
    private static FirstPersonPose drawn() {
        return facing(0.42f, -0.62f, -0.6f, 0.1f, -0.75f, -0.6f, TO_CAMERA, new Vector3f(0, 1, 0));
    }

    /** The wind-up of a mill: the blade circles once down and back at the right side, then is raised for the chop. */
    private static FirstPersonPose mill(Grip grip, float p) {
        float t = smooth(p);
        float theta = lerp(60, -240, t) * DEG;
        float sin = (float) Math.sin(theta);
        float cos = (float) Math.cos(theta);
        float bow = (float) Math.sin(Math.PI * t);
        Vector3f hand = new Vector3f(lerp(0.42f, 0.32f, t) + 0.12f * bow, lerp(-0.3f, 0.18f, t) + 0.08f * sin, lerp(-0.8f, -0.85f, t) - 0.06f * cos);
        Vector3f blade = new Vector3f(0.12f, sin, -cos);
        Vector3f motion = new Vector3f(0, -cos, -sin);
        FirstPersonPose pose = facing(hand, blade, new Vector3f(1, 0, 0), motion);
        return p < 0.8f ? pose : FirstPersonPose.lerp(pose, windupEnd(AttackType.OVERHEAD, grip, false, Combo.MILL, 0), smooth((p - 0.8f) / 0.2f));
    }

    /** Raised beside the head and drawn back over the shoulder (most of the throw), then whipped out ahead. */
    private static FirstPersonPose throwing(Grip grip, float p) {
        FirstPersonPose back = facing(0.55f, 0.1f, -0.52f, 0.25f, 0.75f, 0.6f, new Vector3f(1, 0, 0.2f), new Vector3f(0, 1, 0));
        if (p < 0.72f) {
            return FirstPersonPose.lerp(guard(grip), back, backOut(p / 0.72f));
        }
        FirstPersonPose out = facing(0.14f, -0.04f, -1.0f, 0.02f, 0.15f, -1, new Vector3f(0, 1, 0.1f), new Vector3f(0, -1, 0));
        return FirstPersonPose.lerp(back, out, smooth((p - 0.72f) / 0.28f));
    }

    /** A quick step: the weapon swings the other way and dips, then comes back. */
    private static FirstPersonPose dodge(Grip grip, int flags, float p) {
        float k = (float) Math.sin(Math.PI * Math.min(1, p * 1.15f));
        FirstPersonPose pose = guard(grip);
        Vector3f hand = new Vector3f(pose.hand);
        Vector3f blade = new Vector3f(pose.blade);
        if ((flags & (CombatState.DODGE_LEFT | CombatState.DODGE_RIGHT)) != 0) {
            float side = (flags & CombatState.DODGE_LEFT) != 0 ? -1 : 1;
            hand.add(-side * 0.1f * k, -0.05f * k, 0);
            blade.add(-side * 0.3f * k, 0, 0);
        } else if ((flags & CombatState.DODGE_FORWARD) != 0) {
            hand.add(0, -0.03f * k, -0.08f * k);
            blade.add(0, -0.15f * k, -0.2f * k);
        } else {
            hand.add(0, 0.04f * k, 0.08f * k);
            blade.add(0, 0, 0.25f * k);
        }
        return new FirstPersonPose(hand, blade, pose.edge);
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

    /** Behind a raised shield a one-handed weapon is held high at the right, point forward over the shield. */
    private static FirstPersonPose shieldGuard(Grip grip) {
        return grip == Grip.ONE_HANDED
                ? facing(0.5f, -0.14f, -0.8f, -0.1f, 0.28f, -1, new Vector3f(0.25f, 1, 0.35f), new Vector3f(0, -1, 0))
                : guard(grip);
    }

    /**
     * A blow on the guard drives the weapon down towards the eyes (behind a shield it only shakes); a parry sweeps
     * it out to the right and up against the blow.
     */
    private static FirstPersonPose guardReaction(FirstPersonPose pose, boolean shield, float jolt, float parry) {
        if (jolt == 0 && parry == 0) {
            return pose;
        }
        float k = shield ? 0.35f : 1;
        Vector3f hand = new Vector3f(pose.hand).add(0, -0.07f * jolt * k, 0.12f * jolt * k);
        Vector3f blade = new Vector3f(pose.blade).add(0, -0.25f * jolt * k, 0);
        if (!shield) {
            hand.add(0.08f * parry, 0.05f * parry, -0.04f * parry);
            blade.add(0.7f * parry, 0.6f * parry, -0.2f * parry);
        }
        return new FirstPersonPose(hand, blade, pose.edge);
    }

    /** Running: the weapon carried low at the right, point forward; a two-handed one across the body. */
    private static FirstPersonPose running(Grip grip) {
        return switch (grip) {
            case ONE_HANDED -> facing(0.54f, -0.5f, -0.66f, 0.06f, -0.06f, -1, new Vector3f(0.7f, 0.4f, 0.6f), new Vector3f(0, -1, 0));
            case TWO_HANDED -> facing(0.34f, -0.52f, -0.66f, -0.75f, 0.75f, -0.35f, new Vector3f(0.2f, 0.3f, 1), new Vector3f(0, 1, 0));
            case POLEARM -> facing(0.5f, -0.54f, -0.64f, -0.08f, 0.22f, -1, new Vector3f(0.7f, 0.5f, 0.5f), new Vector3f(0, -1, 0));
        };
    }

    /**
     * Looking the weapon over, {@code t} of the way through: a one-handed weapon is brought up before the eyes, flat
     * to them, then turned to look along the edge; a two-handed one is swung onto the shoulder; a polearm is grounded
     * upright at the side.
     */
    private static FirstPersonPose inspect(Grip grip, float t) {
        FirstPersonPose guard = guard(grip);
        return switch (grip) {
            case ONE_HANDED -> {
                FirstPersonPose flat = facing(0.17f, -0.2f, -0.6f, -0.05f, 1, -0.25f, new Vector3f(0, 0.1f, 1), new Vector3f(-1, 0, 0));
                FirstPersonPose edge = facing(0.17f, -0.18f, -0.6f, -0.05f, 1, -0.25f, new Vector3f(1, 0, 0.1f), new Vector3f(0, 0, 1));
                yield t < 0.22f ? FirstPersonPose.lerp(guard, flat, backOut(t / 0.22f))
                        : t < 0.42f ? flat
                        : t < 0.58f ? FirstPersonPose.lerp(flat, edge, smooth((t - 0.42f) / 0.16f))
                        : t < 0.78f ? edge
                        : FirstPersonPose.lerp(edge, guard, smooth((t - 0.78f) / 0.22f));
            }
            case TWO_HANDED -> {
                FirstPersonPose shoulder = facing(0.58f, -0.4f, -0.42f, 0.35f, 0.55f, 0.75f, new Vector3f(1, 0.2f, 0), new Vector3f(0, 1, 0));
                yield t < 0.22f ? FirstPersonPose.lerp(guard, shoulder, backOut(t / 0.22f))
                        : t < 0.78f ? shoulder : FirstPersonPose.lerp(shoulder, guard, smooth((t - 0.78f) / 0.22f));
            }
            case POLEARM -> {
                FirstPersonPose planted = facing(0.62f, -0.62f, -0.78f, 0.05f, 1, 0.05f, TO_CAMERA, new Vector3f(0, 0, -1));
                yield t < 0.22f ? FirstPersonPose.lerp(guard, planted, smooth(t / 0.22f))
                        : t < 0.8f ? planted : FirstPersonPose.lerp(planted, guard, smooth((t - 0.8f) / 0.2f));
            }
        };
    }

    /**
     * From the guard to the end of the wind-up along a curve: the hand rises on the way and the blade turns back,
     * carrying a little past the end and settling there.
     */
    private static FirstPersonPose windup(AttackType attack, Grip grip, boolean mirrored, Combo combo, float p) {
        FirstPersonPose pose = FirstPersonPose.lerp(guard(grip), windupEnd(attack, grip, mirrored, combo, 0), backOut(p));
        if (attack == AttackType.SLASH || attack == AttackType.OVERHEAD) {
            pose.hand.y += 0.06f * (float) Math.sin(Math.PI * smooth(p));
        }
        return pose;
    }

    /**
     * The end of the wind-up; {@code charge} (0 to 1) draws it back further for heavy attacks, and finishers are drawn
     * back a little further too.
     */
    private static FirstPersonPose windupEnd(AttackType attack, Grip grip, boolean mirrored, Combo combo, float charge) {
        float deep = 1.4f * charge + (combo != Combo.NONE && combo != Combo.KICK_FOLLOW ? 0.4f : 0);
        return switch (attack) {
            // The blade is raised beside the head and cocked back, edge towards the side the swing goes to.
            case SLASH -> {
                if (combo == Combo.HAMSTRING) {
                    // Low at the right, point down and back, ready to cut at the legs.
                    yield facing(0.6f, -0.42f, -0.74f, 0.4f, -0.55f, 0.45f, new Vector3f(-0.3f, 0.2f, 1), new Vector3f(-1, 0, 0));
                }
                yield mirrored
                        ? facing(-0.48f - 0.08f * deep, 0.03f * deep, -0.8f + 0.06f * deep, -0.35f - 0.1f * deep, 0.9f, 0.25f + 0.25f * deep,
                        new Vector3f(0.4f, 0, 1), new Vector3f(1, 0, 0))
                        : facing(0.6f + 0.08f * deep, 0.02f + 0.03f * deep, -0.8f + 0.06f * deep, 0.3f + 0.1f * deep, 0.9f, 0.25f + 0.25f * deep,
                        new Vector3f(-0.4f, 0, 1), new Vector3f(-1, 0, 0));
            }
            case OVERHEAD -> facing(0.3f, 0.2f + 0.07f * deep, -0.85f + 0.05f * deep, 0.12f, 0.85f - 0.15f * deep, 0.5f + 0.35f * deep,
                    new Vector3f(1, 0, 0), new Vector3f(0, 0.8f, -0.6f));
            case STAB -> aimed(0.42f + 0.04f * deep, -0.42f - 0.03f * deep, -0.62f + 0.14f * deep, 0);
            case KICK -> guard(grip);
        };
    }

    /** The pose {@code p} of the way through the release of an attack. */
    static FirstPersonPose release(AttackType attack, Grip grip, boolean mirrored, float p) {
        return release(attack, grip, mirrored, Combo.NONE, p);
    }

    /** The pose {@code p} of the way through the release of an attack that may be a finisher. */
    static FirstPersonPose release(AttackType attack, Grip grip, boolean mirrored, Combo combo, float p) {
        float t = smooth(p);
        return switch (attack) {
            case SLASH -> slash(grip, mirrored, combo, t);
            case OVERHEAD -> {
                // The chop comes down slightly diagonally, from the upper right to the middle, so the blade stays in view.
                // Hammerfalls and mills come from higher and go lower.
                boolean big = combo == Combo.HAMMERFALL || combo == Combo.MILL;
                float theta = lerp(big ? 0 : 10, big ? 160 : 148, t) * DEG;
                float sin = (float) Math.sin(theta);
                float cos = (float) Math.cos(theta);
                Vector3f hand = new Vector3f(lerp(0.3f, 0.1f, t), lerp(big ? 0.34f : 0.26f, big ? -0.38f : -0.32f, t),
                        lerp(-0.8f, -0.9f, t) - 0.1f * (float) Math.sin(Math.PI * t));
                Vector3f blade = new Vector3f(-0.45f * sin, cos, -0.85f * sin);
                Vector3f motion = new Vector3f(-0.45f * cos, -sin, -0.85f * cos);
                yield facing(hand, blade, new Vector3f(0.4f, 0.2f, 1), motion);
            }
            case STAB -> {
                // Out fast, held for a moment at full reach; a sting lunges further.
                float out = PoseMath.easeOut(Math.min(1, p * 1.3f));
                float reach = combo == Combo.STING ? -1.25f : -1.05f;
                yield aimed(lerp(0.42f, combo == Combo.STING ? 0.14f : 0.2f, out), lerp(-0.42f, -0.26f, out), lerp(-0.62f, reach, out), 0);
            }
            case KICK -> guard(grip);
        };
    }

    /**
     * The hand crosses the view on a curve that bows away from the camera and the blade leads: it points ahead and
     * towards the side the swing goes to, turning further to that side as the swing ends. The swing falls a little
     * from the shoulder to the other hip, so it is not seen edge-on from the eyes.
     */
    private static FirstPersonPose slash(Grip grip, boolean mirrored, Combo combo, float t) {
        float side = mirrored ? -1 : 1;
        // A whirlwind sweeps wider; a hamstring goes low, the blade pointing down at the legs.
        boolean wide = combo == Combo.WHIRLWIND;
        boolean low = combo == Combo.HAMSTRING;
        float reach = (grip == Grip.ONE_HANDED ? 0.62f : 0.5f) + (wide ? 0.2f : 0);
        float bow = (float) Math.sin(t * Math.PI);
        Vector3f hand = new Vector3f(side * lerp(reach, -reach, t), lerp(0.02f, -0.32f, t) + 0.04f * bow - (low ? 0.2f : 0),
                lerp(-0.78f, -0.86f, t) - 0.16f * bow);
        float psi = lerp(wide ? -15 : -25, wide ? -140 : -120, t) * DEG;
        float sin = (float) Math.sin(psi);
        float cos = (float) Math.cos(psi);
        Vector3f blade = new Vector3f(side * sin, low ? lerp(-0.35f, -0.7f, t) : lerp(0.32f, -0.28f, t), -cos);
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
