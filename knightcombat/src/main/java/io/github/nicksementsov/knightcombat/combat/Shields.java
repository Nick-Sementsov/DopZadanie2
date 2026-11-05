package io.github.nicksementsov.knightcombat.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Shields in the combat rules: Minecraft's shield, the shields of Epic Knights and its addons (they are shields of
 * Minecraft too) and any item of another mod that blocks like a shield. A raised shield is a block: it parries
 * right after it is raised, costs stamina for every blow it stops and covers more or less of the front by its size.
 */
public final class Shields {
    /** How much of the front a shield covers, how much stamina a blow on it costs and how long it parries. */
    public enum Size {
        /** Bucklers, targets, rondaches: quick to parry with, but they cover little and every blow tells. */
        SMALL(55, 0.6f, 8),
        /** Round, heater and kite shields, Minecraft's shield. */
        MEDIUM(80, 0.4f, 6),
        /** Pavises, scuta and tower shields: they cover almost everything in front and take blows easily. */
        LARGE(95, 0.28f, 5);

        /** Half-angle of the cone in front of the bearer that the shield covers, in degrees. */
        public final float halfAngle;
        /** Share of a blow's stamina cost that the bearer pays. */
        public final float blockCost;
        /** Ticks after raising during which a block is a parry. */
        public final int parryWindow;

        Size(float halfAngle, float blockCost, int parryWindow) {
            this.halfAngle = halfAngle;
            this.blockCost = blockCost;
            this.parryWindow = parryWindow;
        }
    }

    private static final String[] SMALL = {"buckler", "target", "rondache", "rotella", "parma", "pelta", "small"};
    private static final String[] LARGE = {"pavise", "pavese", "scutum", "hoplon", "aspis", "thureos", "tureos", "dueling",
            "tower", "mantlet", "large", "great"};

    private Shields() {
    }

    public static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ShieldItem || stack.canPerformAction(ToolActions.SHIELD_BLOCK));
    }

    public static Size size(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return size(id != null ? id.getPath() : "");
    }

    /** The size of a shield from the name of its item. */
    static Size size(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        for (String word : SMALL) {
            if (name.contains(word)) {
                return Size.SMALL;
            }
        }
        for (String word : LARGE) {
            if (name.contains(word)) {
                return Size.LARGE;
            }
        }
        return Size.MEDIUM;
    }

    /**
     * The shield the fighter carries: in the off hand next to a one-handed weapon (or nothing), or in the main hand
     * when the off hand has none. Two-handed weapons leave no hand for a shield.
     */
    public static ItemStack carried(LivingEntity entity) {
        WeaponStats weapon = Weapons.of(entity.getMainHandItem());
        if (weapon != null && weapon.twoHanded()) {
            return ItemStack.EMPTY;
        }
        if (isShield(entity.getOffhandItem())) {
            return entity.getOffhandItem();
        }
        return isShield(entity.getMainHandItem()) ? entity.getMainHandItem() : ItemStack.EMPTY;
    }

    /** Whether the entity holds a shield up right now (from the moment it is raised, unlike Minecraft's own blocking). */
    public static boolean raised(LivingEntity entity) {
        return entity.isUsingItem() && isShield(entity.getUseItem());
    }

    /** Whether the raised shield is in the off hand, the weapon in the main hand staying ready. */
    public static boolean raisedInOffHand(LivingEntity entity) {
        return raised(entity) && entity.getUsedItemHand() == InteractionHand.OFF_HAND;
    }
}
