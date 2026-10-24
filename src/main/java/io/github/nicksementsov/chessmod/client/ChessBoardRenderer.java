package io.github.nicksementsov.chessmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.board.ChessBoardBlock;
import io.github.nicksementsov.chessmod.board.ChessBoardBlockEntity;
import io.github.nicksementsov.chessmod.chess.ChessGame;
import io.github.nicksementsov.chessmod.chess.Move;
import io.github.nicksementsov.chessmod.chess.Piece;
import io.github.nicksementsov.chessmod.chess.Position;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.chess.Square;
import io.github.nicksementsov.chessmod.piece.ChessPieceBlock;
import io.github.nicksementsov.chessmod.registry.ModBlocks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the pieces of the current position on top of the board block, using the piece statue
 * models in miniature, plus highlights for the last move and a king in check. Moves are animated.
 */
public class ChessBoardRenderer implements BlockEntityRenderer<ChessBoardBlockEntity> {
    private static final ResourceLocation HIGHLIGHT = ChessMod.id("textures/misc/board_highlight.png");
    private static final float BOARD_TOP = 2f / 16f;
    private static final float SQUARE = 1f / 8f;
    private static final float PIECE_SCALE = 0.19f;
    private static final float HOP_HEIGHT = 0.07f;
    private static final int LAST_MOVE_COLOR = 0x70F5D547;
    private static final int CHECK_COLOR = 0xA0F03028;

    private final BlockRenderDispatcher blockRenderer;

    public ChessBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ChessBoardBlockEntity board, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ChessGame game = board.game();
        Position position = game.position();
        Direction facing = board.getBlockState().getValue(ChessBoardBlock.FACING);

        poseStack.pushPose();
        // In board space white sits at +Z (south); turn the board so white sits on the FACING side.
        poseStack.translate(0.5, 0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, 0, -0.5);

        VertexConsumer highlights = buffers.getBuffer(RenderType.entityTranslucent(HIGHLIGHT));
        Move last = game.lastMove();
        if (last != null) {
            highlightSquare(poseStack, highlights, last.from(), LAST_MOVE_COLOR, packedLight);
            highlightSquare(poseStack, highlights, last.to(), LAST_MOVE_COLOR, packedLight);
        }
        if (position.inCheck()) {
            highlightSquare(poseStack, highlights, position.kingSquare(position.sideToMove()), CHECK_COLOR, packedLight);
        }

        Move animated = board.animatedMove();
        float progress = smooth(board.animationProgress());
        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.EMPTY) {
                continue;
            }
            float x = centreX(square);
            float z = centreZ(square);
            float y = BOARD_TOP;
            if (animated != null) {
                int from = square == animated.to() ? animated.from()
                        : square == animated.castleRookTo() ? animated.castleRookFrom() : Square.NONE;
                if (from != Square.NONE) {
                    x = Mth.lerp(progress, centreX(from), x);
                    z = Mth.lerp(progress, centreZ(from), z);
                    y += Mth.sin(progress * Mth.PI) * HOP_HEIGHT;
                }
            }
            renderPiece(piece, x, y, z, poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }

    private void renderPiece(int piece, float x, float y, float z, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Side side = Piece.side(piece);
        ChessPieceBlock block = ModBlocks.piece(side, Piece.type(piece)).get();
        // Pieces face the opponent: white looks towards rank 8 (north in board space).
        BlockState state = block.defaultBlockState().setValue(ChessPieceBlock.FACING, side == Side.WHITE ? Direction.NORTH : Direction.SOUTH);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(PIECE_SCALE, PIECE_SCALE, PIECE_SCALE);
        poseStack.translate(-0.5, 0, -0.5);
        blockRenderer.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
    }

    private static void highlightSquare(PoseStack poseStack, VertexConsumer consumer, int square, int argb, int light) {
        float x0 = Square.file(square) * SQUARE;
        float z0 = (7 - Square.rank(square)) * SQUARE;
        float x1 = x0 + SQUARE;
        float z1 = z0 + SQUARE;
        float y = BOARD_TOP + 0.002f;
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(consumer, pose, normal, x0, y, z0, 0, 0, r, g, b, a, light);
        vertex(consumer, pose, normal, x0, y, z1, 0, 1, r, g, b, a, light);
        vertex(consumer, pose, normal, x1, y, z1, 1, 1, r, g, b, a, light);
        vertex(consumer, pose, normal, x1, y, z0, 1, 0, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, int light) {
        consumer.vertex(pose, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normal, 0, 1, 0).endVertex();
    }

    private static float centreX(int square) {
        return (Square.file(square) + 0.5f) * SQUARE;
    }

    private static float centreZ(int square) {
        return (7 - Square.rank(square) + 0.5f) * SQUARE;
    }

    /** Ease in and out, so pieces lift off and land gently. */
    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
