package io.github.nicksementsov.knightcombat.item;

import io.github.nicksementsov.knightcombat.combat.AttackStats;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import org.jetbrains.annotations.NotNull;

/**
 * A medieval weapon. It is a sword for Minecraft (enchantments, repairs, cobwebs), but it only attacks
 * through the combat rules: its attack damage attribute is the damage of its slash.
 */
public class KnightWeaponItem extends SwordItem {
    private final WeaponStats stats;
    private final HeldSprite heldSprite;

    public KnightWeaponItem(WeaponStats stats, HeldSprite heldSprite, int durability) {
        super(Tiers.IRON, Math.round(stats.slash().damage() - 1 - Tiers.IRON.getAttackDamageBonus()),
                attackSpeedModifier(stats.slash()), new Item.Properties().durability(durability));
        this.stats = stats;
        this.heldSprite = heldSprite;
    }

    /** Attack speed shown to other mods: slashes per second without combos, relative to the base speed of 4. */
    private static float attackSpeedModifier(AttackStats slash) {
        return 20f / (slash.windup() + slash.release() + slash.recovery()) - 4f;
    }

    public WeaponStats stats() {
        return stats;
    }

    public HeldSprite heldSprite() {
        return heldSprite;
    }

    /** The attribute lines are replaced by the attack lines of the combat tooltip. */
    @Override
    public int getDefaultTooltipHideFlags(@NotNull ItemStack stack) {
        return ItemStack.TooltipPart.MODIFIERS.getMask();
    }
}
