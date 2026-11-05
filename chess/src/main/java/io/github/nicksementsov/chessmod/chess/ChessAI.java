package io.github.nicksementsov.chessmod.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Computer opponent: iterative-deepening alpha-beta search with a quiescence search and
 * piece-square-table evaluation. Weaker levels search less deeply and add noise to their choice.
 */
public final class ChessAI {
    public enum Difficulty {
        EASY(1, false, 90),
        MEDIUM(3, true, 15),
        HARD(64, true, 0);

        private final int maxDepth;
        private final boolean quiescence;
        private final int noise;

        Difficulty(int maxDepth, boolean quiescence, int noise) {
            this.maxDepth = maxDepth;
            this.quiescence = quiescence;
            this.noise = noise;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Difficulty byName(String name, Difficulty fallback) {
            for (Difficulty difficulty : values()) {
                if (difficulty.serializedName().equals(name)) {
                    return difficulty;
                }
            }
            return fallback;
        }
    }

    static final int MATE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_QUIESCENCE_DEPTH = 8;
    private static final int MAX_CHECK_EXTENSION_PLY = 16;
    /** How far behind (in centipawns) the computer must be before it agrees to a draw. */
    private static final int DRAW_ACCEPT_MARGIN = 150;

    // Piece-square tables from white's point of view, rank 8 first (Tomasz Michniewski's "simplified evaluation").
    private static final int[] PAWN_TABLE = {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] KNIGHT_TABLE = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] BISHOP_TABLE = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] ROOK_TABLE = {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] QUEEN_TABLE = {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] KING_MIDDLEGAME_TABLE = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] KING_ENDGAME_TABLE = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};
    private static final int[][] TABLES = {null, PAWN_TABLE, KNIGHT_TABLE, BISHOP_TABLE, ROOK_TABLE, QUEEN_TABLE, KING_MIDDLEGAME_TABLE};
    private static final int[] VALUES = {0, 100, 320, 330, 500, 900, 0};
    /** Remaining non-pawn material (both sides) at or below which the king should become active. */
    private static final int ENDGAME_MATERIAL = 2600;

    private final Difficulty difficulty;
    private final Set<Long> history;
    private final long deadline;
    private long nodes;
    private boolean aborted;

    private ChessAI(Difficulty difficulty, Set<Long> history, long timeLimitMillis) {
        this.difficulty = difficulty;
        this.history = history;
        this.deadline = System.nanoTime() + timeLimitMillis * 1_000_000L;
    }

    /**
     * Picks a move for the side to move, or returns {@code null} if there are no legal moves.
     *
     * @param history         hashes of earlier positions in the game; reaching one again counts as a draw
     * @param timeLimitMillis how long the search may take; the result of the last finished depth is used
     */
    public static Move chooseMove(Position position, Difficulty difficulty, Set<Long> history, long timeLimitMillis, Random random) {
        return new ChessAI(difficulty, history, timeLimitMillis).searchRoot(position.copy(), random);
    }

    private Move searchRoot(Position root, Random random) {
        List<Move> moves = new ArrayList<>();
        List<Position> children = new ArrayList<>();
        root.generateLegal(moves, children, false);
        if (moves.size() <= 1) {
            return moves.isEmpty() ? null : moves.get(0);
        }

        int count = moves.size();
        int[] noise = new int[count];
        for (int i = 0; i < count; i++) {
            noise[i] = difficulty.noise == 0 ? 0 : random.nextInt(2 * difficulty.noise + 1) - difficulty.noise;
        }
        int[] order = orderMoves(moves);
        int best = order[0];
        int[] scores = new int[count];

        for (int depth = 1; depth <= difficulty.maxDepth; depth++) {
            int alpha = -INFINITY;
            boolean finished = true;
            for (int index : order) {
                // With noise every root move needs an exact score; otherwise a null window above alpha is enough.
                int score = difficulty.noise > 0
                        ? -negamax(children.get(index), depth - 1, -INFINITY, INFINITY, 1)
                        : -negamax(children.get(index), depth - 1, -INFINITY, -alpha, 1);
                if (aborted && depth > 1) {
                    finished = false;
                    break;
                }
                scores[index] = score;
                alpha = Math.max(alpha, score);
            }
            if (!finished) {
                break;
            }

            int bestIndex = order[0];
            for (int index : order) {
                if (scores[index] + noise[index] > scores[bestIndex] + noise[bestIndex]) {
                    bestIndex = index;
                }
            }
            best = bestIndex;
            sortByScore(order, scores);
            if (Math.abs(scores[best]) >= MATE - 1000 || aborted) {
                break;
            }
        }
        return moves.get(best);
    }

    private int negamax(Position position, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            aborted = true;
        }
        if (aborted) {
            return 0;
        }
        if (position.halfmoveClock() >= 100 || position.hasInsufficientMaterial() || history.contains(position.hash())) {
            return 0;
        }

        boolean inCheck = position.inCheck();
        if (inCheck && ply < MAX_CHECK_EXTENSION_PLY) {
            depth++;
        }
        if (depth <= 0) {
            return difficulty.quiescence ? quiescence(position, alpha, beta, 0) : evaluate(position);
        }

        List<Move> moves = new ArrayList<>(48);
        List<Position> children = new ArrayList<>(48);
        position.generateLegal(moves, children, false);
        if (moves.isEmpty()) {
            return inCheck ? -MATE + ply : 0;
        }

        int best = -INFINITY;
        for (int index : orderMoves(moves)) {
            int score = -negamax(children.get(index), depth - 1, -beta, -alpha, ply + 1);
            if (aborted) {
                return 0;
            }
            if (score > best) {
                best = score;
            }
            if (score > alpha) {
                alpha = score;
                if (alpha >= beta) {
                    break;
                }
            }
        }
        return best;
    }

    private int quiescence(Position position, int alpha, int beta, int depth) {
        if ((++nodes & 1023) == 0 && System.nanoTime() > deadline) {
            aborted = true;
        }
        if (aborted) {
            return 0;
        }
        int standPat = evaluate(position);
        if (depth >= MAX_QUIESCENCE_DEPTH || standPat >= beta) {
            return standPat;
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        List<Move> moves = new ArrayList<>(16);
        List<Position> children = new ArrayList<>(16);
        position.generateLegal(moves, children, true);
        for (int index : orderMoves(moves)) {
            int score = -quiescence(children.get(index), -beta, -alpha, depth + 1);
            if (aborted) {
                return 0;
            }
            if (score >= beta) {
                return score;
            }
            if (score > alpha) {
                alpha = score;
            }
        }
        return alpha;
    }

    /** Indices of the moves, best guesses first: winning captures (MVV-LVA), promotions, then quiet moves. */
    private static int[] orderMoves(List<Move> moves) {
        int count = moves.size();
        int[] order = new int[count];
        int[] keys = new int[count];
        for (int i = 0; i < count; i++) {
            Move move = moves.get(i);
            int key = 0;
            if (move.isCapture()) {
                key += 10_000 + VALUES[Piece.typeId(move.captured())] * 10 - VALUES[Piece.typeId(move.piece())] / 10;
            }
            if (move.isPromotion()) {
                key += 9_000 + VALUES[move.promotion()];
            }
            order[i] = i;
            keys[i] = key;
        }
        sortByScore(order, keys);
        return order;
    }

    /** Stable insertion sort of indices by descending score. */
    private static void sortByScore(int[] order, int[] scores) {
        for (int i = 1; i < order.length; i++) {
            int index = order[i];
            int j = i - 1;
            while (j >= 0 && scores[order[j]] < scores[index]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = index;
        }
    }

    /**
     * Whether the computer playing {@code side} accepts a draw offer: only when its position is clearly worse,
     * judged after the pending captures are played out.
     */
    public static boolean acceptsDraw(Position position, Side side) {
        ChessAI judge = new ChessAI(Difficulty.HARD, Set.of(), 500);
        int score = judge.quiescence(position.copy(), -INFINITY, INFINITY, 0);
        return (position.sideToMove() == side ? score : -score) <= -DRAW_ACCEPT_MARGIN;
    }

    /** Static evaluation in centipawns from the point of view of the side to move. */
    static int evaluate(Position position) {
        int nonPawnMaterial = 0;
        int whiteMaterial = 0;
        int blackMaterial = 0;
        int whiteBishops = 0;
        int blackBishops = 0;
        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.EMPTY) {
                continue;
            }
            int type = Piece.typeId(piece);
            if (type != Piece.PAWN && type != Piece.KING) {
                nonPawnMaterial += VALUES[type];
            }
            if (Piece.side(piece) == Side.WHITE) {
                whiteMaterial += VALUES[type];
                whiteBishops += type == Piece.BISHOP ? 1 : 0;
            } else {
                blackMaterial += VALUES[type];
                blackBishops += type == Piece.BISHOP ? 1 : 0;
            }
        }
        boolean endgame = nonPawnMaterial <= ENDGAME_MATERIAL;

        int score = whiteMaterial - blackMaterial;
        for (int square = 0; square < 64; square++) {
            int piece = position.pieceAt(square);
            if (piece == Piece.EMPTY) {
                continue;
            }
            int type = Piece.typeId(piece);
            boolean white = Piece.side(piece) == Side.WHITE;
            int index = white ? Square.of(Square.file(square), 7 - Square.rank(square)) : square;
            int[] table = type == Piece.KING && endgame ? KING_ENDGAME_TABLE : TABLES[type];
            score += white ? table[index] : -table[index];
        }
        if (whiteBishops >= 2) score += 30;
        if (blackBishops >= 2) score -= 30;

        if (endgame && Math.abs(whiteMaterial - blackMaterial) >= 300) {
            Side winning = whiteMaterial > blackMaterial ? Side.WHITE : Side.BLACK;
            int bonus = mopUp(position.kingSquare(winning), position.kingSquare(winning.opposite()));
            score += winning == Side.WHITE ? bonus : -bonus;
        }
        return position.sideToMove() == Side.WHITE ? score : -score;
    }

    /** Helps the stronger side finish the game: drive the lone king to the edge and walk the own king closer. */
    private static int mopUp(int winningKing, int losingKing) {
        int file = Square.file(losingKing);
        int rank = Square.rank(losingKing);
        int centreDistance = Math.max(3 - file, file - 4) + Math.max(3 - rank, rank - 4);
        int kingDistance = Math.abs(file - Square.file(winningKing)) + Math.abs(rank - Square.rank(winningKing));
        return 10 * centreDistance + 4 * (14 - kingDistance);
    }
}
