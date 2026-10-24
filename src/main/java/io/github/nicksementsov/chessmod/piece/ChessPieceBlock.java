package io.github.nicksementsov.chessmod.piece;

import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative, block-sized chess piece. The same models are drawn in miniature on the chess board,
 * so the board and a life-size chess set built from these blocks look alike.
 */
public class ChessPieceBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape BASE = Block.box(4, 0, 4, 12, 2.5, 12);

    private final Side side;
    private final PieceType type;
    private final VoxelShape shape;

    public ChessPieceBlock(Properties properties, Side side, PieceType type) {
        super(properties);
        this.side = side;
        this.type = type;
        this.shape = Shapes.or(BASE, bodyShape(type));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape bodyShape(PieceType type) {
        return switch (type) {
            case PAWN -> Block.box(5.5, 2.5, 5.5, 10.5, 10.5, 10.5);
            case KNIGHT -> Block.box(5, 2.5, 4, 11, 14.5, 11.5);
            case BISHOP -> Block.box(5.5, 2.5, 5.5, 10.5, 14.5, 10.5);
            case ROOK -> Block.box(4.5, 2.5, 4.5, 11.5, 12.5, 11.5);
            case QUEEN -> Block.box(5, 2.5, 5, 11, 15.5, 11);
            case KING -> Block.box(5, 2.5, 5, 11, 16, 11);
        };
    }

    public Side side() {
        return side;
    }

    public PieceType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player who places the piece, like a statue presented to its builder.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
