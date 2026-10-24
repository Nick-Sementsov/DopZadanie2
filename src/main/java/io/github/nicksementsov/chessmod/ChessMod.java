package io.github.nicksementsov.chessmod;

import com.mojang.logging.LogUtils;
import io.github.nicksementsov.chessmod.network.ChessNetwork;
import io.github.nicksementsov.chessmod.registry.ModBlockEntities;
import io.github.nicksementsov.chessmod.registry.ModBlocks;
import io.github.nicksementsov.chessmod.registry.ModCreativeTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ChessMod.MODID)
public class ChessMod {
    public static final String MODID = "chessmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChessMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        context.registerConfig(ModConfig.Type.SERVER, ChessConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ChessNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
