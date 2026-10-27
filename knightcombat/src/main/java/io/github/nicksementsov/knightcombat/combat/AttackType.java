package io.github.nicksementsov.knightcombat.combat;

/** The kinds of attack a player can make. Slash, overhead and stab use the weapon, the kick does not. */
public enum AttackType {
    /** Horizontal swing across the view. */
    SLASH,
    /** Vertical chop from above the head. */
    OVERHEAD,
    /** Straight thrust forward. */
    STAB,
    /** Short kick that breaks blocks. */
    KICK;

    private static final AttackType[] VALUES = values();

    public static AttackType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SLASH;
    }

    public boolean usesWeapon() {
        return this != KICK;
    }
}
