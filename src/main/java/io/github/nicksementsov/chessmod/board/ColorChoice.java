package io.github.nicksementsov.chessmod.board;

import io.github.nicksementsov.chessmod.chess.Side;
import net.minecraft.util.RandomSource;

import java.util.Locale;

/** Which side the player wants to take against the computer. */
public enum ColorChoice {
    WHITE,
    BLACK,
    RANDOM;

    public Side resolve(RandomSource random) {
        return switch (this) {
            case WHITE -> Side.WHITE;
            case BLACK -> Side.BLACK;
            case RANDOM -> random.nextBoolean() ? Side.WHITE : Side.BLACK;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ColorChoice byName(String name, ColorChoice fallback) {
        for (ColorChoice choice : values()) {
            if (choice.serializedName().equals(name)) {
                return choice;
            }
        }
        return fallback;
    }
}
