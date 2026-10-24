package io.github.nicksementsov.chessmod.client;

import io.github.nicksementsov.chessmod.ChessMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Locations in the chess GUI texture and small drawing helpers. */
final class GuiUtil {
    static final ResourceLocation TEXTURE = ChessMod.id("textures/gui/chess_board.png");
    static final int TEXTURE_SIZE = 512;

    static final int PANEL_WIDTH = 312;
    static final int PANEL_HEIGHT = 192;

    static final int HIGHLIGHT_V = 200;
    static final int SELECTED_U = 0;
    static final int LAST_MOVE_U = 20;
    static final int MOVE_DOT_U = 40;
    static final int CAPTURE_RING_U = 60;
    static final int CHECK_U = 80;
    static final int HOVER_U = 100;

    static final int WHITE_PIECES_V = 224;
    static final int BLACK_PIECES_V = 240;
    static final int PIECE_SIZE = 16;

    static final int ICON_V = 256;
    static final int ICON_SIZE = 9;

    private static final int FRAME_U = 128;
    private static final int FRAME_V = 200;
    private static final int FRAME_SIZE = 32;
    private static final int FRAME_BORDER = 6;

    static final int TEXT = 0xF3E6CF;
    static final int TEXT_DIM = 0xB09A7C;
    static final int TEXT_GOLD = 0xFFD37A;
    static final int TEXT_RED = 0xFF8A70;
    static final int TEXT_GREEN = 0xA8E888;

    private GuiUtil() {
    }

    static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(TEXTURE, x, y, u, v, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /** Draws a wooden frame of any size, stretched from a nine-slice region of the texture. */
    static void frame(GuiGraphics graphics, int x, int y, int width, int height) {
        int b = FRAME_BORDER;
        int inner = FRAME_SIZE - 2 * b;
        int innerWidth = width - 2 * b;
        int innerHeight = height - 2 * b;
        // corners
        blit(graphics, x, y, FRAME_U, FRAME_V, b, b);
        blit(graphics, x + width - b, y, FRAME_U + FRAME_SIZE - b, FRAME_V, b, b);
        blit(graphics, x, y + height - b, FRAME_U, FRAME_V + FRAME_SIZE - b, b, b);
        blit(graphics, x + width - b, y + height - b, FRAME_U + FRAME_SIZE - b, FRAME_V + FRAME_SIZE - b, b, b);
        // edges and centre, stretched
        stretch(graphics, x + b, y, innerWidth, b, FRAME_U + b, FRAME_V, inner, b);
        stretch(graphics, x + b, y + height - b, innerWidth, b, FRAME_U + b, FRAME_V + FRAME_SIZE - b, inner, b);
        stretch(graphics, x, y + b, b, innerHeight, FRAME_U, FRAME_V + b, b, inner);
        stretch(graphics, x + width - b, y + b, b, innerHeight, FRAME_U + FRAME_SIZE - b, FRAME_V + b, b, inner);
        stretch(graphics, x + b, y + b, innerWidth, innerHeight, FRAME_U + b, FRAME_V + b, inner, inner);
    }

    private static void stretch(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int uWidth, int vHeight) {
        if (width > 0 && height > 0) {
            graphics.blit(TEXTURE, x, y, width, height, u, v, uWidth, vHeight, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
