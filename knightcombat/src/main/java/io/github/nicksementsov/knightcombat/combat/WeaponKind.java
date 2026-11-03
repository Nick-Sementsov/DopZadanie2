package io.github.nicksementsov.knightcombat.combat;

/**
 * Family of a weapon. It decides how strong each attack is, how fast the attacks are compared to each other,
 * how well the weapon blocks and whether it knocks shields aside.
 */
public enum WeaponKind {
    //            slash  overhead  stab   overhead and stab wind-up (ticks added)  block cost  breaks shields
    SWORD(1.0f, 1.12f, 0.95f, 2, 0, 0.95f, false),
    /** Estocs, rapiers: made for thrusting. */
    THRUSTING_SWORD(0.75f, 0.85f, 1.2f, 2, -1, 1.0f, false),
    DAGGER(0.85f, 1.0f, 1.15f, 1, -1, 1.35f, false),
    AXE(1.0f, 1.22f, 0.45f, 2, -1, 1.2f, true),
    /** Maces, hammers, clubs, flails, morning stars. */
    BLUNT(0.9f, 1.3f, 0.5f, 3, -1, 1.25f, true),
    /** Pikes, spears, partisans: made for thrusting from far away. */
    SPEAR(0.7f, 0.85f, 1.15f, 2, -2, 1.05f, false),
    /** Halberds, poleaxes, glaives, bardiches: long shafts with a heavy head. */
    POLEAXE(1.0f, 1.22f, 0.8f, 2, -1, 1.0f, true);

    final float slash;
    final float overhead;
    final float stab;
    final int overheadWindup;
    final int stabWindup;
    final float blockCost;
    final boolean breaksShields;

    WeaponKind(float slash, float overhead, float stab, int overheadWindup, int stabWindup, float blockCost, boolean breaksShields) {
        this.slash = slash;
        this.overhead = overhead;
        this.stab = stab;
        this.overheadWindup = overheadWindup;
        this.stabWindup = stabWindup;
        this.blockCost = blockCost;
        this.breaksShields = breaksShields;
    }

    public boolean polearm() {
        return this == SPEAR || this == POLEAXE;
    }
}
