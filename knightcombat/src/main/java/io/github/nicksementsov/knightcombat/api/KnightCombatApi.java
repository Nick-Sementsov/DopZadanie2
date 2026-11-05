package io.github.nicksementsov.knightcombat.api;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.combat.CombatManager;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.network.CombatAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What other mods may use: the inputs of an NPC fighter (the same actions a player's keys send) and what it can see of
 * any fighter, itself or an opponent. Server side only.
 */
public final class KnightCombatApi {
    private KnightCombatApi() {
    }

    /** Whether {@code entity} fights by the rules: every player, and every {@link CombatFighter}. */
    public static boolean isFighter(Entity entity) {
        return CombatManager.isFighter(entity);
    }

    /**
     * Does what a player's key would do: start an attack ({@link CombatAction#SLASH}, {@code OVERHEAD}, {@code STAB}),
     * let go of the attack key ({@code ATTACK_RELEASE}; an attack held through its wind-up becomes heavy), feint,
     * kick, raise and lower the guard ({@code BLOCK_START} / {@code BLOCK_STOP}), dodge or cry.
     */
    public static void input(LivingEntity fighter, CombatAction action) {
        CombatManager.handleInput(fighter, action);
    }

    /** The fighter's state: its phase, the attack it winds up or swings, how far it is, its stamina. */
    public static CombatState state(LivingEntity fighter) {
        return CombatManager.state(fighter);
    }

    /** Whether the fighter's shield may be raised: a shield knocked aside or broken out of the guard is down a while. */
    public static boolean shieldReady(LivingEntity fighter) {
        return CombatManager.shieldReady(fighter);
    }

    /** How a weapon fights (reach, wind-ups, damage), or null when the item is not a weapon. */
    public static WeaponStats weapon(ItemStack stack) {
        return Weapons.of(stack);
    }

    public static float maxStamina() {
        return CombatConfig.maxStamina();
    }
}
