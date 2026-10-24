package io.github.nicksementsov.chessmod.registry;

import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.board.ChessBoardBlock;
import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.piece.ChessPieceBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ChessMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ChessMod.MODID);

    public static final RegistryObject<ChessBoardBlock> CHESS_BOARD = BLOCKS.register("chess_board",
            () -> new ChessBoardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(1.5f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .ignitedByLava()));
    public static final RegistryObject<Item> CHESS_BOARD_ITEM = ITEMS.register("chess_board",
            () -> new BlockItem(CHESS_BOARD.get(), new Item.Properties()));

    private static final Map<Side, Map<PieceType, RegistryObject<ChessPieceBlock>>> PIECES = new EnumMap<>(Side.class);
    private static final Map<Side, Map<PieceType, RegistryObject<Item>>> PIECE_ITEMS = new EnumMap<>(Side.class);

    static {
        for (Side side : Side.values()) {
            Map<PieceType, RegistryObject<ChessPieceBlock>> blocks = new EnumMap<>(PieceType.class);
            Map<PieceType, RegistryObject<Item>> items = new EnumMap<>(PieceType.class);
            for (PieceType type : PieceType.values()) {
                String name = side.serializedName() + "_" + type.serializedName();
                RegistryObject<ChessPieceBlock> block = BLOCKS.register(name, () -> new ChessPieceBlock(pieceProperties(side), side, type));
                blocks.put(type, block);
                items.put(type, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
            }
            PIECES.put(side, blocks);
            PIECE_ITEMS.put(side, items);
        }
    }

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties pieceProperties(Side side) {
        return BlockBehaviour.Properties.of()
                .mapColor(side == Side.WHITE ? MapColor.QUARTZ : MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(0.8f)
                .sound(SoundType.STONE)
                .noOcclusion();
    }

    public static RegistryObject<ChessPieceBlock> piece(Side side, PieceType type) {
        return PIECES.get(side).get(type);
    }

    public static RegistryObject<Item> pieceItem(Side side, PieceType type) {
        return PIECE_ITEMS.get(side).get(type);
    }
}
