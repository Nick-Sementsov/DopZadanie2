package io.github.nicksementsov.druzhina.entity;

import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What a soldier is: his weapon and armour (see {@link Kits}), his health and how well he fights. Hired roles serve a
 * player; the bandits roam the land on their own.
 */
public enum Role {
    //            emeralds  health  skill   ranged  bandit  grade
    SWORDSMAN(8, 24, 0.5f, false, false, Kits.REGULAR),
    SPEARMAN(8, 24, 0.5f, false, false, Kits.REGULAR),
    AXEMAN(8, 26, 0.45f, false, false, Kits.REGULAR),
    WARRIOR(8, 26, 0.45f, false, false, Kits.REGULAR),
    ARCHER(10, 20, 0.45f, true, false, Kits.REGULAR),
    CROSSBOWMAN(12, 22, 0.45f, true, false, Kits.REGULAR),
    KNIGHT(28, 32, 0.75f, false, false, Kits.PICKED),
    MILITIA(4, 20, 0.3f, false, false, Kits.LEVY),
    BANDIT(0, 18, 0.25f, false, true, Kits.LEVY),
    BANDIT_ARCHER(0, 16, 0.25f, true, true, Kits.LEVY),
    CHIEFTAIN(0, 30, 0.5f, false, true, Kits.LEVY);

    /** Price of hiring one, in emeralds. */
    public final int cost;
    public final float health;
    /** 0 to 1: how quickly and how often he guards, parries, chains and feints. */
    public final float skill;
    public final boolean ranged;
    public final boolean bandit;
    /** The grade of his kit when he is hired at a muster table. */
    public final int grade;

    Role(int cost, float health, float skill, boolean ranged, boolean bandit, int grade) {
        this.cost = cost;
        this.health = health;
        this.skill = skill;
        this.ranged = ranged;
        this.bandit = bandit;
        this.grade = grade;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Role byId(int ordinal) {
        Role[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : SWORDSMAN;
    }

    public static Role byName(String id) {
        for (Role r : values()) {
            if (r.id().equals(id)) {
                return r;
            }
        }

        return null;
    }

    /** The roles a player may hire at a muster table. */
    public static Role[] forHire() {
        return new Role[]{SWORDSMAN, WARRIOR, SPEARMAN, AXEMAN, ARCHER, CROSSBOWMAN, KNIGHT};
    }

    /** Whether a player may lead one. */
    public boolean hired() {
        return !this.bandit;
    }

    /** What he fights with, to show him by. */
    public ItemStack icon() {
        return Kits.icon(this);
    }

    /** Arms and armour of a new soldier of this role, of his usual grade. */
    public void equip(Soldier s, RandomSource rnd) {
        Kits.equip(s, this, this.grade, rnd);
    }
}
