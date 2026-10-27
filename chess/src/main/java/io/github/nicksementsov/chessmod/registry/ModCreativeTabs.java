package io.github.nicksementsov.chessmod.registry;

import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.style.ChessStyle;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChessMod.MODID);

    public static final RegistryObject<CreativeModeTab> CHESS = CREATIVE_MODE_TABS.register("chess", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chessmod.chess"))
            .icon(() -> new ItemStack(ModBlocks.pieceItem(Side.WHITE, PieceType.KING).get()))
            .displayItems((parameters, output) -> {
                for (ChessStyle style : ChessStyle.values()) {
                    output.accept(style.applyTo(new ItemStack(ModBlocks.CHESS_BOARD_ITEM.get())));
                }
                for (Side side : Side.values()) {
                    for (PieceType type : PieceType.values()) {
                        output.accept(ModBlocks.pieceItem(side, type).get());
                    }
                }
            })
            .build());

    private ModCreativeTabs() {
    }
}
