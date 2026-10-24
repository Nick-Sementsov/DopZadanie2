package io.github.nicksementsov.chessmod.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A game in progress: the starting position, the moves played so far and the result. */
public final class ChessGame {
    private final String startFen;
    private final List<Position> positions = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private final List<String> sanMoves = new ArrayList<>();
    private Side resigned;
    private GameStatus status;

    public ChessGame() {
        this(Position.START_FEN);
    }

    /** @throws IllegalArgumentException if the FEN is invalid */
    public ChessGame(String startFen) {
        Position start = Position.fromFen(startFen);
        this.startFen = start.toFen();
        positions.add(start);
    }

    /**
     * Rebuilds a game from saved data. Replaying stops at the first move that is not legal, so damaged
     * data still produces a valid game; a broken start position falls back to the standard one.
     */
    public static ChessGame fromHistory(String startFen, List<String> uciMoves, Side resigned) {
        ChessGame game;
        try {
            game = new ChessGame(startFen);
        } catch (IllegalArgumentException e) {
            game = new ChessGame();
        }
        for (String uci : uciMoves) {
            Move move = game.findMove(uci);
            if (move == null) {
                break;
            }
            game.play(move);
        }
        if (resigned != null && !game.isOver()) {
            game.resign(resigned);
        }
        return game;
    }

    public String startFen() {
        return startFen;
    }

    public Position position() {
        return positions.get(positions.size() - 1);
    }

    public Side sideToMove() {
        return position().sideToMove();
    }

    /** Number of half-moves played. */
    public int ply() {
        return moves.size();
    }

    public List<Move> moves() {
        return Collections.unmodifiableList(moves);
    }

    public List<String> sanMoves() {
        return Collections.unmodifiableList(sanMoves);
    }

    public List<String> uciMoves() {
        List<String> result = new ArrayList<>(moves.size());
        for (Move move : moves) {
            result.add(move.uci());
        }
        return result;
    }

    public Move lastMove() {
        return moves.isEmpty() ? null : moves.get(moves.size() - 1);
    }

    /** Legal moves in the current position; empty once the game is over. */
    public List<Move> legalMoves() {
        return isOver() ? List.of() : position().legalMoves();
    }

    /** Finds the legal move between two squares. Promotions need the {@link PieceType#id()} to promote to. */
    public Move findMove(int from, int to, int promotion) {
        for (Move move : legalMoves()) {
            if (move.from() == from && move.to() == to && move.promotion() == promotion) {
                return move;
            }
        }
        return null;
    }

    public Move findMove(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            return null;
        }
        int from = Square.parse(uci.substring(0, 2));
        int to = Square.parse(uci.substring(2, 4));
        int promotion = 0;
        if (uci.length() == 5) {
            PieceType type = PieceType.bySymbol(uci.charAt(4));
            if (type == null) {
                return null;
            }
            promotion = type.id();
        }
        return from == Square.NONE || to == Square.NONE ? null : findMove(from, to, promotion);
    }

    /** True if some legal move goes from {@code from} to {@code to} (with any promotion). */
    public boolean isPromotion(int from, int to) {
        for (Move move : legalMoves()) {
            if (move.from() == from && move.to() == to && move.isPromotion()) {
                return true;
            }
        }
        return false;
    }

    /** Plays a move if it is legal in the current position. */
    public boolean play(Move move) {
        if (!legalMoves().contains(move)) {
            return false;
        }
        Position current = position();
        sanMoves.add(Notation.san(current, move));
        moves.add(move);
        positions.add(current.apply(move));
        status = null;
        return true;
    }

    /** Takes back the last half-move. Not possible after a resignation. */
    public boolean undo() {
        if (moves.isEmpty() || resigned != null) {
            return false;
        }
        moves.remove(moves.size() - 1);
        sanMoves.remove(sanMoves.size() - 1);
        positions.remove(positions.size() - 1);
        status = null;
        return true;
    }

    public void resign(Side side) {
        if (!isOver()) {
            resigned = side;
            status = null;
        }
    }

    public Side resignedSide() {
        return resigned;
    }

    public GameStatus status() {
        if (status == null) {
            status = computeStatus();
        }
        return status;
    }

    private GameStatus computeStatus() {
        if (resigned != null) {
            return GameStatus.RESIGNATION;
        }
        Position position = position();
        if (position.legalMoves().isEmpty()) {
            return position.inCheck() ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (position.hasInsufficientMaterial()) {
            return GameStatus.INSUFFICIENT_MATERIAL;
        }
        if (position.halfmoveClock() >= 100) {
            return GameStatus.FIFTY_MOVE_RULE;
        }
        long hash = position.hash();
        int repetitions = 0;
        for (Position earlier : positions) {
            if (earlier.hash() == hash) {
                repetitions++;
            }
        }
        return repetitions >= 3 ? GameStatus.THREEFOLD_REPETITION : GameStatus.ONGOING;
    }

    public boolean isOver() {
        return status() != GameStatus.ONGOING;
    }

    /** The winner, or {@code null} while the game is going on or when it ended in a draw. */
    public Side winner() {
        return switch (status()) {
            case CHECKMATE -> sideToMove().opposite();
            case RESIGNATION -> resigned.opposite();
            default -> null;
        };
    }

    /** Hashes of every earlier position, so the computer can recognise repetitions. */
    public Set<Long> previousPositionHashes() {
        Set<Long> hashes = new HashSet<>();
        for (int i = 0; i < positions.size() - 1; i++) {
            hashes.add(positions.get(i).hash());
        }
        return hashes;
    }

    /** Material difference in centipawns, positive when white is ahead. */
    public int materialBalance() {
        Position position = position();
        int balance = 0;
        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece != Piece.EMPTY) {
                int value = Piece.type(piece).value();
                balance += Piece.side(piece) == Side.WHITE ? value : -value;
            }
        }
        return balance;
    }
}
