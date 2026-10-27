package io.github.nicksementsov.knightcombat;

import io.github.nicksementsov.knightcombat.network.CombatNetwork;
import io.github.nicksementsov.knightcombat.registry.ModCreativeTabs;
import io.github.nicksementsov.knightcombat.registry.ModItems;
import io.github.nicksementsov.knightcombat.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Knightly Combat: melee fights with wind-ups, directional attacks, blocks, parries, ripostes, feints,
 * kicks and stamina. The rules run on the server ({@code combat} package), the client sends inputs and
 * animates the fighters ({@code client} package).
 */
@Mod(KnightCombat.MODID)
public final class KnightCombat {
    public static final String MODID = "knightcombat";

    public KnightCombat(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modBus);
        modBus.addListener(this::commonSetup);

        context.registerConfig(ModConfig.Type.SERVER, CombatConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, CombatConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CombatNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
