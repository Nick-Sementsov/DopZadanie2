package io.github.nicksementsov.chessmod.chess;

import java.util.Locale;

/** One of the two players. */
public enum Side {
    WHITE,
    BLACK;

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /** The bit that marks this side inside a {@link Piece} code. */
    int bit() {
        return this == WHITE ? 0 : Piece.BLACK_BIT;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Side byName(String name, Side fallback) {
        for (Side side : values()) {
            if (side.serializedName().equals(name)) {
                return side;
            }
        }
        return fallback;
    }
}
