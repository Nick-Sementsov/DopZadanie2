package io.github.nicksementsov.chessmod.chess;

import java.util.Locale;

/** Kind of chess piece. The order matters: {@link #id()} is used inside {@link Piece} codes. */
public enum PieceType {
    PAWN('p', 100),
    KNIGHT('n', 320),
    BISHOP('b', 330),
    ROOK('r', 500),
    QUEEN('q', 900),
    KING('k', 0);

    private static final PieceType[] BY_ID = {null, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING};

    private final char symbol;
    private final int value;

    PieceType(char symbol, int value) {
        this.symbol = symbol;
        this.value = value;
    }

    /** Lower-case letter used by FEN and UCI notation. */
    public char symbol() {
        return symbol;
    }

    /** Material value in centipawns (the king is priceless, so 0). */
    public int value() {
        return value;
    }

    public int id() {
        return ordinal() + 1;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PieceType byId(int id) {
        return id > 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    public static PieceType bySymbol(char symbol) {
        char lower = Character.toLowerCase(symbol);
        for (PieceType type : values()) {
            if (type.symbol == lower) {
                return type;
            }
        }
        return null;
    }
}
