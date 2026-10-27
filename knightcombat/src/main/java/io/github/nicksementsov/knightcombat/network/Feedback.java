package io.github.nicksementsov.knightcombat.network;

/** Things that happened to a player in a fight, shown near the crosshair and felt through the camera. */
public enum Feedback {
    /** Your attack hit. */
    HIT,
    /** You blocked an attack. */
    BLOCKED,
    /** You parried an attack: riposte now. */
    PARRIED,
    /** You countered an attack with the same attack. */
    COUNTERED,
    /** You ran out of stamina while blocking. */
    GUARD_BROKEN,
    /** Your attack was blocked. */
    GOT_BLOCKED,
    /** Your attack was parried: block the riposte. */
    GOT_PARRIED,
    /** Your attack was countered. */
    GOT_COUNTERED,
    /** Your attack broke the guard of the enemy. */
    BROKE_GUARD,
    /** Your weapon hit a wall. */
    WALL,
    /** Your kick broke a block. */
    KICK_BREAK,
    /** You were kicked out of your block. */
    KICKED,
    /** A hit interrupted your wind-up. */
    FLINCHED,
    /** Your battle cry restored stamina. */
    BATTLE_CRY;

    private static final Feedback[] VALUES = values();

    public static Feedback byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HIT;
    }
}
