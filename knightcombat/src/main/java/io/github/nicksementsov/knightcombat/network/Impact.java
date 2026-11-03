package io.github.nicksementsov.knightcombat.network;

/** A moment of a fight that everyone nearby feels: blows that land, clashes of weapons, kicks and battle cries. */
public enum Impact {
    /** A weapon hit a creature. */
    HIT,
    /** A weapon hit and killed a creature. */
    KILL,
    /** A blow was blocked with a weapon or a shield. */
    BLOCK,
    /** A blow was parried or countered. */
    PARRY,
    /** A blow broke the guard of a blocker who ran out of stamina. */
    GUARD_BREAK,
    /** A weapon struck a wall. */
    WALL,
    /** A kick landed. */
    KICK,
    /** A battle cry. */
    CRY;

    private static final Impact[] VALUES = values();

    public static Impact byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HIT;
    }

    /** Whether the one on the receiving end is shaken by it, not only the one who struck. */
    public boolean hurts() {
        return this == HIT || this == KILL || this == KICK || this == GUARD_BREAK;
    }
}
