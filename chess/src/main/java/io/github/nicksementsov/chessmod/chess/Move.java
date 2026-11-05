package io.github.nicksementsov.chessmod.chess;

/**
 * A move together with the information needed to apply and describe it.
 *
 * @param piece     the moving piece code
 * @param captured  the captured piece code, or {@link Piece#EMPTY}
 * @param promotion the {@link PieceType#id()} a pawn promotes to, or 0
 */
public record Move(int from, int to, int piece, int captured, int promotion, int flags) {
    public static final int CAPTURE = 1;
    public static final int EN_PASSANT = 2;
    public static final int CASTLE = 4;
    public static final int DOUBLE_PUSH = 8;

    public boolean isCapture() {
        return (flags & CAPTURE) != 0;
    }

    public boolean isEnPassant() {
        return (flags & EN_PASSANT) != 0;
    }

    public boolean isCastle() {
        return (flags & CASTLE) != 0;
    }

    public boolean isPromotion() {
        return promotion != 0;
    }

    /** Square the castling rook starts on, or {@link Square#NONE} if this is not castling. */
    public int castleRookFrom() {
        if (!isCastle()) {
            return Square.NONE;
        }
        return to > from ? from + 3 : from - 4;
    }

    /** Square the castling rook ends on, or {@link Square#NONE} if this is not castling. */
    public int castleRookTo() {
        if (!isCastle()) {
            return Square.NONE;
        }
        return to > from ? from + 1 : from - 1;
    }

    /** Square of the captured piece; differs from {@link #to()} for en passant. */
    public int captureSquare() {
        if (isEnPassant()) {
            return Square.of(Square.file(to), Square.rank(from));
        }
        return to;
    }

    /** Long algebraic notation used by UCI engines, e.g. {@code e2e4} or {@code e7e8q}. */
    public String uci() {
        String text = Square.name(from) + Square.name(to);
        return isPromotion() ? text + PieceType.byId(promotion).symbol() : text;
    }

    @Override
    public String toString() {
        return uci();
    }
}
