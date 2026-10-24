package io.github.nicksementsov.chessmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nicksementsov.chessmod.board.BoardAction;
import io.github.nicksementsov.chessmod.board.ChessBoardBlockEntity;
import io.github.nicksementsov.chessmod.board.ChessTexts;
import io.github.nicksementsov.chessmod.board.ColorChoice;
import io.github.nicksementsov.chessmod.board.GameMode;
import io.github.nicksementsov.chessmod.chess.ChessAI;
import io.github.nicksementsov.chessmod.network.ChessActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Lets the player choose how the next game is played. */
public class NewGameScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 140;

    // Remembered for the rest of the session, so starting the next game is a single click.
    private static GameMode lastMode = GameMode.VS_COMPUTER;
    private static ColorChoice lastColor = ColorChoice.WHITE;
    private static ChessAI.Difficulty lastDifficulty = ChessAI.Difficulty.MEDIUM;

    private final Screen parent;
    private final BlockPos pos;
    private int left;
    private int top;
    private CycleButton<ColorChoice> colorButton;
    private CycleButton<ChessAI.Difficulty> difficultyButton;

    public NewGameScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.chessmod.new_game.title"));
        this.parent = parent;
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + 15;
        int buttonWidth = WIDTH - 30;

        addRenderableWidget(CycleButton.<GameMode>builder(mode -> Component.translatable("gui.chessmod.mode." + mode.serializedName()))
                .withValues(GameMode.values())
                .withInitialValue(lastMode)
                .create(x, top + 30, buttonWidth, 20, Component.translatable("gui.chessmod.option.mode"), (button, mode) -> {
                    lastMode = mode;
                    updateActive();
                }));
        colorButton = addRenderableWidget(CycleButton.<ColorChoice>builder(color -> Component.translatable("gui.chessmod.color." + color.serializedName()))
                .withValues(ColorChoice.values())
                .withInitialValue(lastColor)
                .create(x, top + 54, buttonWidth, 20, Component.translatable("gui.chessmod.option.color"),
                        (button, color) -> lastColor = color));
        difficultyButton = addRenderableWidget(CycleButton.<ChessAI.Difficulty>builder(ChessTexts::difficultyName)
                .withValues(ChessAI.Difficulty.values())
                .withInitialValue(lastDifficulty)
                .create(x, top + 78, buttonWidth, 20, Component.translatable("gui.chessmod.option.difficulty"),
                        (button, difficulty) -> lastDifficulty = difficulty));

        int half = (buttonWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.start"), button -> start())
                .bounds(x, top + 108, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.chessmod.button.back"), button -> onClose())
                .bounds(x + half + 4, top + 108, half, 20).build());
        updateActive();
    }

    private void updateActive() {
        boolean computer = lastMode == GameMode.VS_COMPUTER;
        colorButton.active = computer;
        difficultyButton.active = computer;
    }

    private void start() {
        ChessActionPacket.send(pos, BoardAction.NEW_GAME, ChessBoardBlockEntity.encodeNewGame(lastMode, lastColor, lastDifficulty));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        RenderSystem.enableBlend();
        GuiUtil.frame(graphics, left, top, WIDTH, HEIGHT);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + 12, GuiUtil.TEXT_GOLD);
        super.render(graphics, mouseX, mouseY, partialTick);
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
