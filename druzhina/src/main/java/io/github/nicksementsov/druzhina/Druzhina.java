package io.github.nicksementsov.druzhina;

import io.github.nicksementsov.druzhina.network.DruzhinaNetwork;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Druzhina: a war band of your own. Hire swordsmen, spearmen, axemen, archers and knights at a muster table, lead them
 * with a commander's baton, and beat off the bandit bands that roam the land. Every soldier fights by the rules of
 * Knightly Combat, exactly as a player does, and soldiers who live through their fights rise in rank.
 */
@Mod(Druzhina.MODID)
public final class Druzhina {
    public static final String MODID = "druzhina";

    public Druzhina(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        DruzhinaRegistry.register(modBus);
        modBus.addListener(this::commonSetup);
        context.registerConfig(ModConfig.Type.SERVER, DruzhinaConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DruzhinaNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
