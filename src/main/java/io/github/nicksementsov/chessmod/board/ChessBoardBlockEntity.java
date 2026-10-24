package io.github.nicksementsov.chessmod.board;

import io.github.nicksementsov.chessmod.ChessConfig;
import io.github.nicksementsov.chessmod.ChessMod;
import io.github.nicksementsov.chessmod.chess.ChessAI;
import io.github.nicksementsov.chessmod.chess.ChessGame;
import io.github.nicksementsov.chessmod.chess.GameStatus;
import io.github.nicksementsov.chessmod.chess.Move;
import io.github.nicksementsov.chessmod.chess.Position;
import io.github.nicksementsov.chessmod.chess.Side;
import io.github.nicksementsov.chessmod.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Holds the game played on a chess board. The server is the authority: clients send moves as
 * {@link BoardAction}s, the server checks and plays them and syncs the board to everyone nearby.
 */
public class ChessBoardBlockEntity extends BlockEntity {
    /** All computer opponents share one background thread so a server with many boards stays responsive. */
    private static final ExecutorService COMPUTER_THREAD = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Chess computer opponent");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private static final long EASY_THINK_TIME_MS = 400;
    private static final long MEDIUM_THINK_TIME_MS = 1500;
    private static final double ANNOUNCE_RADIUS = 16;
    public static final long MOVE_ANIMATION_MS = 260;

    private ChessGame game = new ChessGame();
    private GameMode mode = GameMode.TWO_PLAYERS;
    private Side computerSide = Side.BLACK;
    private ChessAI.Difficulty difficulty = ChessAI.Difficulty.MEDIUM;
    private final UUID[] playerIds = new UUID[2];
    private final String[] playerNames = {"", ""};
    /** Changes whenever a new game starts. */
    private int gameId;
    /** Changes whenever the game is replaced or moves are taken back, so stale computer moves are ignored. */
    private int revision;

    private boolean computerThinking;
    private int ticksSinceMove;

    @Nullable
    private Move animatedMove;
    private long animationStart;

    public ChessBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHESS_BOARD.get(), pos, state);
    }

    public static String encodeNewGame(GameMode mode, ColorChoice color, ChessAI.Difficulty difficulty) {
        return mode.serializedName() + ";" + color.serializedName() + ";" + difficulty.serializedName();
    }

    public ChessGame game() {
        return game;
    }

    public GameMode mode() {
        return mode;
    }

    public Side computerSide() {
        return computerSide;
    }

    public ChessAI.Difficulty difficulty() {
        return difficulty;
    }

    @Nullable
    public UUID playerId(Side side) {
        return playerIds[side.ordinal()];
    }

    public String playerName(Side side) {
        return playerNames[side.ordinal()];
    }

    public int gameId() {
        return gameId;
    }

    public boolean isComputer(Side side) {
        return mode == GameMode.VS_COMPUTER && side == computerSide;
    }

    public boolean isComputerToMove() {
        return !game.isOver() && isComputer(game.sideToMove());
    }

    public boolean isParticipant(UUID player) {
        return player.equals(playerIds[0]) || player.equals(playerIds[1]);
    }

    /** The move being animated on the client and how far along it is, from 0 to 1. */
    @Nullable
    public Move animatedMove() {
        return animationProgress() < 1 ? animatedMove : null;
    }

    public float animationProgress() {
        if (animatedMove == null) {
            return 1;
        }
        return Math.min(1, (Util.getMillis() - animationStart) / (float) MOVE_ANIMATION_MS);
    }

    // ---------------------------------------------------------------- server logic

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChessBoardBlockEntity board) {
        board.ticksSinceMove++;
        if (board.isComputerToMove() && !board.computerThinking
                && board.ticksSinceMove >= ChessConfig.COMPUTER_MOVE_DELAY_TICKS.get()) {
            board.startComputerMove((ServerLevel) level);
        }
    }

    private void startComputerMove(ServerLevel level) {
        computerThinking = true;
        int expectedRevision = revision;
        int expectedPly = game.ply();
        Position position = game.position().copy();
        Set<Long> history = game.previousPositionHashes();
        ChessAI.Difficulty chosenDifficulty = difficulty;
        long thinkTime = switch (difficulty) {
            case EASY -> EASY_THINK_TIME_MS;
            case MEDIUM -> MEDIUM_THINK_TIME_MS;
            case HARD -> ChessConfig.HARD_THINK_TIME_MS.get();
        };
        Random random = new Random(level.getRandom().nextLong());
        MinecraftServer server = level.getServer();

        CompletableFuture.supplyAsync(() -> ChessAI.chooseMove(position, chosenDifficulty, history, thinkTime, random), COMPUTER_THREAD)
                .whenComplete((move, error) -> server.execute(() -> {
                    computerThinking = false;
                    if (isRemoved() || revision != expectedRevision || game.ply() != expectedPly || game.isOver()) {
                        return;
                    }
                    Move chosen = move == null ? null : game.findMove(move.uci());
                    if (error != null || chosen == null) {
                        ChessMod.LOGGER.error("Chess computer failed to pick a move at {}", worldPosition, error);
                        List<Move> legal = game.legalMoves();
                        chosen = legal.get(random.nextInt(legal.size()));
                    }
                    playMove(chosen);
                }));
    }

    public void handleAction(ServerPlayer player, BoardAction action, String data) {
        switch (action) {
            case MOVE -> tryPlayerMove(player, data);
            case NEW_GAME -> tryNewGame(player, data);
            case UNDO -> tryUndo(player);
            case RESIGN -> tryResign(player);
        }
    }

    private void tryPlayerMove(ServerPlayer player, String uci) {
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        Side side = game.sideToMove();
        if (isComputer(side)) {
            deny(player, "message.chessmod.computer_turn");
            return;
        }
        UUID seat = playerIds[side.ordinal()];
        if (seat != null && !seat.equals(player.getUUID())) {
            deny(player, "message.chessmod.seat_taken." + side.serializedName(), playerNames[side.ordinal()]);
            return;
        }
        Move move = game.findMove(uci);
        if (move == null) {
            deny(player, "message.chessmod.illegal_move");
            return;
        }
        if (seat == null) {
            playerIds[side.ordinal()] = player.getUUID();
            playerNames[side.ordinal()] = player.getGameProfile().getName();
        }
        playMove(move);
    }

    private void tryNewGame(ServerPlayer player, String data) {
        boolean gameInProgress = game.ply() > 0 && !game.isOver();
        boolean seatsTaken = playerIds[0] != null || playerIds[1] != null;
        if (gameInProgress && seatsTaken && !isParticipant(player.getUUID()) && !player.hasPermissions(2)) {
            deny(player, "message.chessmod.busy");
            return;
        }
        String[] parts = data.split(";");
        GameMode newMode = GameMode.byName(parts.length > 0 ? parts[0] : "", GameMode.TWO_PLAYERS);
        ColorChoice color = ColorChoice.byName(parts.length > 1 ? parts[1] : "", ColorChoice.WHITE);
        ChessAI.Difficulty newDifficulty = ChessAI.Difficulty.byName(parts.length > 2 ? parts[2] : "", ChessAI.Difficulty.MEDIUM);

        game = new ChessGame();
        gameId++;
        revision++;
        mode = newMode;
        difficulty = newDifficulty;
        Arrays.fill(playerIds, null);
        Arrays.fill(playerNames, "");
        if (mode == GameMode.VS_COMPUTER) {
            Side human = color.resolve(player.getRandom());
            computerSide = human.opposite();
            playerIds[human.ordinal()] = player.getUUID();
            playerNames[human.ordinal()] = player.getGameProfile().getName();
        }
        ticksSinceMove = 0;
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
        sync();
    }

    private void tryUndo(ServerPlayer player) {
        if (!mayControl(player)) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        if (humanMovesPlayed() == 0 || game.status() == GameStatus.RESIGNATION) {
            deny(player, "message.chessmod.cannot_undo");
            return;
        }
        game.undo();
        if (mode == GameMode.VS_COMPUTER) {
            // Take back the computer's answer too, so it is the player's turn again.
            Side human = computerSide.opposite();
            while (game.ply() > 0 && game.sideToMove() != human) {
                game.undo();
            }
        }
        revision++;
        ticksSinceMove = 0;
        playSound(SoundEvents.WOOD_HIT, 0.6f, 1.4f);
        sync();
    }

    private void tryResign(ServerPlayer player) {
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        UUID id = player.getUUID();
        Side side;
        if (mode == GameMode.VS_COMPUTER) {
            side = computerSide.opposite();
        } else if (id.equals(playerIds[0]) && id.equals(playerIds[1])) {
            side = game.sideToMove();
        } else if (id.equals(playerIds[0])) {
            side = Side.WHITE;
        } else if (id.equals(playerIds[1])) {
            side = Side.BLACK;
        } else {
            side = null;
        }
        if (side == null || !id.equals(playerIds[side.ordinal()])) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        game.resign(side);
        onGameOver();
        sync();
    }

    /** Against the computer only the player's own moves can be taken back; otherwise any move. */
    private int humanMovesPlayed() {
        if (mode != GameMode.VS_COMPUTER) {
            return game.ply();
        }
        return computerSide == Side.BLACK ? (game.ply() + 1) / 2 : game.ply() / 2;
    }

    /** Players of the current game may control it; so may anyone when nobody has taken a seat yet. */
    private boolean mayControl(ServerPlayer player) {
        boolean seatsTaken = playerIds[0] != null || playerIds[1] != null;
        return !seatsTaken || isParticipant(player.getUUID()) || player.hasPermissions(2);
    }

    private void playMove(Move move) {
        game.play(move);
        ticksSinceMove = 0;
        if (move.isCapture()) {
            playSound(SoundEvents.WOOD_HIT, 0.9f, 0.85f);
        } else {
            playSound(SoundEvents.WOOD_PLACE, 0.8f, 1.25f);
        }
        if (game.isOver()) {
            onGameOver();
        } else if (game.position().inCheck()) {
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.6f);
        }
        sync();
    }

    private void onGameOver() {
        playSound(game.winner() != null ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
        if (level instanceof ServerLevel serverLevel && ChessConfig.ANNOUNCE_RESULTS.get()) {
            Component message = Component.translatable("message.chessmod.prefix").withStyle(ChatFormatting.GOLD)
                    .append(" ").append(ChessTexts.result(game, this::winnerName).withStyle(ChatFormatting.WHITE));
            Vec3 centre = Vec3.atCenterOf(worldPosition);
            for (ServerPlayer player : serverLevel.players()) {
                if (player.position().distanceToSqr(centre) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    /** Name of the winner for messages, or {@code null} for the computer and empty seats. */
    @Nullable
    private String winnerName(Side side) {
        String name = playerNames[side.ordinal()];
        return isComputer(side) || name.isEmpty() ? null : name;
    }

    private void playSound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    private static void deny(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    // ---------------------------------------------------------------- saving and syncing

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeGame(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readGame(tag);
    }

    private void writeGame(CompoundTag tag) {
        tag.putString("StartFen", game.startFen());
        tag.putString("Moves", String.join(" ", game.uciMoves()));
        if (game.resignedSide() != null) {
            tag.putString("Resigned", game.resignedSide().serializedName());
        }
        tag.putString("Mode", mode.serializedName());
        tag.putString("Computer", computerSide.serializedName());
        tag.putString("Difficulty", difficulty.serializedName());
        tag.putInt("GameId", gameId);
        for (Side side : Side.values()) {
            UUID id = playerIds[side.ordinal()];
            if (id != null) {
                String prefix = side == Side.WHITE ? "White" : "Black";
                tag.putUUID(prefix + "Id", id);
                tag.putString(prefix + "Name", playerNames[side.ordinal()]);
            }
        }
    }

    private void readGame(CompoundTag tag) {
        String moves = tag.getString("Moves").trim();
        Side resigned = tag.contains("Resigned") ? Side.byName(tag.getString("Resigned"), null) : null;
        String startFen = tag.contains("StartFen") ? tag.getString("StartFen") : Position.START_FEN;
        game = ChessGame.fromHistory(startFen, moves.isEmpty() ? List.of() : List.of(moves.split(" ")), resigned);
        mode = GameMode.byName(tag.getString("Mode"), GameMode.TWO_PLAYERS);
        computerSide = Side.byName(tag.getString("Computer"), Side.BLACK);
        difficulty = ChessAI.Difficulty.byName(tag.getString("Difficulty"), ChessAI.Difficulty.MEDIUM);
        gameId = tag.getInt("GameId");
        for (Side side : Side.values()) {
            String prefix = side == Side.WHITE ? "White" : "Black";
            boolean seated = tag.hasUUID(prefix + "Id");
            playerIds[side.ordinal()] = seated ? tag.getUUID(prefix + "Id") : null;
            playerNames[side.ordinal()] = seated ? tag.getString(prefix + "Name") : "";
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        writeGame(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag == null) {
            return;
        }
        int oldGameId = gameId;
        int oldPly = game.ply();
        readGame(tag);
        if (gameId == oldGameId && game.ply() == oldPly + 1) {
            animatedMove = game.lastMove();
            animationStart = Util.getMillis();
        } else if (gameId != oldGameId || game.ply() != oldPly) {
            animatedMove = null;
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The pieces stand on top of the thin board, so the default (collision box) would cull them too early.
        return new AABB(worldPosition, worldPosition.offset(1, 1, 1));
    }
}
