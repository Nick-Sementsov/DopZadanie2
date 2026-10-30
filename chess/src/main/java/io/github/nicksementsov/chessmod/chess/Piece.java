package io.github.nicksementsov.chessmod.chess;

/**
 * Pieces are stored on the board as small ints: the lower three bits hold the {@link PieceType#id()},
 * bit 3 is set for black pieces and {@code 0} means an empty square.
 */
public final class Piece {
    public static final int EMPTY = 0;
    static final int BLACK_BIT = 8;

    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;

    private Piece() {
    }

    public static int of(Side side, PieceType type) {
        return side.bit() | type.id();
    }

    public static PieceType type(int piece) {
        return PieceType.byId(piece & 7);
    }

    public static Side side(int piece) {
        return (piece & BLACK_BIT) == 0 ? Side.WHITE : Side.BLACK;
    }

    static int typeId(int piece) {
        return piece & 7;
    }

    static char toFen(int piece) {
        char symbol = type(piece).symbol();
        return side(piece) == Side.WHITE ? Character.toUpperCase(symbol) : symbol;
    }

    static int fromFen(char c) {
        PieceType type = PieceType.bySymbol(c);
        if (type == null) {
            return EMPTY;
        }
        return of(Character.isUpperCase(c) ? Side.WHITE : Side.BLACK, type);
    }
}
