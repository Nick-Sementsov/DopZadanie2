package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatRules;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * What the client knows about the combat state of one player, for the animations. The server sends every
 * change; in between, the client moves on to the next phase by itself so that latency does not freeze a swing.
 */
public final class FighterAnim {
    /** How much of the time of a hit-stop the swing stands still. */
    private static final float STALL_DEPTH = 0.9f;
    /** Ticks a remote fighter's wind-up may run over before the client moves on without the server (heavy attacks wind up longer). */
    private static final int REMOTE_GRACE = 3;
    /** Length of the flourish of drawing a weapon, in ticks. */
    static final float DRAW_TICKS = 9;

    Phase phase = Phase.IDLE;
    AttackType attack = AttackType.SLASH;
    int flags;
    Combo combo = Combo.NONE;
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
    // The turn of the whole body (whirlwinds), shown last and when the phase started.
    float spinShown;
    float spinFrom;
    int spinVersion = -1;

    // Hit-stop: when a blow lands, the swing almost stops for an instant. The animation clock of the phase runs
    // slower by the time frozen so far, plus the part of the current stop that has passed.
    private float stalled;
    private float stallFrom;
    private float stallTo;

    // The last blow that landed on this fighter: when, from which side (1 the right, -1 the left) and how hard.
    private float hurtAt = -100;
    float hurtSide;
    private float hurtStrength;

    // The weapon in hand and when it was drawn.
    private Item heldItem;
    private float drawnAt = -100;

    public Phase phase() {
        return phase;
    }

    public AttackType attack() {
        return attack;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public Combo combo() {
        return combo;
    }

    /** A finisher with its own look (the follow-up of a kick only comes out faster). */
    boolean finisher() {
        return combo != Combo.NONE && combo != Combo.KICK_FOLLOW;
    }

    /** Notices the fighter taking another weapon in hand, for the flourish of drawing it. */
    void watchHeld(Player player) {
        Item item = player.getMainHandItem().getItem();
        if (heldItem != null && item != heldItem && Weapons.isWeapon(player.getMainHandItem())) {
            drawnAt = ClientCombat.ticks();
        }
        heldItem = item;
    }

    /** How far the drawing of the weapon is, 0 just drawn to 1 done. */
    float drawProgress(float partialTick) {
        return PoseMath.clamp01((ClientCombat.ticks() + partialTick - drawnAt) / DRAW_TICKS);
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
        apply(newPhase, newAttack, newFlags, combo, newLength, newStart);
    }

    void apply(Phase newPhase, AttackType newAttack, int newFlags, Combo newCombo, int newLength, long newStart) {
        boolean same = phase == newPhase && attack == newAttack && flags == newFlags && combo == newCombo && length == newLength;
        if (same && Math.abs(start - newStart) <= 2) {
            // The server confirms what is already shown; keep the timing the client had, it is smoother.
            return;
        }
        phase = newPhase;
        attack = newAttack;
        flags = newFlags;
        combo = newCombo;
        length = newLength;
        start = newStart;
        stalled = 0;
        stallFrom = 0;
        stallTo = 0;
        version++;
    }

    /**
     * Moves to the next phase when the current one is over, the way the server will. The local player's own wind-up
     * goes on into the charge of a heavy attack while the attack key is held; other fighters' wind-ups wait a moment
     * for the server, which knows whether their key is held.
     */
    void advance(Player player, boolean local) {
        if (length <= 0 || elapsed(0) < length) {
            return;
        }
        long end = start + length + Math.round(frozen(ClientCombat.ticks()));
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        switch (phase) {
            case WINDUP -> {
                if (weapon == null || !local && elapsed(0) < length + REMOTE_GRACE) {
                    return;
                }
                if (local && ClientCombat.attackHeld() && CombatConfig.heavyAttacks() && !has(CombatState.HEAVY)
                        && !has(CombatState.RIPOSTE) && ClientCombat.stamina() >= CombatRules.HEAVY_COST) {
                    apply(Phase.WINDUP, attack, flags | CombatState.HEAVY, combo, CombatRules.heavyWindup(weapon, attack), end);
                } else {
                    apply(Phase.RELEASE, attack, flags, combo, Math.max(1, weapon.attack(attack).release()), end);
                }
            }
            case RELEASE -> {
                if (weapon != null) {
                    apply(Phase.RECOVERY, attack, flags & CombatState.MIRRORED, combo,
                            weapon.attack(attack).recovery() + CombatRules.extraRecovery(flags), end);
                }
            }
            case KICK -> apply(Phase.RECOVERY, AttackType.KICK, flags & CombatState.SHIELD_BASH, Combo.NONE, CombatRules.KICK_RECOVERY, end);
            case THROW -> apply(Phase.RECOVERY, attack, CombatState.THROWN, Combo.NONE, CombatRules.THROW_RECOVERY, end);
            case RECOVERY, STAGGER, CRY, DODGE -> apply(Phase.IDLE, attack, 0, Combo.NONE, 0, end);
            default -> {
            }
        }
    }
}
