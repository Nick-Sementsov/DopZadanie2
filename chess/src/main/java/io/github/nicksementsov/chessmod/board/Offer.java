package io.github.nicksementsov.chessmod.board;

import java.util.Locale;

/** A request one player makes to the other in a two-player game. */
public enum Offer {
    NONE,
    TAKEBACK,
    DRAW;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Offer byName(String name) {
        for (Offer offer : values()) {
            if (offer.serializedName().equals(name)) {
                return offer;
            }
        }
        return NONE;
    }
}
