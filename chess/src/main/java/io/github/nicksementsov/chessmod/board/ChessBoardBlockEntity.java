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
import io.github.nicksementsov.chessmod.style.ChessStyle;
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
import java.util.HashSet;
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

    private Offer offer = Offer.NONE;
    private Side offerFrom = Side.WHITE;

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

    /** The side a player sits on; when one person plays both sides, the side to move. */
    @Nullable
    public Side sideOf(UUID player) {
        boolean white = player.equals(playerIds[0]);
        boolean black = player.equals(playerIds[1]);
        if (white && black) {
            return game.sideToMove();
        }
        return white ? Side.WHITE : black ? Side.BLACK : null;
    }

    /** True when two different people sit at the board. */
    public boolean hasTwoPlayers() {
        return playerIds[0] != null && playerIds[1] != null && !playerIds[0].equals(playerIds[1]);
    }

    /** The take-back or draw offer waiting for an answer, made by {@link #offerFrom()}. */
    public Offer offer() {
        return offer;
    }

    public Side offerFrom() {
        return offerFrom;
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
                    playMove(chosen, null);
                }));
    }

    public void handleAction(ServerPlayer player, BoardAction action, String data) {
        switch (action) {
            case MOVE -> tryPlayerMove(player, data);
            case NEW_GAME -> tryNewGame(player, data);
            case UNDO -> tryUndo(player);
            case RESIGN -> tryResign(player);
            case SIT -> trySit(player, Side.byName(data, null));
            case OFFER_DRAW -> tryOfferDraw(player);
            case ANSWER_OFFER -> tryAnswerOffer(player, "accept".equals(data));
            case SET_STYLE -> trySetStyle(player, ChessStyle.byName(data, null));
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
        UUID me = player.getUUID();
        UUID seat = playerIds[side.ordinal()];
        if (seat != null && !seat.equals(me)) {
            deny(player, "message.chessmod.seat_taken." + side.serializedName(), playerNames[side.ordinal()]);
            return;
        }
        if (seat == null && mode == GameMode.TWO_PLAYERS && me.equals(playerIds[side.opposite().ordinal()])) {
            deny(player, "message.chessmod.already_seated." + side.opposite().serializedName());
            return;
        }
        Move move = game.findMove(uci);
        if (move == null) {
            deny(player, "message.chessmod.illegal_move");
            return;
        }
        if (seat == null) {
            takeSeat(player, side);
        }
        playMove(move, player);
    }

    private void trySetStyle(ServerPlayer player, @Nullable ChessStyle style) {
        if (style == null || level == null) {
            return;
        }
        if (!mayControl(player)) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        // The block entity stays in place when only a property of the same block changes.
        level.setBlock(worldPosition, getBlockState().setValue(ChessBoardBlock.STYLE, style), Block.UPDATE_ALL);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private void trySit(ServerPlayer player, @Nullable Side side) {
        if (side == null || mode != GameMode.TWO_PLAYERS) {
            return;
        }
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        UUID me = player.getUUID();
        UUID seat = playerIds[side.ordinal()];
        if (me.equals(seat)) {
            return;
        }
        if (seat != null) {
            deny(player, "message.chessmod.seat_taken." + side.serializedName(), playerNames[side.ordinal()]);
            return;
        }
        if (me.equals(playerIds[side.opposite().ordinal()])) {
            deny(player, "message.chessmod.already_seated." + side.opposite().serializedName());
            return;
        }
        takeSeat(player, side);
        playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
        sync();
    }

    private void takeSeat(ServerPlayer player, Side side) {
        String name = player.getGameProfile().getName();
        playerIds[side.ordinal()] = player.getUUID();
        playerNames[side.ordinal()] = name;
        if (mode == GameMode.TWO_PLAYERS) {
            notifyPlayer(playerIds[side.opposite().ordinal()], player,
                    Component.translatable("message.chessmod.joined." + side.serializedName(), name).withStyle(ChatFormatting.GOLD), false);
        }
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

        // Let the previous opponent know the game they were playing has been replaced.
        if (mode == GameMode.TWO_PLAYERS && gameInProgress) {
            for (UUID id : playerIds) {
                notifyPlayer(id, player, Component.translatable("message.chessmod.new_game_started",
                        player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD), false);
            }
        }

        game = new ChessGame();
        gameId++;
        revision++;
        mode = newMode;
        difficulty = newDifficulty;
        offer = Offer.NONE;
        Arrays.fill(playerIds, null);
        Arrays.fill(playerNames, "");
        Side starter = color.resolve(player.getRandom());
        switch (mode) {
            case VS_COMPUTER -> {
                computerSide = starter.opposite();
                takeSeat(player, starter);
            }
            case TWO_PLAYERS -> takeSeat(player, starter);
            case SOLO -> {
                takeSeat(player, Side.WHITE);
                takeSeat(player, Side.BLACK);
            }
        }
        ticksSinceMove = 0;
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
        sync();
    }

    private void tryUndo(ServerPlayer player) {
        if (mode == GameMode.TWO_PLAYERS && hasTwoPlayers()) {
            requestTakeback(player);
            return;
        }
        if (!mayControl(player)) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        if (humanMovesPlayed() == 0 || game.status() == GameStatus.RESIGNATION || game.isDrawAgreed()) {
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
        afterTakeback();
    }

    /** Against another person a move can only be taken back with their consent. */
    private void requestTakeback(ServerPlayer player) {
        Side side = sideOf(player.getUUID());
        if (side == null) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        if (game.movesPlayedBy(side) == 0) {
            deny(player, "message.chessmod.cannot_undo");
            return;
        }
        if (offer != Offer.NONE) {
            deny(player, "message.chessmod.offer_pending");
            return;
        }
        makeOffer(player, side, Offer.TAKEBACK);
    }

    private void tryOfferDraw(ServerPlayer player) {
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        Side side = mode == GameMode.VS_COMPUTER
                ? (isParticipant(player.getUUID()) ? computerSide.opposite() : null)
                : sideOf(player.getUUID());
        if (side == null) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        switch (mode) {
            case VS_COMPUTER -> {
                if (ChessAI.acceptsDraw(game.position(), computerSide)) {
                    game.agreeDraw();
                    endGame();
                } else {
                    player.displayClientMessage(Component.translatable("message.chessmod.computer_declines_draw")
                            .withStyle(ChatFormatting.YELLOW), true);
                }
            }
            case SOLO -> {
                game.agreeDraw();
                endGame();
            }
            case TWO_PLAYERS -> {
                if (!hasTwoPlayers()) {
                    deny(player, "message.chessmod.no_opponent");
                } else if (offer == Offer.DRAW && offerFrom == side.opposite()) {
                    // Both players want a draw: offering back counts as accepting.
                    tryAnswerOffer(player, true);
                } else if (offer != Offer.NONE) {
                    deny(player, "message.chessmod.offer_pending");
                } else {
                    makeOffer(player, side, Offer.DRAW);
                }
            }
        }
    }

    private void makeOffer(ServerPlayer player, Side side, Offer newOffer) {
        offer = newOffer;
        offerFrom = side;
        notifyPlayer(playerIds[side.opposite().ordinal()], player, Component.translatable(
                "message.chessmod.offer." + newOffer.serializedName(), player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.translatable("message.chessmod.offer_sent").withStyle(ChatFormatting.GRAY), true);
        playSound(SoundEvents.BOOK_PAGE_TURN, 0.6f, 1.4f);
        sync();
    }

    private void tryAnswerOffer(ServerPlayer player, boolean accept) {
        if (offer == Offer.NONE) {
            return;
        }
        Side requester = offerFrom;
        if (!player.getUUID().equals(playerIds[requester.opposite().ordinal()])) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        Offer answered = offer;
        offer = Offer.NONE;
        String key = "message.chessmod.offer." + (accept ? "accepted." : "declined.") + answered.serializedName();
        notifyPlayer(playerIds[requester.ordinal()], player, Component.translatable(key, player.getGameProfile().getName())
                .withStyle(accept ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        if (!accept) {
            sync();
            return;
        }
        if (answered == Offer.DRAW) {
            game.agreeDraw();
            endGame();
        } else if (!game.isOver() && game.movesPlayedBy(requester) > 0) {
            // Go back to the requester's last turn, taking back the opponent's reply if there was one.
            do {
                game.undo();
            } while (game.ply() > 0 && game.sideToMove() != requester);
            afterTakeback();
        } else {
            sync();
        }
    }

    private void afterTakeback() {
        revision++;
        ticksSinceMove = 0;
        offer = Offer.NONE;
        playSound(SoundEvents.WOOD_HIT, 0.6f, 1.4f);
        sync();
    }

    private void tryResign(ServerPlayer player) {
        if (game.isOver()) {
            deny(player, "message.chessmod.game_over");
            return;
        }
        Side side = mode == GameMode.VS_COMPUTER
                ? (isParticipant(player.getUUID()) ? computerSide.opposite() : null)
                : sideOf(player.getUUID());
        if (side == null) {
            deny(player, "message.chessmod.not_player");
            return;
        }
        game.resign(side);
        endGame();
    }

    /** Against the computer only the player's own moves can be taken back; otherwise any move. */
    private int humanMovesPlayed() {
        return mode == GameMode.VS_COMPUTER ? game.movesPlayedBy(computerSide.opposite()) : game.ply();
    }

    /** Players of the current game may control it; so may anyone when nobody has taken a seat yet. */
    private boolean mayControl(ServerPlayer player) {
        boolean seatsTaken = playerIds[0] != null || playerIds[1] != null;
        return !seatsTaken || isParticipant(player.getUUID()) || player.hasPermissions(2);
    }

    private void playMove(Move move, @Nullable ServerPlayer mover) {
        game.play(move);
        ticksSinceMove = 0;
        offer = Offer.NONE;
        if (move.isCapture()) {
            playSound(SoundEvents.WOOD_HIT, 0.9f, 0.85f);
        } else {
            playSound(SoundEvents.WOOD_PLACE, 0.8f, 1.25f);
        }
        if (game.isOver()) {
            endGame();
            return;
        }
        if (game.position().inCheck()) {
            playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.6f);
        }
        if (mode == GameMode.TWO_PLAYERS && mover != null) {
            // The opponent may have walked away from the board: tell them it is their turn.
            List<String> san = game.sanMoves();
            notifyPlayer(playerIds[game.sideToMove().ordinal()], mover, Component.translatable("message.chessmod.opponent_moved",
                    mover.getGameProfile().getName(), ChessTexts.san(san.get(san.size() - 1))).withStyle(ChatFormatting.GOLD), true);
        }
        sync();
    }

    private void endGame() {
        offer = Offer.NONE;
        onGameOver();
        sync();
    }

    private void onGameOver() {
        playSound(game.winner() != null ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.0f);
        if (!(level instanceof ServerLevel serverLevel) || !ChessConfig.ANNOUNCE_RESULTS.get()) {
            return;
        }
        Component message = Component.translatable("message.chessmod.prefix").withStyle(ChatFormatting.GOLD)
                .append(" ").append(ChessTexts.result(game, this::winnerName).withStyle(ChatFormatting.WHITE));
        Set<ServerPlayer> recipients = new HashSet<>();
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.position().distanceToSqr(centre) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS) {
                recipients.add(player);
            }
        }
        // The players themselves hear about it wherever they are.
        for (UUID id : playerIds) {
            ServerPlayer player = id == null ? null : serverLevel.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                recipients.add(player);
            }
        }
        recipients.forEach(player -> player.sendSystemMessage(message));
    }

    /**
     * Tells a seated player about something their opponent did, even if they are away from the board.
     * Nothing is sent to the actor themselves (for example when one person plays both sides).
     */
    private void notifyPlayer(@Nullable UUID target, ServerPlayer actor, Component message, boolean actionBar) {
        if (target == null || target.equals(actor.getUUID()) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(target);
        if (player != null) {
            player.displayClientMessage(message, actionBar);
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.6f, 1.5f);
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
        if (game.isDrawAgreed()) {
            tag.putBoolean("DrawAgreed", true);
        }
        if (offer != Offer.NONE) {
            tag.putString("Offer", offer.serializedName());
            tag.putString("OfferFrom", offerFrom.serializedName());
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
        if (tag.getBoolean("DrawAgreed")) {
            game.agreeDraw();
        }
        offer = Offer.byName(tag.getString("Offer"));
        offerFrom = Side.byName(tag.getString("OfferFrom"), Side.WHITE);
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
