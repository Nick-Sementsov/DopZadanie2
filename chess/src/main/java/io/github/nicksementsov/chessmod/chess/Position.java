package io.github.nicksementsov.chessmod.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

import static io.github.nicksementsov.chessmod.chess.Piece.BISHOP;
import static io.github.nicksementsov.chessmod.chess.Piece.EMPTY;
import static io.github.nicksementsov.chessmod.chess.Piece.KING;
import static io.github.nicksementsov.chessmod.chess.Piece.KNIGHT;
import static io.github.nicksementsov.chessmod.chess.Piece.PAWN;
import static io.github.nicksementsov.chessmod.chess.Piece.QUEEN;
import static io.github.nicksementsov.chessmod.chess.Piece.ROOK;

/**
 * An immutable chess position. Applying a move returns a new position, which keeps the
 * engine simple and makes positions safe to hand over to the computer opponent's thread.
 */
public final class Position {
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    static final int WHITE_KINGSIDE = 1;
    static final int WHITE_QUEENSIDE = 2;
    static final int BLACK_KINGSIDE = 4;
    static final int BLACK_QUEENSIDE = 8;

    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] PROMOTIONS = {QUEEN, ROOK, BISHOP, KNIGHT};

    /** Castling rights that survive a move touching each square (moving the king or a rook, or capturing a rook). */
    private static final int[] CASTLING_MASK = new int[64];

    private static final long[][] PIECE_KEYS = new long[16][64];
    private static final long[] CASTLING_KEYS = new long[16];
    private static final long[] EN_PASSANT_KEYS = new long[8];
    private static final long BLACK_TO_MOVE_KEY;

    static {
        Arrays.fill(CASTLING_MASK, 15);
        CASTLING_MASK[Square.of(0, 0)] &= ~WHITE_QUEENSIDE;
        CASTLING_MASK[Square.of(7, 0)] &= ~WHITE_KINGSIDE;
        CASTLING_MASK[Square.of(4, 0)] &= ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        CASTLING_MASK[Square.of(0, 7)] &= ~BLACK_QUEENSIDE;
        CASTLING_MASK[Square.of(7, 7)] &= ~BLACK_KINGSIDE;
        CASTLING_MASK[Square.of(4, 7)] &= ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);

        SplittableRandom random = new SplittableRandom(0x5EEDC4E55L);
        for (long[] keys : PIECE_KEYS) {
            for (int i = 0; i < keys.length; i++) {
                keys[i] = random.nextLong();
            }
        }
        for (int i = 0; i < CASTLING_KEYS.length; i++) {
            CASTLING_KEYS[i] = random.nextLong();
        }
        for (int i = 0; i < EN_PASSANT_KEYS.length; i++) {
            EN_PASSANT_KEYS[i] = random.nextLong();
        }
        BLACK_TO_MOVE_KEY = random.nextLong();
    }

    private final int[] board;
    private final Side sideToMove;
    private final int castling;
    private final int enPassant;
    private final int halfmoveClock;
    private final int fullmoveNumber;
    private final int whiteKing;
    private final int blackKing;

    private long hash;
    private boolean hashComputed;
    private List<Move> legalMoves;

    private Position(int[] board, Side sideToMove, int castling, int enPassant, int halfmoveClock,
                     int fullmoveNumber, int whiteKing, int blackKing) {
        this.board = board;
        this.sideToMove = sideToMove;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
        this.whiteKing = whiteKing;
        this.blackKing = blackKing;
    }

    public static Position start() {
        return fromFen(START_FEN);
    }

    /**
     * Reads a position in Forsyth-Edwards Notation.
     *
     * @throws IllegalArgumentException if the text is not a valid position
     */
    public static Position fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN needs 8 ranks: " + fen);
        }
        int[] board = new int[64];
        int whiteKing = Square.NONE;
        int blackKing = Square.NONE;
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i;
            int file = 0;
            for (char c : ranks[i].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                    continue;
                }
                int piece = Piece.fromFen(c);
                if (piece == EMPTY || file > 7) {
                    throw new IllegalArgumentException("Bad FEN rank '" + ranks[i] + "': " + fen);
                }
                int square = Square.of(file, rank);
                board[square] = piece;
                if (Piece.typeId(piece) == KING) {
                    if (Piece.side(piece) == Side.WHITE) {
                        if (whiteKing != Square.NONE) {
                            throw new IllegalArgumentException("Two white kings: " + fen);
                        }
                        whiteKing = square;
                    } else {
                        if (blackKing != Square.NONE) {
                            throw new IllegalArgumentException("Two black kings: " + fen);
                        }
                        blackKing = square;
                    }
                }
                file++;
            }
            if (file != 8) {
                throw new IllegalArgumentException("Bad FEN rank '" + ranks[i] + "': " + fen);
            }
        }
        if (whiteKing == Square.NONE || blackKing == Square.NONE) {
            throw new IllegalArgumentException("Both kings must be on the board: " + fen);
        }

        Side side = switch (parts[1]) {
            case "w" -> Side.WHITE;
            case "b" -> Side.BLACK;
            default -> throw new IllegalArgumentException("Bad side to move: " + fen);
        };

        int castling = 0;
        if (!parts[2].equals("-")) {
            for (char c : parts[2].toCharArray()) {
                castling |= switch (c) {
                    case 'K' -> WHITE_KINGSIDE;
                    case 'Q' -> WHITE_QUEENSIDE;
                    case 'k' -> BLACK_KINGSIDE;
                    case 'q' -> BLACK_QUEENSIDE;
                    default -> throw new IllegalArgumentException("Bad castling rights: " + fen);
                };
            }
        }

        int enPassant = Square.NONE;
        if (!parts[3].equals("-")) {
            enPassant = Square.parse(parts[3]);
            if (enPassant == Square.NONE) {
                throw new IllegalArgumentException("Bad en passant square: " + fen);
            }
        }

        try {
            int halfmove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
            int fullmove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
            return new Position(board, side, castling, enPassant, halfmove, Math.max(1, fullmove), whiteKing, blackKing);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad move counters: " + fen, e);
        }
    }

    public String toFen() {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = board[Square.of(file, rank)];
                if (piece == EMPTY) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    fen.append(empty);
                    empty = 0;
                }
                fen.append(Piece.toFen(piece));
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (rank > 0) {
                fen.append('/');
            }
        }
        fen.append(sideToMove == Side.WHITE ? " w " : " b ");
        if (castling == 0) {
            fen.append('-');
        } else {
            if ((castling & WHITE_KINGSIDE) != 0) fen.append('K');
            if ((castling & WHITE_QUEENSIDE) != 0) fen.append('Q');
            if ((castling & BLACK_KINGSIDE) != 0) fen.append('k');
            if ((castling & BLACK_QUEENSIDE) != 0) fen.append('q');
        }
        fen.append(' ').append(enPassant == Square.NONE ? "-" : Square.name(enPassant));
        fen.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return fen.toString();
    }

    /** A copy that shares no lazily computed state with this one, for use on another thread. */
    public Position copy() {
        return new Position(board.clone(), sideToMove, castling, enPassant, halfmoveClock, fullmoveNumber, whiteKing, blackKing);
    }

    public int pieceAt(int square) {
        return board[square];
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public int kingSquare(Side side) {
        return side == Side.WHITE ? whiteKing : blackKing;
    }

    public boolean inCheck() {
        return isSquareAttacked(kingSquare(sideToMove), sideToMove.opposite());
    }

    public boolean isSquareAttacked(int square, Side by) {
        int file = Square.file(square);
        int rank = Square.rank(square);
        int bit = by.bit();

        int pawnRank = by == Side.WHITE ? rank - 1 : rank + 1;
        if (pawnRank >= 0 && pawnRank <= 7) {
            int pawn = bit | PAWN;
            if (file > 0 && board[Square.of(file - 1, pawnRank)] == pawn) return true;
            if (file < 7 && board[Square.of(file + 1, pawnRank)] == pawn) return true;
        }
        if (attackedByStep(file, rank, KNIGHT_STEPS, bit | KNIGHT)) return true;
        if (attackedByStep(file, rank, KING_STEPS, bit | KING)) return true;
        if (attackedBySlide(file, rank, ROOK_DIRECTIONS, bit | ROOK, bit | QUEEN)) return true;
        return attackedBySlide(file, rank, BISHOP_DIRECTIONS, bit | BISHOP, bit | QUEEN);
    }

    private boolean attackedByStep(int file, int rank, int[][] steps, int attacker) {
        for (int[] step : steps) {
            int f = file + step[0];
            int r = rank + step[1];
            if (f >= 0 && f <= 7 && r >= 0 && r <= 7 && board[Square.of(f, r)] == attacker) {
                return true;
            }
        }
        return false;
    }

    private boolean attackedBySlide(int file, int rank, int[][] directions, int slider, int queen) {
        for (int[] direction : directions) {
            int f = file + direction[0];
            int r = rank + direction[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int piece = board[Square.of(f, r)];
                if (piece != EMPTY) {
                    if (piece == slider || piece == queen) {
                        return true;
                    }
                    break;
                }
                f += direction[0];
                r += direction[1];
            }
        }
        return false;
    }

    /** All legal moves for the side to move. The list is cached and must not be modified. */
    public List<Move> legalMoves() {
        if (legalMoves == null) {
            List<Move> moves = new ArrayList<>(48);
            generateLegal(moves, null, false);
            legalMoves = Collections.unmodifiableList(moves);
        }
        return legalMoves;
    }

    /**
     * Generates legal moves and, optionally, the positions they lead to (index-aligned with the moves).
     *
     * @param tacticalOnly only captures and queen promotions, for the quiescence search
     */
    void generateLegal(List<Move> moves, List<Position> results, boolean tacticalOnly) {
        List<Move> pseudo = new ArrayList<>(tacticalOnly ? 16 : 64);
        generatePseudoLegal(pseudo, tacticalOnly);
        Side us = sideToMove;
        for (Move move : pseudo) {
            Position next = apply(move);
            if (!next.isSquareAttacked(next.kingSquare(us), us.opposite())) {
                moves.add(move);
                if (results != null) {
                    results.add(next);
                }
            }
        }
    }

    private void generatePseudoLegal(List<Move> out, boolean tacticalOnly) {
        int us = sideToMove.bit();
        for (int square = 0; square < 64; square++) {
            int piece = board[square];
            if (piece == EMPTY || (piece & Piece.BLACK_BIT) != us) {
                continue;
            }
            switch (Piece.typeId(piece)) {
                case PAWN -> generatePawnMoves(out, square, piece, tacticalOnly);
                case KNIGHT -> generateSteps(out, square, piece, KNIGHT_STEPS, tacticalOnly);
                case BISHOP -> generateSlides(out, square, piece, BISHOP_DIRECTIONS, tacticalOnly);
                case ROOK -> generateSlides(out, square, piece, ROOK_DIRECTIONS, tacticalOnly);
                case QUEEN -> {
                    generateSlides(out, square, piece, ROOK_DIRECTIONS, tacticalOnly);
                    generateSlides(out, square, piece, BISHOP_DIRECTIONS, tacticalOnly);
                }
                case KING -> {
                    generateSteps(out, square, piece, KING_STEPS, tacticalOnly);
                    if (!tacticalOnly) {
                        generateCastling(out, square, piece);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void generatePawnMoves(List<Move> out, int from, int pawn, boolean tacticalOnly) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        int direction = sideToMove == Side.WHITE ? 1 : -1;
        int startRank = sideToMove == Side.WHITE ? 1 : 6;
        int lastRank = sideToMove == Side.WHITE ? 7 : 0;
        int nextRank = rank + direction;
        if (nextRank < 0 || nextRank > 7) {
            return;
        }

        int forward = Square.of(file, nextRank);
        if (board[forward] == EMPTY) {
            if (nextRank == lastRank) {
                addPromotions(out, from, forward, pawn, EMPTY, 0, tacticalOnly);
            } else if (!tacticalOnly) {
                out.add(new Move(from, forward, pawn, EMPTY, 0, 0));
                int twoForward = Square.of(file, nextRank + direction);
                if (rank == startRank && board[twoForward] == EMPTY) {
                    out.add(new Move(from, twoForward, pawn, EMPTY, 0, Move.DOUBLE_PUSH));
                }
            }
        }

        for (int side = -1; side <= 1; side += 2) {
            int targetFile = file + side;
            if (targetFile < 0 || targetFile > 7) {
                continue;
            }
            int to = Square.of(targetFile, nextRank);
            int target = board[to];
            if (target != EMPTY) {
                if ((target & Piece.BLACK_BIT) != (pawn & Piece.BLACK_BIT)) {
                    if (nextRank == lastRank) {
                        addPromotions(out, from, to, pawn, target, Move.CAPTURE, tacticalOnly);
                    } else {
                        out.add(new Move(from, to, pawn, target, 0, Move.CAPTURE));
                    }
                }
            } else if (to == enPassant) {
                int victim = board[Square.of(targetFile, rank)];
                if (victim == ((pawn ^ Piece.BLACK_BIT))) {
                    out.add(new Move(from, to, pawn, victim, 0, Move.CAPTURE | Move.EN_PASSANT));
                }
            }
        }
    }

    private static void addPromotions(List<Move> out, int from, int to, int pawn, int captured, int flags, boolean queenOnly) {
        for (int promotion : PROMOTIONS) {
            out.add(new Move(from, to, pawn, captured, promotion, flags));
            if (queenOnly) {
                return;
            }
        }
    }

    private void generateSteps(List<Move> out, int from, int piece, int[][] steps, boolean capturesOnly) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        for (int[] step : steps) {
            int f = file + step[0];
            int r = rank + step[1];
            if (f < 0 || f > 7 || r < 0 || r > 7) {
                continue;
            }
            addIfFreeOrEnemy(out, from, Square.of(f, r), piece, capturesOnly);
        }
    }

    private void generateSlides(List<Move> out, int from, int piece, int[][] directions, boolean capturesOnly) {
        int file = Square.file(from);
        int rank = Square.rank(from);
        for (int[] direction : directions) {
            int f = file + direction[0];
            int r = rank + direction[1];
            while (f >= 0 && f <= 7 && r >= 0 && r <= 7) {
                int to = Square.of(f, r);
                addIfFreeOrEnemy(out, from, to, piece, capturesOnly);
                if (board[to] != EMPTY) {
                    break;
                }
                f += direction[0];
                r += direction[1];
            }
        }
    }

    private void addIfFreeOrEnemy(List<Move> out, int from, int to, int piece, boolean capturesOnly) {
        int target = board[to];
        if (target == EMPTY) {
            if (!capturesOnly) {
                out.add(new Move(from, to, piece, EMPTY, 0, 0));
            }
        } else if ((target & Piece.BLACK_BIT) != (piece & Piece.BLACK_BIT)) {
            out.add(new Move(from, to, piece, target, 0, Move.CAPTURE));
        }
    }

    private void generateCastling(List<Move> out, int from, int king) {
        boolean white = sideToMove == Side.WHITE;
        int home = white ? Square.of(4, 0) : Square.of(4, 7);
        if (from != home) {
            return;
        }
        int kingside = white ? WHITE_KINGSIDE : BLACK_KINGSIDE;
        int queenside = white ? WHITE_QUEENSIDE : BLACK_QUEENSIDE;
        int rook = sideToMove.bit() | ROOK;
        Side enemy = sideToMove.opposite();
        if ((castling & (kingside | queenside)) == 0 || isSquareAttacked(home, enemy)) {
            return;
        }
        if ((castling & kingside) != 0 && board[home + 1] == EMPTY && board[home + 2] == EMPTY && board[home + 3] == rook
                && !isSquareAttacked(home + 1, enemy) && !isSquareAttacked(home + 2, enemy)) {
            out.add(new Move(home, home + 2, king, EMPTY, 0, Move.CASTLE));
        }
        if ((castling & queenside) != 0 && board[home - 1] == EMPTY && board[home - 2] == EMPTY && board[home - 3] == EMPTY
                && board[home - 4] == rook && !isSquareAttacked(home - 1, enemy) && !isSquareAttacked(home - 2, enemy)) {
            out.add(new Move(home, home - 2, king, EMPTY, 0, Move.CASTLE));
        }
    }

    /** Plays a move without checking that it is legal. Use moves from {@link #legalMoves()}. */
    public Position apply(Move move) {
        int[] next = board.clone();
        int from = move.from();
        int to = move.to();
        int piece = next[from];
        next[from] = EMPTY;
        if (move.isEnPassant()) {
            next[move.captureSquare()] = EMPTY;
        }
        next[to] = move.isPromotion() ? (piece & Piece.BLACK_BIT) | move.promotion() : piece;
        if (move.isCastle()) {
            next[move.castleRookTo()] = next[move.castleRookFrom()];
            next[move.castleRookFrom()] = EMPTY;
        }

        int newWhiteKing = whiteKing;
        int newBlackKing = blackKing;
        if (Piece.typeId(piece) == KING) {
            if (sideToMove == Side.WHITE) {
                newWhiteKing = to;
            } else {
                newBlackKing = to;
            }
        }
        int newCastling = castling & CASTLING_MASK[from] & CASTLING_MASK[to];
        int newEnPassant = (move.flags() & Move.DOUBLE_PUSH) != 0 ? (from + to) / 2 : Square.NONE;
        int newHalfmove = Piece.typeId(piece) == PAWN || move.isCapture() ? 0 : halfmoveClock + 1;
        int newFullmove = sideToMove == Side.BLACK ? fullmoveNumber + 1 : fullmoveNumber;
        return new Position(next, sideToMove.opposite(), newCastling, newEnPassant, newHalfmove, newFullmove,
                newWhiteKing, newBlackKing);
    }

    /**
     * Zobrist hash of everything that matters for repetitions: pieces, side to move, castling rights
     * and the en passant file, but only if an en passant capture is actually available.
     */
    public long hash() {
        if (!hashComputed) {
            long h = 0;
            for (int square = 0; square < 64; square++) {
                if (board[square] != EMPTY) {
                    h ^= PIECE_KEYS[board[square]][square];
                }
            }
            h ^= CASTLING_KEYS[castling];
            if (sideToMove == Side.BLACK) {
                h ^= BLACK_TO_MOVE_KEY;
            }
            if (enPassantCapturePossible()) {
                h ^= EN_PASSANT_KEYS[Square.file(enPassant)];
            }
            hash = h;
            hashComputed = true;
        }
        return hash;
    }

    private boolean enPassantCapturePossible() {
        if (enPassant == Square.NONE) {
            return false;
        }
        for (Move move : legalMoves()) {
            if (move.isEnPassant()) {
                return true;
            }
        }
        return false;
    }

    /** True when neither side can possibly checkmate (K v K, K+minor v K, or only same-coloured bishops). */
    public boolean hasInsufficientMaterial() {
        int knights = 0;
        int bishops = 0;
        int bishopSquareColours = 0;
        for (int square = 0; square < 64; square++) {
            switch (Piece.typeId(board[square])) {
                case PAWN, ROOK, QUEEN -> {
                    return false;
                }
                case KNIGHT -> knights++;
                case BISHOP -> {
                    bishops++;
                    bishopSquareColours |= Square.isLight(square) ? 1 : 2;
                }
                default -> {
                }
            }
        }
        if (knights + bishops <= 1) {
            return true;
        }
        return knights == 0 && bishopSquareColours != 3;
    }
}
