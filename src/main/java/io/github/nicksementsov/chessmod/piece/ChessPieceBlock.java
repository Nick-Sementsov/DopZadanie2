package io.github.nicksementsov.chessmod.piece;

import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.style.ChessStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A decorative, block-sized chess piece. The same models are drawn in miniature on the chess board,
 * so the board and a life-size chess set built from these blocks look alike.
 */
public class ChessPieceBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<ChessStyle> STYLE = ChessStyle.PROPERTY;
    private static final VoxelShape BASE = Block.box(4, 0, 4, 12, 2.5, 12);

    private final Side side;
    private final PieceType type;
    private final VoxelShape shape;

    public ChessPieceBlock(Properties properties, Side side, PieceType type) {
        super(properties);
        this.side = side;
        this.type = type;
        this.shape = Shapes.or(BASE, bodyShape(type));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STYLE, ChessStyle.CLASSIC));
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
        builder.add(FACING, STYLE);
    }

    /** Right-clicking a statue with an empty hand gives it the next style. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty() || !player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ChessStyle style = state.getValue(STYLE).next();
            level.setBlock(pos, state.setValue(STYLE, style), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("tooltip.chessmod.style", style.displayName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.chessmod.style", ChessStyle.fromStack(stack).displayName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.chessmod.change_style").withStyle(ChatFormatting.GRAY));
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
