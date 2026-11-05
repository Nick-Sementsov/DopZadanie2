package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Druzhina.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DruzhinaKeys.COMMAND);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DruzhinaRegistry.SOLDIER.get(), SoldierRenderer::new);
        event.registerEntityRenderer(DruzhinaRegistry.WAR_ELEPHANT.get(), ElephantRenderer::new);
        event.registerEntityRenderer(DruzhinaRegistry.TREBUCHET.get(), io.github.nicksementsov.druzhina.client.siege.SiegeRenderer::trebuchet);
        event.registerEntityRenderer(DruzhinaRegistry.BALLISTA.get(), io.github.nicksementsov.druzhina.client.siege.SiegeRenderer::ballista);
        event.registerEntityRenderer(DruzhinaRegistry.RAM.get(), io.github.nicksementsov.druzhina.client.siege.SiegeRenderer::ram);
        event.registerEntityRenderer(DruzhinaRegistry.SIEGE_STONE.get(), io.github.nicksementsov.druzhina.client.siege.SiegeStoneRenderer::new);
        event.registerEntityRenderer(DruzhinaRegistry.BALLISTA_BOLT.get(), io.github.nicksementsov.druzhina.client.siege.BoltRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElephantModel.LAYER, ElephantModel::createBodyLayer);
        event.registerLayerDefinition(io.github.nicksementsov.druzhina.client.siege.TrebuchetModel.LAYER, io.github.nicksementsov.druzhina.client.siege.TrebuchetModel::createBodyLayer);
        event.registerLayerDefinition(io.github.nicksementsov.druzhina.client.siege.BallistaModel.LAYER, io.github.nicksementsov.druzhina.client.siege.BallistaModel::createBodyLayer);
        event.registerLayerDefinition(io.github.nicksementsov.druzhina.client.siege.RamModel.LAYER, io.github.nicksementsov.druzhina.client.siege.RamModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DruzhinaRegistry.SOLDIER_MENU.get(), SoldierScreen::new));
    }
}
