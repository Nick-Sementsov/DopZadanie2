package io.github.nicksementsov.chessmod.board;

import java.util.Locale;

public enum GameMode {
    /** One person against the built-in computer opponent. */
    VS_COMPUTER,
    /** Two different players, one on each side. Take-backs and draws need the opponent's consent. */
    TWO_PLAYERS,
    /** One person moves both sides, e.g. to study a position or to show a game to friends. */
    SOLO;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GameMode byName(String name, GameMode fallback) {
        for (GameMode mode : values()) {
            if (mode.serializedName().equals(name)) {
                return mode;
            }
        }
        return fallback;
    }
}
