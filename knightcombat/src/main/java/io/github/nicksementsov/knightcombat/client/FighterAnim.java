package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatRules;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import net.minecraft.world.entity.player.Player;

/**
 * What the client knows about the combat state of one player, for the animations. The server sends every
 * change; in between, the client moves on to the next phase by itself so that latency does not freeze a swing.
 */
public final class FighterAnim {
    /** How much of the time of a hit-stop the swing stands still. */
    private static final float STALL_DEPTH = 0.9f;

    Phase phase = Phase.IDLE;
    AttackType attack = AttackType.SLASH;
    int flags;
    int length;
    /** Client tick at which the phase started. */
    long start;
    /** Changes whenever a different phase starts; the animators blend from the last shown pose when it changes. */
    int version;

    // Poses shown last and the pose a new phase blends from, separately for the model and the first person view.
    ThirdPersonPose tpShown;
    ThirdPersonPose tpFrom;
    int tpVersion = -1;
    FirstPersonPose fpShown;
    FirstPersonPose fpFrom;
    int fpVersion = -1;

    // Hit-stop: when a blow lands, the swing almost stops for an instant. The animation clock of the phase runs
    // slower by the time frozen so far, plus the part of the current stop that has passed.
    private float stalled;
    private float stallFrom;
    private float stallTo;

    // The last blow that landed on this fighter: when, from which side (1 the right, -1 the left) and how hard.
    private float hurtAt = -100;
    float hurtSide;
    private float hurtStrength;

    public Phase phase() {
        return phase;
    }

    public AttackType attack() {
        return attack;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public float elapsed(float partialTick) {
        float now = ClientCombat.ticks() + partialTick;
        return now - start - frozen(now);
    }

    /** Animation time lost to hit-stops up to {@code now}. */
    private float frozen(float now) {
        return stalled + Math.max(0, Math.min(now, stallTo) - stallFrom) * STALL_DEPTH;
    }

    /** Almost stops the swing for {@code ticks}, as if the blade bit into what it hit. */
    void hitStop(float ticks) {
        float now = ClientCombat.ticks();
        stalled = frozen(now);
        stallFrom = now;
        stallTo = now + ticks;
    }

    /** The fighter was struck from {@code side} (1 the right, -1 the left, 0 the front) with {@code strength} 0..1. */
    void hurt(float side, float strength) {
        hurtAt = ClientCombat.ticks();
        hurtSide = side;
        hurtStrength = strength;
    }

    /**
     * How much the fighter flinches from the last blow now: a snap within a tick, then a springy return with
     * a small rebound, about one at the strongest.
     */
    float flinch(float partialTick) {
        float x = ClientCombat.ticks() + partialTick - hurtAt;
        if (x < 0 || x > 12) {
            return 0;
        }
        float envelope = x < 1 ? x : (float) (Math.exp(-(x - 1) / 2.5) * Math.cos((x - 1) * 0.55));
        return envelope * hurtStrength;
    }

    /** Progress through the phase, 0..1. Phases without a length are always complete. */
    public float progress(float partialTick) {
        return length <= 0 ? 1 : PoseMath.clamp01(elapsed(partialTick) / length);
    }

    void apply(Phase newPhase, AttackType newAttack, int newFlags, int newLength, long newStart) {
        boolean same = phase == newPhase && attack == newAttack && flags == newFlags && length == newLength;
        if (same && Math.abs(start - newStart) <= 2) {
            // The server confirms what is already shown; keep the timing the client had, it is smoother.
            return;
        }
        phase = newPhase;
        attack = newAttack;
        flags = newFlags;
        length = newLength;
        start = newStart;
        stalled = 0;
        stallFrom = 0;
        stallTo = 0;
        version++;
    }

    /** Moves to the next phase when the current one is over, the way the server will. */
    void advance(Player player) {
        if (length <= 0 || elapsed(0) < length) {
            return;
        }
        long end = start + length + Math.round(frozen(ClientCombat.ticks()));
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        switch (phase) {
            case WINDUP -> {
                if (weapon != null) {
                    apply(Phase.RELEASE, attack, flags, Math.max(1, weapon.attack(attack).release()), end);
                }
            }
            case RELEASE -> {
                if (weapon != null) {
                    apply(Phase.RECOVERY, attack, flags & CombatState.MIRRORED, weapon.attack(attack).recovery(), end);
                }
            }
            case KICK -> apply(Phase.RECOVERY, AttackType.KICK, 0, CombatRules.KICK_RECOVERY, end);
            case RECOVERY, STAGGER, CRY -> apply(Phase.IDLE, attack, 0, 0, end);
            default -> {
            }
        }
    }
}
