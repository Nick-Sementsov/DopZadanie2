package io.github.nicksementsov.knightcombat.network;

import io.github.nicksementsov.knightcombat.combat.AttackType;

/** Inputs a client sends to the server. */
public enum CombatAction {
    SLASH,
    OVERHEAD,
    STAB,
    FEINT,
    KICK,
    BLOCK_START,
    BLOCK_STOP,
    BATTLE_CRY;

    private static final CombatAction[] VALUES = values();

    public static CombatAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public static CombatAction of(AttackType type) {
        return switch (type) {
            case SLASH -> SLASH;
            case OVERHEAD -> OVERHEAD;
            case STAB -> STAB;
            case KICK -> KICK;
        };
    }

    /** The weapon attack of this input, or {@code null}. */
    public AttackType attack() {
        return switch (this) {
            case SLASH -> AttackType.SLASH;
            case OVERHEAD -> AttackType.OVERHEAD;
            case STAB -> AttackType.STAB;
            default -> null;
        };
    }
}
