package io.github.nicksementsov.chessmod.chess;

import java.util.Locale;

public enum GameStatus {
    ONGOING,
    CHECKMATE,
    STALEMATE,
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE,
    INSUFFICIENT_MATERIAL,
    RESIGNATION;

    public boolean isDraw() {
        return this == STALEMATE || this == THREEFOLD_REPETITION || this == FIFTY_MOVE_RULE || this == INSUFFICIENT_MATERIAL;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
