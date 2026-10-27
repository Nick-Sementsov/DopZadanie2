package io.github.nicksementsov.knightcombat.combat;

import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads the weapon data of Epic Knights (mod id {@code magistuarmory}) and of its addons, which register their
 * weapons through Epic Knights. Everything is looked up by reflection, so Epic Knights stays optional.
 */
final class EpicKnights {
    private static final String WEAPON_CLASS = "com.magistuarmory.item.MedievalWeaponItem";
    private static final String LANCE_CLASS = "com.magistuarmory.item.LanceItem";

    /**
     * What Epic Knights says about one of its weapons.
     *
     * @param twoHanded      0 one-handed, 1 hand-and-a-half, 2 two-handed
     * @param bonusReach     reach added to the normal 3 blocks
     * @param weight         how heavy the weapon is (0.6 for a dagger, 4 and more for a halberd)
     * @param maxBlockDamage damage the weapon can block on its own in Epic Knights, 0 when it cannot block
     * @param halberd        whether Epic Knights treats it as a halberd
     */
    record Info(int twoHanded, float bonusReach, float weight, float maxBlockDamage, boolean halberd) {
    }

    private EpicKnights() {
    }

    /** Lances keep the jousting of Epic Knights and stay out of the combat rules. */
    static boolean isLance(Item item) {
        return extendsClass(item.getClass(), LANCE_CLASS) != null;
    }

    /** Data of an Epic Knights weapon, or {@code null} for any other item. */
    static Info info(Item item) {
        Class<?> weaponClass = extendsClass(item.getClass(), WEAPON_CLASS);
        if (weaponClass == null) {
            return null;
        }
        try {
            Field typeField = weaponClass.getField("type");
            Object type = typeField.get(item);
            Class<?> typeClass = type.getClass();
            int twoHanded = (Integer) call(typeClass, type, "getTwoHanded");
            float bonusReach = (Float) call(typeClass, type, "getBonusAttackReach");
            float weight = (Float) call(typeClass, type, "getWeight");
            boolean canBlock = (Boolean) call(typeClass, type, "canBlock");
            float maxBlockDamage = canBlock ? (Float) call(typeClass, type, "getMaxBlockDamage") : 0;
            boolean halberd = (Boolean) call(typeClass, type, "isHalberd");
            return new Info(twoHanded, bonusReach, weight, maxBlockDamage, halberd);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A version of Epic Knights with a different layout: treat the item like any other modded sword.
            return null;
        }
    }

    private static Object call(Class<?> owner, Object target, String name) throws ReflectiveOperationException {
        Method method = owner.getMethod(name);
        return method.invoke(target);
    }

    private static Class<?> extendsClass(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }
}
