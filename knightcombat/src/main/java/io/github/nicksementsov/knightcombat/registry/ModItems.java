package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.item.HeldSprite;
import io.github.nicksementsov.knightcombat.item.KnightWeaponItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, KnightCombat.MODID);
    private static final List<RegistryObject<KnightWeaponItem>> WEAPONS = new ArrayList<>();

    // Held textures: short weapons use their 16x16 icon, long ones a 32x32 texture. The grip points are printed
    // by tools/generate_assets.py, which draws the textures.
    public static final RegistryObject<KnightWeaponItem> DAGGER = weapon("dagger", Weapons.DAGGER, new HeldSprite(16, 5.7f, 10.3f), 400);
    public static final RegistryObject<KnightWeaponItem> FALCHION = weapon("falchion", Weapons.FALCHION, new HeldSprite(16, 3.5f, 12.5f), 600);
    public static final RegistryObject<KnightWeaponItem> MORNING_STAR = weapon("morning_star", Weapons.MORNING_STAR, new HeldSprite(16, 3.3f, 12.7f), 700);
    public static final RegistryObject<KnightWeaponItem> LONGSWORD = weapon("longsword", Weapons.LONGSWORD, new HeldSprite(32, 10.0f, 22.0f), 700);
    public static final RegistryObject<KnightWeaponItem> ZWEIHANDER = weapon("zweihander", Weapons.ZWEIHANDER, new HeldSprite(32, 9.6f, 22.4f), 800);
    public static final RegistryObject<KnightWeaponItem> BATTLE_AXE = weapon("battle_axe", Weapons.BATTLE_AXE, new HeldSprite(32, 9.9f, 22.1f), 650);
    public static final RegistryObject<KnightWeaponItem> WAR_HAMMER = weapon("war_hammer", Weapons.WAR_HAMMER, new HeldSprite(32, 9.9f, 22.1f), 750);
    public static final RegistryObject<KnightWeaponItem> HALBERD = weapon("halberd", Weapons.HALBERD, new HeldSprite(32, 9.2f, 22.8f), 750);
    public static final RegistryObject<KnightWeaponItem> SPEAR = weapon("spear", Weapons.SPEAR, new HeldSprite(32, 9.2f, 22.8f), 600);

    private ModItems() {
    }

    private static RegistryObject<KnightWeaponItem> weapon(String name, WeaponStats stats, HeldSprite held, int durability) {
        RegistryObject<KnightWeaponItem> item = ITEMS.register(name, () -> new KnightWeaponItem(stats, held, durability));
        WEAPONS.add(item);
        return item;
    }

    public static List<RegistryObject<KnightWeaponItem>> weapons() {
        return Collections.unmodifiableList(WEAPONS);
    }
}
