package io.github.nicksementsov.chessmod.registry;

import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.board.ChessBoardBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ChessMod.MODID);

    @SuppressWarnings("DataFlowIssue") // the data fixer type may be null for mod block entities
    public static final RegistryObject<BlockEntityType<ChessBoardBlockEntity>> CHESS_BOARD = BLOCK_ENTITIES.register("chess_board",
            () -> BlockEntityType.Builder.of(ChessBoardBlockEntity::new, ModBlocks.CHESS_BOARD.get()).build(null));

    private ModBlockEntities() {
    }
}
