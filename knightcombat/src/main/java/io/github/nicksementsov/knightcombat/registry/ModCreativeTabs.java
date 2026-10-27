package io.github.nicksementsov.knightcombat.registry;

import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.item.KnightWeaponItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KnightCombat.MODID);

    public static final RegistryObject<CreativeModeTab> WEAPONS = CREATIVE_MODE_TABS.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.knightcombat.weapons"))
            .icon(() -> new ItemStack(ModItems.LONGSWORD.get()))
            .displayItems((parameters, output) -> {
                for (RegistryObject<KnightWeaponItem> weapon : ModItems.weapons()) {
                    output.accept(weapon.get());
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
