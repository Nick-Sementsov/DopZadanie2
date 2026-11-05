package io.github.nicksementsov.knightcombat.combat;

/** What a fighter is doing right now. Every phase except IDLE and BLOCK has a fixed length in ticks. */
public enum Phase {
    IDLE,
    /** Wind-up: the attack is visible and can still be feinted or morphed into another attack. */
    WINDUP,
    /** Release: the weapon is moving and hits whatever it meets. */
    RELEASE,
    /** Recovery after an attack, a feint, a kick or after being blocked. */
    RECOVERY,
    /** Holding the weapon up to block; the first ticks are a parry. */
    BLOCK,
    /** Flinched by a hit, kicked out of a block or with a broken guard: no actions. */
    STAGGER,
    /** Wind-up of a kick. */
    KICK,
    /** Battle cry. */
    CRY,
    /** Drawing the weapon back to throw it; it leaves the hand at the end. */
    THROW,
    /** A quick step aside, back or forward, out of the way of a blow. */
    DODGE,
    /** A rider holding a lance or spear couched under the arm, ready to strike with the speed of the mount. */
    COUCH;

    private static final Phase[] VALUES = values();

    public static Phase byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
    }
}
