package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.client.mesh.BallistaMesh;
import io.github.nicksementsov.druzhina.client.mesh.BombardMesh;
import io.github.nicksementsov.druzhina.client.mesh.ElephantMesh;
import io.github.nicksementsov.druzhina.client.mesh.MangonelMesh;
import io.github.nicksementsov.druzhina.client.mesh.MantletMesh;
import io.github.nicksementsov.druzhina.client.mesh.RamMesh;
import io.github.nicksementsov.druzhina.client.mesh.SiegeTowerMesh;
import io.github.nicksementsov.druzhina.client.mesh.TrebuchetMesh;
import io.github.nicksementsov.druzhina.client.siege.BallistaModel;
import io.github.nicksementsov.druzhina.client.siege.BoltRenderer;
import io.github.nicksementsov.druzhina.client.siege.BombardModel;
import io.github.nicksementsov.druzhina.client.siege.MangonelModel;
import io.github.nicksementsov.druzhina.client.siege.MantletModel;
import io.github.nicksementsov.druzhina.client.siege.RamModel;
import io.github.nicksementsov.druzhina.client.siege.SiegeRenderer;
import io.github.nicksementsov.druzhina.client.siege.SiegeStoneRenderer;
import io.github.nicksementsov.druzhina.client.siege.SiegeTowerModel;
import io.github.nicksementsov.druzhina.client.siege.TrebuchetModel;
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
        event.registerEntityRenderer(DruzhinaRegistry.TREBUCHET.get(), SiegeRenderer::trebuchet);
        event.registerEntityRenderer(DruzhinaRegistry.BALLISTA.get(), SiegeRenderer::ballista);
        event.registerEntityRenderer(DruzhinaRegistry.RAM.get(), SiegeRenderer::ram);
        event.registerEntityRenderer(DruzhinaRegistry.MANGONEL.get(), SiegeRenderer::mangonel);
        event.registerEntityRenderer(DruzhinaRegistry.BOMBARD.get(), SiegeRenderer::bombard);
        event.registerEntityRenderer(DruzhinaRegistry.SIEGE_TOWER.get(), SiegeRenderer::tower);
        event.registerEntityRenderer(DruzhinaRegistry.MANTLET.get(), SiegeRenderer::mantlet);
        event.registerEntityRenderer(DruzhinaRegistry.SIEGE_STONE.get(), c -> new SiegeStoneRenderer<>(c, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 0.7f));
        event.registerEntityRenderer(DruzhinaRegistry.CANNON_BALL.get(), c -> new SiegeStoneRenderer<>(c, net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 0.42f));
        event.registerEntityRenderer(DruzhinaRegistry.FIRE_POT.get(), c -> new SiegeStoneRenderer<>(c, net.minecraft.world.level.block.Blocks.TERRACOTTA.defaultBlockState(), 0.4f));
        event.registerEntityRenderer(DruzhinaRegistry.BALLISTA_BOLT.get(), BoltRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ElephantModel.LAYER, ElephantMesh::createBodyLayer);
        event.registerLayerDefinition(TrebuchetModel.LAYER, TrebuchetMesh::createBodyLayer);
        event.registerLayerDefinition(BallistaModel.LAYER, BallistaMesh::createBodyLayer);
        event.registerLayerDefinition(RamModel.LAYER, RamMesh::createBodyLayer);
        event.registerLayerDefinition(MangonelModel.LAYER, MangonelMesh::createBodyLayer);
        event.registerLayerDefinition(BombardModel.LAYER, BombardMesh::createBodyLayer);
        event.registerLayerDefinition(SiegeTowerModel.LAYER, SiegeTowerMesh::createBodyLayer);
        event.registerLayerDefinition(MantletModel.LAYER, MantletMesh::createBodyLayer);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(DruzhinaRegistry.SOLDIER_MENU.get(), SoldierScreen::new));
    }
}
