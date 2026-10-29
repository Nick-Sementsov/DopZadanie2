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
    BATTLE_CRY,
    /** The key of the attack was let go: an attack whose key is held through the wind-up becomes a heavy attack. */
    ATTACK_RELEASE,
    THROW,
    DODGE_BACK,
    DODGE_LEFT,
    DODGE_RIGHT,
    DODGE_FORWARD,
    COUCH_START,
    COUCH_STOP;

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

    /** The dodge in the given direction: -1 left, 1 right, 0 back, 2 forward. */
    public static CombatAction dodge(int direction) {
        return switch (direction) {
            case -1 -> DODGE_LEFT;
            case 1 -> DODGE_RIGHT;
            case 2 -> DODGE_FORWARD;
            default -> DODGE_BACK;
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
