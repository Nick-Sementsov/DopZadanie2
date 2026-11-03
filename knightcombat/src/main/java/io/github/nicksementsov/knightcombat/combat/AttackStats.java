package io.github.nicksementsov.knightcombat.combat;

/**
 * Timing and damage of one attack of a weapon.
 *
 * @param damage        damage of a clean hit (half-hearts)
 * @param windup        wind-up length in ticks
 * @param release       release length in ticks, the time the weapon can hit
 * @param recovery      recovery length in ticks after a release that was not continued into a combo
 * @param staminaDamage stamina a blocker loses when blocking this attack
 */
public record AttackStats(float damage, int windup, int release, int recovery, float staminaDamage) {
    public AttackStats scaled(float damageFactor) {
        return new AttackStats(damage * damageFactor, windup, release, recovery, staminaDamage);
    }
}
