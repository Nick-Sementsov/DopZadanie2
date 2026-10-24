package io.github.nicksementsov.chessmod.board;

import java.util.Locale;

public enum GameMode {
    /** Two people at the same board (or one person playing both sides). */
    TWO_PLAYERS,
    /** One person against the built-in computer opponent. */
    VS_COMPUTER;

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
