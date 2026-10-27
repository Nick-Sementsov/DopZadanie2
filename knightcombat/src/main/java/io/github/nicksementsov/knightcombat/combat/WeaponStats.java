package io.github.nicksementsov.knightcombat.combat;

/**
 * Everything the combat rules need to know about a weapon.
 *
 * @param kind          family of the weapon
 * @param grip          how the weapon is held
 * @param reach         distance from the eyes to the tip of the weapon, in blocks
 * @param blockCost     multiplier of the stamina the wielder loses when blocking (lower is a better blocker)
 * @param armorPiercing share of the damage added back against armoured targets (blunt weapons)
 * @param breaksShields whether a hit on a raised shield disables it, like a vanilla axe
 * @param size          how much larger than its own model the weapon is drawn in the hands (huge weapons look huge)
 */
public record WeaponStats(WeaponKind kind, Grip grip, float reach,
                          AttackStats slash, AttackStats overhead, AttackStats stab,
                          float blockCost, float armorPiercing, boolean breaksShields, float size) {

    /** The kick does not depend on the weapon. */
    public static final AttackStats KICK = new AttackStats(1.0f, CombatRules.KICK_WINDUP, 1, CombatRules.KICK_RECOVERY, 0);

    public AttackStats attack(AttackType type) {
        return switch (type) {
            case SLASH -> slash;
            case OVERHEAD -> overhead;
            case STAB -> stab;
            case KICK -> KICK;
        };
    }

    /** How strong an attack is compared to the slash; the slash damage is what the item shows as attack damage. */
    public float damageFactor(AttackType type) {
        return attack(type).damage() / slash.damage();
    }

    public boolean twoHanded() {
        return grip.twoHanded();
    }
}
