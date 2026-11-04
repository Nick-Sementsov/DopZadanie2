package io.github.nicksementsov.knightcombat.api;

/**
 * A creature that fights by the rules of Knightly Combat, like a player: its blows wind up, sweep and recover, it
 * blocks, parries, kicks and runs out of stamina, and a player's blows against it can be blocked and parried. Put it
 * on a {@link net.minecraft.world.entity.LivingEntity} (a humanoid one, to be drawn with the fighting poses) and let
 * its AI act through {@link KnightCombatApi#input}.
 *
 * <p>Who a fighter must not strike is decided by {@link net.minecraft.world.entity.Entity#isAlliedTo} and by
 * {@link net.minecraft.world.entity.OwnableEntity} owners, both ways.
 */
public interface CombatFighter {
}
