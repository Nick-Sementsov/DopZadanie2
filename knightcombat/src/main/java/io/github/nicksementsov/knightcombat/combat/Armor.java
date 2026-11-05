package io.github.nicksementsov.knightcombat.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Worn armour, for the sounds it makes: mail and plate rattle and clank, leather and cloth (Minecraft's leather,
 * the gambesons, surcoats and hoods of Epic Knights) do not.
 */
public final class Armor {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Words in the name of armour that is metal, even when it is worn over cloth (a gambeson with mail sewn on). */
    private static final String[] METAL = {"chain", "mail", "plates", "scale", "lamellar", "brigandine"};
    /** Words in the name of armour that is not metal. */
    private static final String[] SOFT = {"leather", "gambeson", "aketon", "padded", "quilted", "cloth", "wool", "linen",
            "silk", "fur", "sheepskin", "robe", "tunic", "surcoat", "jupon", "doublet", "cloak", "cape", "hood", "pillbox",
            "eastern_hat", "fancy_hat", "straw", "puff_and_slash", "phrygian", "condottiero_cap", "kaftan", "turtle",
            "bandana", "scarf"};

    private Armor() {
    }

    /** How many pieces of metal armour {@code entity} wears, 0 to 4. */
    public static int metalPieces(LivingEntity entity) {
        int pieces = 0;
        for (EquipmentSlot slot : SLOTS) {
            if (isMetal(entity.getItemBySlot(slot))) {
                pieces++;
            }
        }
        return pieces;
    }

    public static boolean isMetal(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getMaterial() == ArmorMaterials.LEATHER) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return isMetal(id != null ? id.getPath() : "", armor.getMaterial().getName());
    }

    /** Whether armour of this item name and material name is metal: mail and plate are, leather and cloth are not. */
    static boolean isMetal(String item, String material) {
        String name = item.toLowerCase(Locale.ROOT);
        return contains(name, METAL) || !contains(name, SOFT) && !contains(material.toLowerCase(Locale.ROOT), SOFT);
    }

    private static boolean contains(String name, String[] words) {
        for (String word : words) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
