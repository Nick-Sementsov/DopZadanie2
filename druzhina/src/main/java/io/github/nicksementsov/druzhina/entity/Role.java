package io.github.nicksementsov.druzhina.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What a soldier is: his weapon and armour (see {@link Kits}), what he rides (see {@link Mounts}), his health and how
 * well he fights. Hired roles serve a player; the bandits roam the land on their own.
 */
public enum Role {
    //               gold  health  skill   ranged  bandit  grade          branch           mount
    SWORDSMAN(8, 24, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    DRUZHINNIK(14, 28, 0.6f, false, false, Kits.REGULAR, Branch.KNIGHTS, Mount.NONE),
    SPEARMAN(8, 24, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    SHIELDBEARER(10, 26, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    AXEMAN(8, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    MACEMAN(10, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    WARRIOR(8, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE),
    FOOT_KNIGHT(28, 32, 0.75f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE),
    KNIGHT(28, 32, 0.75f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE),
    ARCHER(10, 20, 0.45f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE),
    CROSSBOWMAN(12, 22, 0.45f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE),
    HORSEMAN(24, 26, 0.55f, false, false, Kits.REGULAR, Branch.CAVALRY, Mount.HORSE),
    LANCER(40, 32, 0.7f, false, false, Kits.PICKED, Branch.CAVALRY, Mount.HORSE),
    HORSE_ARCHER(22, 22, 0.5f, true, false, Kits.REGULAR, Branch.CAVALRY, Mount.HORSE),
    MAHOUT(64, 24, 0.5f, false, false, Kits.REGULAR, Branch.ELEPHANTS, Mount.ELEPHANT),
    MILITIA(4, 20, 0.3f, false, false, Kits.LEVY, Branch.NONE, Mount.NONE),
    BANDIT(0, 18, 0.25f, false, true, Kits.LEVY, Branch.NONE, Mount.NONE),
    BANDIT_ARCHER(0, 16, 0.25f, true, true, Kits.LEVY, Branch.NONE, Mount.NONE),
    CHIEFTAIN(0, 30, 0.5f, false, true, Kits.LEVY, Branch.NONE, Mount.NONE);

    /** The tabs of the muster table. */
    public enum Branch {
        INFANTRY,
        KNIGHTS,
        RANGED,
        CAVALRY,
        ELEPHANTS,
        /** Not hired at a muster table. */
        NONE;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /** The tabs, in order. */
        public static Branch[] tabs() {
            return new Branch[]{INFANTRY, KNIGHTS, RANGED, CAVALRY, ELEPHANTS};
        }
    }

    /** What he rides into battle. */
    public enum Mount {
        NONE,
        HORSE,
        /** A war elephant: he drives it, and an archer shoots from the howdah on its back. */
        ELEPHANT
    }

    /** Price of hiring one (with his horse or his elephant and its archer), in gold ingots. */
    public final int cost;
    public final float health;
    /** 0 to 1: how quickly and how often he guards, parries, chains and feints. */
    public final float skill;
    public final boolean ranged;
    public final boolean bandit;
    /** The grade of his kit when he is hired at a muster table. */
    public final int grade;
    public final Branch branch;
    public final Mount mount;

    Role(int cost, float health, float skill, boolean ranged, boolean bandit, int grade, Branch branch, Mount mount) {
        this.cost = cost;
        this.health = health;
        this.skill = skill;
        this.ranged = ranged;
        this.bandit = bandit;
        this.grade = grade;
        this.branch = branch;
        this.mount = mount;
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
        List<Role> out = new ArrayList<>();
        for (Role r : values()) {
            if (r.branch != Branch.NONE) {
                out.add(r);
            }
        }
        return out.toArray(new Role[0]);
    }

    /** The roles of one tab of the muster table. */
    public static Role[] forHire(Branch branch) {
        List<Role> out = new ArrayList<>();
        for (Role r : values()) {
            if (r.branch == branch) {
                out.add(r);
            }
        }
        return out.toArray(new Role[0]);
    }

    /** Whether a player may lead one. */
    public boolean hired() {
        return !this.bandit;
    }

    public boolean mounted() {
        return this.mount != Mount.NONE;
    }

    /** How many men come with one hire: an elephant brings its driver and the archer in its howdah. */
    public int crew() {
        return this.mount == Mount.ELEPHANT ? 2 : 1;
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
