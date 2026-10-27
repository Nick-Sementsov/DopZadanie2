package io.github.nicksementsov.chessmod.client;

import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.registry.ModBlockEntities;
import io.github.nicksementsov.chessmod.registry.ModBlocks;
import io.github.nicksementsov.chessmod.style.ChessStyle;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ChessMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CHESS_BOARD.get(), ChessBoardRenderer::new);
    }

    /** Lets item models show the style an item will place (see the overrides in the item model files). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation property = ChessMod.id("style");
            ClampedItemPropertyFunction style = (stack, level, entity, seed) -> ChessStyle.fromStack(stack).ordinal() / 10f;
            ItemProperties.register(ModBlocks.CHESS_BOARD_ITEM.get(), property, style);
            for (Side side : Side.values()) {
                for (PieceType type : PieceType.values()) {
                    ItemProperties.register(ModBlocks.pieceItem(side, type).get(), property, style);
                }
            }
        });
    }
}
