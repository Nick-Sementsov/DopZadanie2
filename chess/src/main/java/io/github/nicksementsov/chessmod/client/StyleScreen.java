package io.github.nicksementsov.chessmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nicksementsov.chessmod.board.BoardAction;
import io.github.nicksementsov.chessmod.board.ChessBoardBlock;
import io.github.nicksementsov.chessmod.chess.Piece;
import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.network.ChessActionPacket;
import io.github.nicksementsov.chessmod.style.ChessStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.nicksementsov.chessmod.client.GuiUtil.*;

/** Shows every style as a small corner of its board with a few pieces, and applies the one the player picks. */
public class StyleScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int CARD_WIDTH = 64;
    private static final int CARD_HEIGHT = 74;
    private static final int GAP = 6;
    private static final int PREVIEW = 60;
    private static final int WIDTH = COLUMNS * CARD_WIDTH + (COLUMNS - 1) * GAP + 24;
    private static final int HEIGHT = 210;
    private static final int SQUARE = 20;

    private final Screen parent;
    private final BlockPos pos;
    private int left;
    private int top;

    public StyleScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.chessmod.style.title"));
        this.parent = parent;
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.back"), button -> onClose())
                .bounds(left + WIDTH / 2 - 50, top + 182, 100, 20).build());
    }

    private ChessStyle currentStyle() {
        BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(pos);
        return state != null && state.hasProperty(ChessBoardBlock.STYLE) ? state.getValue(ChessBoardBlock.STYLE) : ChessStyle.CLASSIC;
    }

    private int cardX(int index) {
        return left + 12 + (index % COLUMNS) * (CARD_WIDTH + GAP);
    }

    private int cardY(int index) {
        return top + 22 + (index / COLUMNS) * (CARD_HEIGHT + GAP);
    }

    private int cardAt(double mouseX, double mouseY) {
        ChessStyle[] styles = ChessStyle.values();
        for (int i = 0; i < styles.length; i++) {
            int x = cardX(i);
            int y = cardY(i);
            if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        RenderSystem.enableBlend();
        frame(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 9, TEXT_GOLD);

        ChessStyle current = currentStyle();
        int hovered = cardAt(mouseX, mouseY);
        ChessStyle[] styles = ChessStyle.values();
        for (int i = 0; i < styles.length; i++) {
            renderCard(graphics, styles[i], cardX(i), cardY(i), styles[i] == current, i == hovered);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCard(GuiGraphics graphics, ChessStyle style, int x, int y, boolean selected, boolean hovered) {
        int border = selected ? 0xFFFFD37A : hovered ? 0xC0FFFFFF : 0xFF1A100A;
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xFF24170E);
        graphics.fill(x, y, x + CARD_WIDTH, y + 1, border);
        graphics.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, border);
        graphics.fill(x, y, x + 1, y + CARD_HEIGHT, border);
        graphics.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, border);

        // The a8-c6 corner of the board with a few pieces on light and dark squares.
        int px = x + 2;
        int py = y + 2;
        graphics.blit(boardTexture(style), px, py, 0, 0, PREVIEW, PREVIEW, STYLE_BOARD_SIZE, STYLE_BOARD_SIZE);
        blitPiece(graphics, style, Piece.of(Side.BLACK, PieceType.KING), px + 2, py + 2);
        blitPiece(graphics, style, Piece.of(Side.BLACK, PieceType.PAWN), px + SQUARE + 2, py + 2);
        blitPiece(graphics, style, Piece.of(Side.WHITE, PieceType.PAWN), px + SQUARE + 2, py + 2 * SQUARE + 2);
        blitPiece(graphics, style, Piece.of(Side.WHITE, PieceType.QUEEN), px + 2 * SQUARE + 2, py + 2 * SQUARE + 2);

        graphics.drawCenteredString(font, style.displayName(), x + CARD_WIDTH / 2, y + PREVIEW + 4, selected ? TEXT_GOLD : TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = cardAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            ChessStyle style = ChessStyle.values()[index];
            if (style != currentStyle()) {
                ChessActionPacket.send(pos, BoardAction.SET_STYLE, style.getSerializedName());
            }
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
