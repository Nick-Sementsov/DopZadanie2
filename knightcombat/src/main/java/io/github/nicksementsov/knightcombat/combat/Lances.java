package io.github.nicksementsov.knightcombat.combat;

import io.github.nicksementsov.knightcombat.CombatConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Charges with a couched lance. A rider holding a lance (the tournament lances of Epic Knights, or any item named a
 * lance) or a spear lowers it under the arm and lets the horse carry it into the enemy: the faster the horse runs,
 * the harder the lance strikes.
 */
public final class Lances {
    /** Below this speed of the mount (blocks per tick, about a trot) the lance does not strike. */
    public static final float MIN_SPEED = 0.18f;
    /** At this speed (a gallop) the lance strikes with all its force. */
    public static final float FULL_SPEED = 0.5f;
    /** How a lance is drawn in hand: lances do not fight with the melee rules, but they are drawn like weapons. */
    private static final AttackStats NO_ATTACK = new AttackStats(1, 10, 5, 10, 10);
    public static final WeaponStats RENDER = new WeaponStats(WeaponKind.SPEAR, Grip.ONE_HANDED, 4.5f,
            NO_ATTACK, NO_ATTACK, NO_ATTACK, 1, 0, false, 1);

    private Lances() {
    }

    /** A lance: a tournament lance of Epic Knights or any item whose name says it is a lance. */
    public static boolean isLance(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (EpicKnights.isLance(stack.getItem())) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getPath().toLowerCase(Locale.ROOT).contains("lance");
    }

    /** Lances and spears can be couched. */
    public static boolean couchable(ItemStack stack) {
        if (isLance(stack)) {
            return true;
        }
        WeaponStats weapon = Weapons.of(stack);
        return weapon != null && weapon.kind() == WeaponKind.SPEAR;
    }

    /** Whether the player rides something alive (a horse, a donkey, a camel) with a lance or spear in the main hand. */
    public static boolean canCouch(Player player) {
        Entity vehicle = player.getVehicle();
        return CombatConfig.mountedCharges() && vehicle instanceof LivingEntity && couchable(player.getMainHandItem());
    }

    /** How far a couched lance reaches ahead of the rider's shoulder, in blocks. */
    public static float reach(ItemStack stack) {
        WeaponStats weapon = Weapons.of(stack);
        if (weapon != null) {
            return weapon.reach() + 0.5f;
        }
        EpicKnights.Info epic = EpicKnights.info(stack.getItem());
        return epic != null ? Mth.clamp(3.0f + epic.bonusReach(), 3.5f, 6.5f) : RENDER.reach();
    }

    /** How hard a lance carried at this speed strikes, from 0 (too slow) to 1 (a full gallop). */
    public static float power(float speed) {
        return Mth.clamp((speed - MIN_SPEED) / (FULL_SPEED - MIN_SPEED), 0, 1);
    }
}
