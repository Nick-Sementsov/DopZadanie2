package io.github.nicksementsov.knightcombat.combat;

/** How a weapon is held; decides the animations and whether the off hand is used. */
public enum Grip {
    /** One hand on the hilt, the other hand is free (or holds a shield). */
    ONE_HANDED,
    /** Both hands close together on a long hilt. */
    TWO_HANDED,
    /** Both hands spread along a shaft. */
    POLEARM;

    public boolean twoHanded() {
        return this != ONE_HANDED;
    }
}
