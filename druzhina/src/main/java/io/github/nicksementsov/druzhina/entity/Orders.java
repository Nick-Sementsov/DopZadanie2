package io.github.nicksementsov.druzhina.entity;

import java.util.Locale;

/** What a soldier was told to do by his commander. */
public enum Orders {
    /** Walk behind the commander and fall upon whoever threatens him or whom he attacks. */
    FOLLOW,
    /** Stand at his post; fight what comes close and go back there. */
    HOLD,
    /** Fall upon every enemy around. */
    CHARGE;

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Orders byId(int ordinal) {
        Orders[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FOLLOW;
    }

    public Orders next() {
        return byId((this.ordinal() + 1) % values().length);
    }
}
