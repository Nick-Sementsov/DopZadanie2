package io.github.nicksementsov.chessmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nicksementsov.chessmod.board.BoardAction;
import io.github.nicksementsov.chessmod.board.ChessBoardBlockEntity;
import io.github.nicksementsov.chessmod.board.ChessTexts;
import io.github.nicksementsov.chessmod.board.GameMode;
import io.github.nicksementsov.chessmod.chess.ChessGame;
import io.github.nicksementsov.chessmod.chess.GameStatus;
import io.github.nicksementsov.chessmod.chess.Move;
import io.github.nicksementsov.chessmod.chess.Piece;
import io.github.nicksementsov.chessmod.chess.PieceType;
import io.github.nicksementsov.chessmod.chess.Position;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.chess.Square;
import io.github.nicksementsov.chessmod.network.ChessActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

import static io.github.nicksementsov.chessmod.client.GuiUtil.*;

/** The chess board seen from above: play by clicking or dragging pieces. */
public class ChessScreen extends Screen {
    private static final int BOARD_X = 16;
    private static final int BOARD_Y = 16;
    private static final int SQUARE = 20;
    private static final int BOARD_SIZE = SQUARE * 8;
    private static final int SIDE_X = 192;
    private static final int SIDE_WIDTH = 114;
    private static final int HISTORY_Y = 70;
    private static final int HISTORY_HEIGHT = 58;
    private static final int HISTORY_ROW = 9;
    private static final int HISTORY_ROWS = (HISTORY_HEIGHT - 4) / HISTORY_ROW;
    private static final double MAX_DISTANCE_SQR = 8 * 8;
    private static final long FLASH_MS = 2500;
    private static final long RESIGN_CONFIRM_MS = 3000;
    private static final PieceType[] PROMOTION_CHOICES = {PieceType.QUEEN, PieceType.KNIGHT, PieceType.ROOK, PieceType.BISHOP};

    private final BlockPos pos;
    private int left;
    private int top;

    private boolean flipped;
    private int knownGameId = Integer.MIN_VALUE;
    private int knownPly = -1;
    private int historyScroll;
    private boolean bannerHidden;

    private int selected = Square.NONE;
    private boolean pressedOnPiece;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private int promotionFrom = Square.NONE;
    private int promotionTo = Square.NONE;

    @Nullable
    private Component flash;
    private long flashUntil;
    private long resignArmedUntil;

    private Button undoButton;
    private Button resignButton;

    public ChessScreen(BlockPos pos) {
        super(Component.translatable("gui.chessmod.title"));
        this.pos = pos;
    }

    @Nullable
    private ChessBoardBlockEntity board() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
        return blockEntity instanceof ChessBoardBlockEntity board ? board : null;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        int x = left + SIDE_X;
        addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.new_game"),
                button -> minecraft.setScreen(new NewGameScreen(this, pos))).bounds(x, top + 132, SIDE_WIDTH, 16).build());
        undoButton = addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.undo"),
                button -> ChessActionPacket.send(pos, BoardAction.UNDO, "")).bounds(x, top + 150, 56, 16).build());
        resignButton = addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.resign"),
                button -> resign()).bounds(x + 58, top + 150, 56, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.flip"),
                button -> flipped = !flipped).bounds(x, top + 168, SIDE_WIDTH, 16).build());
        ChessBoardBlockEntity board = board();
        if (board != null) {
            syncWithBoard(board);
        }
    }

    @Override
    public void tick() {
        ChessBoardBlockEntity board = board();
        if (board == null || minecraft.player == null || minecraft.player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            onClose();
            return;
        }
        syncWithBoard(board);
    }

    /** Reacts to changes that arrived from the server: a new game, a move, a take-back. */
    private void syncWithBoard(ChessBoardBlockEntity board) {
        ChessGame game = board.game();
        if (board.gameId() != knownGameId) {
            knownGameId = board.gameId();
            flipped = prefersBlackAtBottom(board);
            bannerHidden = false;
            clearSelection();
        }
        if (game.ply() != knownPly) {
            knownPly = game.ply();
            historyScroll = Math.max(0, historyRows(game) - HISTORY_ROWS);
            if (selected != Square.NONE && !canSelect(board, selected, false)) {
                clearSelection();
            }
            if (promotionFrom != Square.NONE && !game.isPromotion(promotionFrom, promotionTo)) {
                clearSelection();
            }
        }

        UUID me = playerId();
        boolean mayControl = (board.playerId(Side.WHITE) == null && board.playerId(Side.BLACK) == null) || board.isParticipant(me);
        int humanMoves = board.mode() == GameMode.VS_COMPUTER
                ? (board.computerSide() == Side.BLACK ? (game.ply() + 1) / 2 : game.ply() / 2)
                : game.ply();
        undoButton.active = mayControl && humanMoves > 0 && game.status() != GameStatus.RESIGNATION;
        resignButton.active = !game.isOver() && board.isParticipant(me);
        boolean armed = Util.getMillis() < resignArmedUntil;
        resignButton.setMessage(Component.translatable(armed ? "gui.chessmod.button.resign_confirm" : "gui.chessmod.button.resign")
                .withStyle(armed ? ChatFormatting.RED : ChatFormatting.RESET));
    }

    private boolean prefersBlackAtBottom(ChessBoardBlockEntity board) {
        if (board.mode() == GameMode.VS_COMPUTER) {
            return board.computerSide() == Side.WHITE;
        }
        UUID me = playerId();
        return me.equals(board.playerId(Side.BLACK)) && !me.equals(board.playerId(Side.WHITE));
    }

    private void resign() {
        if (Util.getMillis() < resignArmedUntil) {
            resignArmedUntil = 0;
            ChessActionPacket.send(pos, BoardAction.RESIGN, "");
        } else {
            resignArmedUntil = Util.getMillis() + RESIGN_CONFIRM_MS;
        }
    }

    private UUID playerId() {
        return minecraft.player.getUUID();
    }

    // ---------------------------------------------------------------- board geometry

    private int squareX(int square) {
        int column = flipped ? 7 - Square.file(square) : Square.file(square);
        return left + BOARD_X + column * SQUARE;
    }

    private int squareY(int square) {
        int row = flipped ? Square.rank(square) : 7 - Square.rank(square);
        return top + BOARD_Y + row * SQUARE;
    }

    private int squareAt(double mouseX, double mouseY) {
        int column = Mth.floor((mouseX - left - BOARD_X) / SQUARE);
        int row = Mth.floor((mouseY - top - BOARD_Y) / SQUARE);
        if (column < 0 || column > 7 || row < 0 || row > 7) {
            return Square.NONE;
        }
        return flipped ? Square.of(7 - column, row) : Square.of(column, 7 - row);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        ChessBoardBlockEntity board = board();
        if (board != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            blit(graphics, left, top, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);
            renderCoordinates(graphics);
            renderBoard(graphics, board, mouseX, mouseY);
            renderSidePanel(graphics, board);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (board != null) {
            renderPromotionChoice(graphics, board, mouseX, mouseY);
        }
    }

    private void renderCoordinates(GuiGraphics graphics) {
        for (int i = 0; i < 8; i++) {
            String file = String.valueOf((char) ('a' + (flipped ? 7 - i : i)));
            String rank = String.valueOf((char) ('1' + (flipped ? i : 7 - i)));
            int columnCentre = left + BOARD_X + i * SQUARE + SQUARE / 2;
            graphics.drawString(font, file, columnCentre - font.width(file) / 2, top + BOARD_Y + BOARD_SIZE + 1, TEXT_DIM, false);
            graphics.drawString(font, rank, left + 11 - font.width(rank) / 2, top + BOARD_Y + i * SQUARE + 6, TEXT_DIM, false);
        }
    }

    private void renderBoard(GuiGraphics graphics, ChessBoardBlockEntity board, int mouseX, int mouseY) {
        ChessGame game = board.game();
        Position position = game.position();

        Move last = game.lastMove();
        if (last != null) {
            highlight(graphics, last.from(), LAST_MOVE_U);
            highlight(graphics, last.to(), LAST_MOVE_U);
        }
        if (selected != Square.NONE) {
            highlight(graphics, selected, SELECTED_U);
        }
        int hovered = squareAt(mouseX, mouseY);
        if (hovered != Square.NONE && promotionTo == Square.NONE && hovered != selected
                && (isTarget(game, hovered) || canSelect(board, hovered, false))) {
            highlight(graphics, hovered, HOVER_U);
        }
        if (position.inCheck()) {
            highlight(graphics, position.kingSquare(position.sideToMove()), CHECK_U);
        }

        Move animated = board.animatedMove();
        float progress = board.animationProgress();
        progress = progress * progress * (3 - 2 * progress);
        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.EMPTY || (dragging && square == selected)) {
                continue;
            }
            float x = squareX(square);
            float y = squareY(square);
            if (animated != null) {
                int from = square == animated.to() ? animated.from()
                        : square == animated.castleRookTo() ? animated.castleRookFrom() : Square.NONE;
                if (from != Square.NONE) {
                    x = Mth.lerp(progress, squareX(from), x);
                    y = Mth.lerp(progress, squareY(from), y);
                }
            }
            renderPiece(graphics, piece, x + 2, y + 2);
        }

        if (selected != Square.NONE) {
            boolean[] marked = new boolean[64];
            for (Move move : game.legalMoves()) {
                if (move.from() != selected || marked[move.to()]) {
                    continue;
                }
                marked[move.to()] = true;
                boolean capture = position.pieceAt(move.to()) != Piece.EMPTY || move.isEnPassant();
                highlight(graphics, move.to(), capture ? CAPTURE_RING_U : MOVE_DOT_U);
            }
        }

        if (dragging && selected != Square.NONE) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 50);
            renderPiece(graphics, position.pieceAt(selected), mouseX - PIECE_SIZE / 2f, mouseY - PIECE_SIZE / 2f);
            graphics.pose().popPose();
        }

        if (game.isOver() && !bannerHidden && animated == null) {
            renderResultBanner(graphics, board);
        }
    }

    private void highlight(GuiGraphics graphics, int square, int u) {
        blit(graphics, squareX(square), squareY(square), u, HIGHLIGHT_V, SQUARE, SQUARE);
    }

    private static void renderPiece(GuiGraphics graphics, int piece, float x, float y) {
        int u = Piece.type(piece).ordinal() * PIECE_SIZE;
        int v = Piece.side(piece) == Side.WHITE ? WHITE_PIECES_V : BLACK_PIECES_V;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        blit(graphics, 0, 0, u, v, PIECE_SIZE, PIECE_SIZE);
        graphics.pose().popPose();
    }

    private void renderResultBanner(GuiGraphics graphics, ChessBoardBlockEntity board) {
        ChessGame game = board.game();
        int x0 = left + BOARD_X;
        int x1 = x0 + BOARD_SIZE;
        int centreX = x0 + BOARD_SIZE / 2;
        int centreY = top + BOARD_Y + BOARD_SIZE / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 100);
        graphics.fill(x0, centreY - 24, x1, centreY + 24, 0xD8140E0A);
        graphics.fill(x0, centreY - 24, x1, centreY - 23, 0xFFD8A84A);
        graphics.fill(x0, centreY + 23, x1, centreY + 24, 0xFFD8A84A);

        Component reason = ChessTexts.reason(game);
        float scale = Math.min(2f, (BOARD_SIZE - 12) / (float) Math.max(1, font.width(reason)));
        graphics.pose().pushPose();
        graphics.pose().translate(centreX, centreY - 18, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawCenteredString(font, reason, 0, 0, TEXT_GOLD);
        graphics.pose().popPose();

        Component outcome = ChessTexts.outcome(game, side -> displayName(board, side, false));
        graphics.drawCenteredString(font, outcome, centreX, centreY + 2, TEXT);
        graphics.drawCenteredString(font, Component.translatable("gui.chessmod.banner.hide"), centreX, centreY + 13, TEXT_DIM);
        graphics.pose().popPose();
    }

    private void renderSidePanel(GuiGraphics graphics, ChessBoardBlockEntity board) {
        ChessGame game = board.game();
        int x = left + SIDE_X;
        int centreX = x + SIDE_WIDTH / 2;
        graphics.drawCenteredString(font, title, centreX, top + 10, TEXT_GOLD);

        List<Component> status = statusLines(board);
        for (int i = 0; i < status.size() && i < 2; i++) {
            FormattedCharSequence line = font.split(status.get(i), SIDE_WIDTH).get(0);
            graphics.drawCenteredString(font, line, centreX, top + 24 + i * 10, TEXT);
        }

        renderPlayer(graphics, board, Side.WHITE, top + 48);
        renderPlayer(graphics, board, Side.BLACK, top + 58);
        renderHistory(graphics, game, x, top + HISTORY_Y);
    }

    private List<Component> statusLines(ChessBoardBlockEntity board) {
        ChessGame game = board.game();
        if (game.isOver()) {
            return List.of(ChessTexts.reason(game).withStyle(ChatFormatting.GOLD),
                    ChessTexts.outcome(game, side -> displayName(board, side, false)));
        }
        Side toMove = game.sideToMove();
        MutableComponent first = Component.translatable("gui.chessmod.turn." + toMove.serializedName());
        if (game.position().inCheck()) {
            first.append(" ").append(Component.translatable("gui.chessmod.check").withStyle(ChatFormatting.RED));
        }
        Component second;
        if (flash != null && Util.getMillis() < flashUntil) {
            second = flash;
        } else if (board.isComputer(toMove)) {
            int dots = (int) (Util.getMillis() / 400 % 4);
            second = Component.translatable("gui.chessmod.thinking").append(".".repeat(dots)).withStyle(ChatFormatting.GRAY);
        } else if (mayMove(board, toMove)) {
            second = Component.translatable("gui.chessmod.your_move").withStyle(ChatFormatting.GREEN);
        } else {
            second = Component.translatable("gui.chessmod.waiting_for", board.playerName(toMove)).withStyle(ChatFormatting.GRAY);
        }
        return List.of(first, second);
    }

    private void renderPlayer(GuiGraphics graphics, ChessBoardBlockEntity board, Side side, int y) {
        int x = left + SIDE_X;
        blit(graphics, x + 1, y - 1, side == Side.WHITE ? 0 : ICON_SIZE, ICON_V, ICON_SIZE, ICON_SIZE);
        boolean toMove = !board.game().isOver() && board.game().sideToMove() == side;
        String name = displayName(board, side, true);
        int advantage = Math.round(board.game().materialBalance() / 100f) * (side == Side.WHITE ? 1 : -1);
        String bonus = advantage > 0 ? "+" + advantage : "";
        int nameWidth = SIDE_WIDTH - 14 - font.width(bonus) - 3;
        String shown = font.plainSubstrByWidth(name, nameWidth);
        if (!shown.equals(name)) {
            shown = font.plainSubstrByWidth(name, nameWidth - font.width("…")) + "…";
        }
        boolean empty = board.playerId(side) == null && !board.isComputer(side);
        graphics.drawString(font, shown, x + 13, y, empty ? TEXT_DIM : toMove ? TEXT_GOLD : TEXT, true);
        if (!bonus.isEmpty()) {
            graphics.drawString(font, bonus, x + SIDE_WIDTH - font.width(bonus) - 1, y, TEXT_GREEN, true);
        }
    }

    /** Name of the player on a side; the computer and empty seats get a description instead. */
    @Nullable
    private String displayName(ChessBoardBlockEntity board, Side side, boolean describeEmpty) {
        if (board.isComputer(side)) {
            return I18n.get("gui.chessmod.player.computer." + board.difficulty().serializedName());
        }
        String name = board.playerName(side);
        if (name.isEmpty()) {
            return describeEmpty ? I18n.get("gui.chessmod.player.free") : null;
        }
        return name;
    }

    private void renderHistory(GuiGraphics graphics, ChessGame game, int x, int y) {
        List<String> moves = game.sanMoves();
        if (moves.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.chessmod.no_moves"), x + SIDE_WIDTH / 2,
                    y + HISTORY_HEIGHT / 2 - 4, TEXT_DIM);
            return;
        }
        int rows = historyRows(game);
        historyScroll = Mth.clamp(historyScroll, 0, Math.max(0, rows - HISTORY_ROWS));
        graphics.enableScissor(x + 1, y + 1, x + SIDE_WIDTH - 1, y + HISTORY_HEIGHT - 1);
        for (int row = historyScroll; row < Math.min(rows, historyScroll + HISTORY_ROWS + 1); row++) {
            int rowY = y + 3 + (row - historyScroll) * HISTORY_ROW;
            graphics.drawString(font, (row + 1) + ".", x + 4, rowY, TEXT_DIM, false);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                if (index >= moves.size()) {
                    break;
                }
                int cellX = x + 24 + column * 44;
                if (index == moves.size() - 1) {
                    graphics.fill(cellX - 2, rowY - 1, cellX + 41, rowY + 8, 0x50FFD37A);
                }
                graphics.drawString(font, localizedSan(moves.get(index)), cellX, rowY, TEXT, false);
            }
        }
        graphics.disableScissor();
        if (rows > HISTORY_ROWS) {
            int trackHeight = HISTORY_HEIGHT - 4;
            int thumbHeight = Math.max(6, trackHeight * HISTORY_ROWS / rows);
            int thumbY = y + 2 + (trackHeight - thumbHeight) * historyScroll / Math.max(1, rows - HISTORY_ROWS);
            graphics.fill(x + SIDE_WIDTH - 4, thumbY, x + SIDE_WIDTH - 2, thumbY + thumbHeight, 0xA0D8C3A0);
        }
    }

    private static int historyRows(ChessGame game) {
        return (game.ply() + 1) / 2;
    }

    /** Replaces the English piece letters of SAN with the ones of the current language (e.g. Кр, Ф, Л, С, К). */
    static String localizedSan(String san) {
        if (san.startsWith("O-O")) {
            String castle = I18n.get(san.startsWith("O-O-O") ? "chessmod.notation.castle_long" : "chessmod.notation.castle_short");
            return castle + san.substring(san.startsWith("O-O-O") ? 5 : 3);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < san.length(); i++) {
            char c = san.charAt(i);
            boolean pieceLetter = "KQRBN".indexOf(c) >= 0 && (i == 0 || san.charAt(i - 1) == '=');
            result.append(pieceLetter ? I18n.get("chessmod.notation." + c) : String.valueOf(c));
        }
        return result.toString();
    }

    private void renderPromotionChoice(GuiGraphics graphics, ChessBoardBlockEntity board, int mouseX, int mouseY) {
        if (promotionTo == Square.NONE) {
            return;
        }
        Side side = board.game().sideToMove();
        int x = squareX(promotionTo);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(left + BOARD_X, top + BOARD_Y, left + BOARD_X + BOARD_SIZE, top + BOARD_Y + BOARD_SIZE, 0x80000000);
        int firstY = promotionChoiceY(0);
        int lastY = promotionChoiceY(PROMOTION_CHOICES.length - 1);
        frame(graphics, x - 4, Math.min(firstY, lastY) - 4, SQUARE + 8, SQUARE * PROMOTION_CHOICES.length + 8);
        for (int i = 0; i < PROMOTION_CHOICES.length; i++) {
            int y = promotionChoiceY(i);
            boolean hovered = mouseX >= x && mouseX < x + SQUARE && mouseY >= y && mouseY < y + SQUARE;
            graphics.fill(x, y, x + SQUARE, y + SQUARE, hovered ? 0xFFF5D88E : 0xFFE9D3AB);
            renderPiece(graphics, Piece.of(side, PROMOTION_CHOICES[i]), x + 2, y + 2);
        }
        graphics.pose().popPose();
    }

    /** Promotion choices stack from the promotion square towards the middle of the board. */
    private int promotionChoiceY(int index) {
        int y = squareY(promotionTo);
        boolean atTop = y == top + BOARD_Y;
        return atTop ? y + index * SQUARE : y - index * SQUARE;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ChessBoardBlockEntity board = board();
        if (board != null && promotionTo != Square.NONE) {
            choosePromotion(board, mouseX, mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int square = squareAt(mouseX, mouseY);
        if (board == null || square == Square.NONE) {
            clearSelection();
            return false;
        }
        ChessGame game = board.game();
        if (game.isOver() && !bannerHidden) {
            bannerHidden = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            clearSelection();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (selected != Square.NONE && isTarget(game, square)) {
            attemptMove(board, selected, square);
            return true;
        }
        if (square != selected && canSelect(board, square, true)) {
            selected = square;
            pressedOnPiece = true;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        pressedOnPiece = square == selected;
        if (!pressedOnPiece) {
            clearSelection();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressedOnPiece && selected != Square.NONE) {
            if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
                dragging = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            pressedOnPiece = false;
            ChessBoardBlockEntity board = board();
            int square = squareAt(mouseX, mouseY);
            if (board != null && square != Square.NONE && square != selected && isTarget(board.game(), square)) {
                attemptMove(board, selected, square);
            }
            return true;
        }
        pressedOnPiece = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int x = left + SIDE_X;
        int y = top + HISTORY_Y;
        if (mouseX >= x && mouseX < x + SIDE_WIDTH && mouseY >= y && mouseY < y + HISTORY_HEIGHT) {
            historyScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (promotionTo != Square.NONE || selected != Square.NONE)) {
            clearSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void choosePromotion(ChessBoardBlockEntity board, double mouseX, double mouseY) {
        int x = squareX(promotionTo);
        for (int i = 0; i < PROMOTION_CHOICES.length; i++) {
            int y = promotionChoiceY(i);
            if (mouseX >= x && mouseX < x + SQUARE && mouseY >= y && mouseY < y + SQUARE) {
                Move move = board.game().findMove(promotionFrom, promotionTo, PROMOTION_CHOICES[i].id());
                if (move != null) {
                    ChessActionPacket.send(pos, BoardAction.MOVE, move.uci());
                }
                break;
            }
        }
        clearSelection();
    }

    private void attemptMove(ChessBoardBlockEntity board, int from, int to) {
        ChessGame game = board.game();
        if (game.isPromotion(from, to)) {
            promotionFrom = from;
            promotionTo = to;
            selected = Square.NONE;
            dragging = false;
            return;
        }
        Move move = game.findMove(from, to, 0);
        if (move != null) {
            ChessActionPacket.send(pos, BoardAction.MOVE, move.uci());
        }
        clearSelection();
    }

    private boolean isTarget(ChessGame game, int square) {
        if (selected == Square.NONE) {
            return false;
        }
        for (Move move : game.legalMoves()) {
            if (move.from() == selected && move.to() == square) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player may pick up the piece on a square; optionally explains why not. */
    private boolean canSelect(ChessBoardBlockEntity board, int square, boolean explain) {
        ChessGame game = board.game();
        int piece = game.position().pieceAt(square);
        if (piece == Piece.EMPTY || game.isOver()) {
            return false;
        }
        Side side = Piece.side(piece);
        if (side != game.sideToMove()) {
            if (explain && mayMove(board, side)) {
                flash(Component.translatable("gui.chessmod.not_your_turn").withStyle(ChatFormatting.RED));
            }
            return false;
        }
        if (board.isComputer(side)) {
            if (explain) {
                flash(Component.translatable("gui.chessmod.computer_pieces").withStyle(ChatFormatting.RED));
            }
            return false;
        }
        if (!mayMove(board, side)) {
            if (explain) {
                flash(Component.translatable("gui.chessmod.seat_taken", board.playerName(side)).withStyle(ChatFormatting.RED));
            }
            return false;
        }
        return true;
    }

    private boolean mayMove(ChessBoardBlockEntity board, Side side) {
        if (board.isComputer(side)) {
            return false;
        }
        UUID seat = board.playerId(side);
        return seat == null || seat.equals(playerId());
    }

    private void clearSelection() {
        selected = Square.NONE;
        dragging = false;
        pressedOnPiece = false;
        promotionFrom = Square.NONE;
        promotionTo = Square.NONE;
    }

    private void flash(Component message) {
        flash = message;
        flashUntil = Util.getMillis() + FLASH_MS;
    }

    @Override
    public boolean isPauseScreen() {
        // The computer opponent lives on the integrated server, which must keep running.
        return false;
    }
}
