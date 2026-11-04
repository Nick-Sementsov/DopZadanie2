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
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElephantModel.LAYER, ElephantModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DruzhinaRegistry.SOLDIER_MENU.get(), SoldierScreen::new));
    }
}
