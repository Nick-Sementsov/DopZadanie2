package io.github.nicksementsov.knightcombat.combat;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.item.KnightWeaponItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stats of the mod weapons, and of swords and axes from Minecraft and other mods. */
public final class Weapons {
    //                                                 damage windup release recovery staminaDamage
    public static final WeaponStats DAGGER = new WeaponStats("dagger", Grip.ONE_HANDED, 2.5f,
            new AttackStats(5, 5, 4, 5, 10),
            new AttackStats(6, 6, 4, 6, 12),
            new AttackStats(6, 4, 3, 5, 10),
            1.4f, 0, false);
    public static final WeaponStats FALCHION = new WeaponStats("falchion", Grip.ONE_HANDED, 2.8f,
            new AttackStats(7, 7, 5, 7, 15),
            new AttackStats(8, 8, 5, 8, 17),
            new AttackStats(6, 6, 4, 7, 13),
            1.1f, 0, false);
    public static final WeaponStats LONGSWORD = new WeaponStats("longsword", Grip.TWO_HANDED, 3.1f,
            new AttackStats(8, 8, 6, 8, 20),
            new AttackStats(10, 10, 6, 9, 22),
            new AttackStats(8, 8, 5, 8, 18),
            0.8f, 0, false);
    public static final WeaponStats ZWEIHANDER = new WeaponStats("zweihander", Grip.TWO_HANDED, 3.6f,
            new AttackStats(11, 11, 7, 11, 28),
            new AttackStats(13, 13, 7, 12, 30),
            new AttackStats(10, 10, 6, 11, 24),
            0.9f, 0, false);
    public static final WeaponStats BATTLE_AXE = new WeaponStats("battle_axe", Grip.TWO_HANDED, 3.0f,
            new AttackStats(10, 10, 6, 10, 30),
            new AttackStats(12, 12, 6, 11, 34),
            new AttackStats(6, 8, 5, 9, 16),
            1.0f, 0, true);
    public static final WeaponStats MORNING_STAR = new WeaponStats("morning_star", Grip.ONE_HANDED, 2.9f,
            new AttackStats(8, 9, 5, 9, 25),
            new AttackStats(10, 10, 5, 10, 28),
            new AttackStats(5, 7, 4, 8, 14),
            1.2f, 0.35f, false);
    public static final WeaponStats WAR_HAMMER = new WeaponStats("war_hammer", Grip.TWO_HANDED, 3.2f,
            new AttackStats(9, 12, 7, 12, 32),
            new AttackStats(14, 14, 7, 13, 38),
            new AttackStats(7, 10, 5, 11, 20),
            1.0f, 0.5f, true);
    public static final WeaponStats HALBERD = new WeaponStats("halberd", Grip.POLEARM, 3.9f,
            new AttackStats(10, 12, 7, 12, 30),
            new AttackStats(13, 13, 7, 12, 32),
            new AttackStats(9, 10, 6, 11, 22),
            1.0f, 0.15f, true);
    public static final WeaponStats SPEAR = new WeaponStats("spear", Grip.POLEARM, 4.2f,
            new AttackStats(6, 10, 6, 10, 14),
            new AttackStats(8, 11, 6, 11, 16),
            new AttackStats(9, 8, 5, 9, 20),
            1.1f, 0, false);

    private static final Map<Item, WeaponStats> VANILLA = new ConcurrentHashMap<>();

    private Weapons() {
    }

    /** Stats of the weapon in the stack, or {@code null} when the item does not fight with the combat rules. */
    public static WeaponStats of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof KnightWeaponItem weapon) {
            return weapon.stats();
        }
        if (!(item instanceof SwordItem) && !(item instanceof AxeItem) || !CombatConfig.vanillaWeapons()) {
            return null;
        }
        return VANILLA.computeIfAbsent(item, Weapons::vanillaStats);
    }

    public static boolean isWeapon(ItemStack stack) {
        return of(stack) != null;
    }

    private static WeaponStats vanillaStats(Item item) {
        if (item instanceof SwordItem sword) {
            // The attack damage of a sword already includes the 1 point every player has.
            float damage = sword.getDamage() + 1;
            return new WeaponStats("vanilla_sword", Grip.ONE_HANDED, 3.0f,
                    new AttackStats(damage, 7, 5, 7, 18),
                    new AttackStats(damage * 1.25f, 9, 5, 8, 20),
                    new AttackStats(damage * 1.05f, 7, 4, 7, 15),
                    1.0f, 0, false);
        }
        float damage = ((AxeItem) item).getAttackDamage() + 1;
        return new WeaponStats("vanilla_axe", Grip.ONE_HANDED, 2.9f,
                new AttackStats(damage * 0.95f, 10, 6, 10, 26),
                new AttackStats(damage * 1.15f, 11, 6, 11, 30),
                new AttackStats(damage * 0.55f, 8, 5, 9, 12),
                1.25f, 0, true);
    }
}
