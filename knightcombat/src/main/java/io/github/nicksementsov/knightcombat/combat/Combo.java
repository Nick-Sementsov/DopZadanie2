package io.github.nicksementsov.knightcombat.combat;

import static io.github.nicksementsov.knightcombat.combat.AttackType.OVERHEAD;
import static io.github.nicksementsov.knightcombat.combat.AttackType.SLASH;
import static io.github.nicksementsov.knightcombat.combat.AttackType.STAB;

/**
 * Finishing blows. Attacks chained one into the next (pressed during the release or early recovery of the previous
 * one) end in a finisher when they come in one of these orders: the last attack of the chain is stronger and looks
 * different. The chain starts over after a finisher, a block, a feint or a pause.
 */
public enum Combo {
    NONE(1, 1, 0, 1),
    /** Three slashes: the third one sweeps wider and the fighter spins with it. */
    WHIRLWIND(1.25f, 1.1f, 0.3f, 1, SLASH, SLASH, SLASH),
    /** Two slashes and an overhead: the blade circles once at the side and comes down fast. */
    MILL(1.35f, 1.2f, 0, 0.85f, SLASH, SLASH, OVERHEAD),
    /** Two overheads: the second is raised higher and comes down hard enough to drain a guard. */
    HAMMERFALL(1.3f, 1.6f, 0, 1, OVERHEAD, OVERHEAD),
    /** Three stabs: the third is a deep lunge that reaches further. */
    STING(1.3f, 1.0f, 0.8f, 0.8f, STAB, STAB, STAB),
    /** A stab and then a low slash at the legs that slows the one it cuts. */
    HAMSTRING(1.15f, 1.0f, 0, 1, STAB, SLASH),
    /** Any attack right after a kick that landed: it comes out fast while the enemy is off balance. */
    KICK_FOLLOW(1.15f, 1.0f, 0, 0.6f);

    private static final Combo[] VALUES = values();

    /** Damage multiplier of the finishing attack. */
    public final float damage;
    /** Multiplier of the stamina a blocker loses. */
    public final float staminaDamage;
    /** Reach added to the weapon, in blocks. */
    public final float reach;
    /** Wind-up multiplier. */
    public final float windup;
    private final AttackType[] order;

    Combo(float damage, float staminaDamage, float reach, float windup, AttackType... order) {
        this.damage = damage;
        this.staminaDamage = staminaDamage;
        this.reach = reach;
        this.windup = windup;
        this.order = order;
    }

    public static Combo byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    /** The finisher that the last {@code length} attacks of {@code chain} (oldest first) end in, or {@link #NONE}. */
    public static Combo match(AttackType[] chain, int length) {
        Combo best = NONE;
        for (Combo combo : VALUES) {
            int n = combo.order.length;
            if (n == 0 || n > length || n <= best.order.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < n && matches; i++) {
                matches = chain[length - n + i] == combo.order[i];
            }
            if (matches) {
                best = combo;
            }
        }
        return best;
    }
}
