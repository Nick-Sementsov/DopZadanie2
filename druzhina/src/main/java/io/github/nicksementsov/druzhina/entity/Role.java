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
    //                 gold  health skill  ranged bandit grade          branch           mount        side
    SWORDSMAN(20, 24, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    DRUZHINNIK(36, 28, 0.6f, false, false, Kits.REGULAR, Branch.KNIGHTS, Mount.NONE, Culture.EAST),
    SPEARMAN(20, 24, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    SHIELDBEARER(25, 26, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    AXEMAN(22, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    MACEMAN(25, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    WARRIOR(22, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    HALBERDIER(24, 26, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.WEST),
    SARACEN(22, 24, 0.55f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.EAST),
    SKUTATOS(25, 26, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.EAST),
    BERDYSHNIK(22, 26, 0.45f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.EAST),
    ROGATINSHCHIK(20, 24, 0.5f, false, false, Kits.REGULAR, Branch.INFANTRY, Mount.NONE, Culture.EAST),
    FOOT_KNIGHT(70, 32, 0.75f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE, Culture.WEST),
    CRUSADER(65, 32, 0.7f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE, Culture.WEST),
    KNIGHT(75, 32, 0.75f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE, Culture.EAST),
    VARANGIAN(45, 30, 0.65f, false, false, Kits.PICKED, Branch.KNIGHTS, Mount.NONE, Culture.EAST),
    ARCHER(24, 20, 0.45f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE, Culture.WEST),
    CROSSBOWMAN(30, 22, 0.45f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE, Culture.WEST),
    STEPPE_ARCHER(24, 20, 0.5f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE, Culture.EAST),
    SAMOSTRELSHCHIK(30, 22, 0.45f, true, false, Kits.REGULAR, Branch.RANGED, Mount.NONE, Culture.EAST),
    HORSEMAN(60, 26, 0.55f, false, false, Kits.REGULAR, Branch.CAVALRY, Mount.HORSE, Culture.EAST),
    SERGEANT(55, 26, 0.55f, false, false, Kits.REGULAR, Branch.CAVALRY, Mount.HORSE, Culture.WEST),
    LANCER(100, 32, 0.7f, false, false, Kits.PICKED, Branch.CAVALRY, Mount.HORSE, Culture.WEST),
    CATAPHRACT(110, 34, 0.7f, false, false, Kits.PICKED, Branch.CAVALRY, Mount.HORSE, Culture.EAST),
    MAMLUK(65, 28, 0.65f, false, false, Kits.PICKED, Branch.CAVALRY, Mount.HORSE, Culture.EAST),
    HORSE_ARCHER(55, 22, 0.5f, true, false, Kits.REGULAR, Branch.CAVALRY, Mount.HORSE, Culture.EAST),
    MAHOUT(160, 24, 0.5f, false, false, Kits.REGULAR, Branch.ELEPHANTS, Mount.ELEPHANT, Culture.EAST),
    /** Siege engines: hired with the man who works them (and who fights with a short blade when it is lost). */
    TREBUCHET(200, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.TREBUCHET, Culture.ANY),
    BALLISTA(90, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.BALLISTA, Culture.ANY),
    RAM(120, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.RAM, Culture.ANY),
    MANGONEL(110, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.MANGONEL, Culture.ANY),
    BOMBARD(260, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.BOMBARD, Culture.ANY),
    SIEGE_TOWER(150, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.SIEGE_TOWER, Culture.ANY),
    MANTLET(40, 24, 0.35f, false, false, Kits.REGULAR, Branch.SIEGE, Mount.MANTLET, Culture.ANY),
    MILITIA(8, 20, 0.3f, false, false, Kits.LEVY, Branch.NONE, Mount.NONE, Culture.NONE),
    BANDIT(0, 18, 0.25f, false, true, Kits.LEVY, Branch.NONE, Mount.NONE, Culture.NONE),
    BANDIT_ARCHER(0, 16, 0.25f, true, true, Kits.LEVY, Branch.NONE, Mount.NONE, Culture.NONE),
    CHIEFTAIN(0, 30, 0.5f, false, true, Kits.LEVY, Branch.NONE, Mount.NONE, Culture.NONE);

    /** Where a soldier comes from, and so how he is armed: the muster table is turned to one side or the other. */
    public enum Culture {
        /** The Latin West: mail and plate, heater shields, arming swords, halberds, longbows and crossbows. */
        WEST,
        /** The East: the Rus, Byzantium, the Saracens and the steppe: scale and lamellar, round shields, sabres. */
        EAST,
        /** Militia and bandits: whoever they are. */
        NONE,
        /** Hired on either side (siege engines: everyone builds them alike). */
        ANY;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /** The two sides of the muster table. */
        public static Culture[] sides() {
            return new Culture[]{WEST, EAST};
        }
    }

    /** The tabs of the muster table. */
    public enum Branch {
        INFANTRY,
        KNIGHTS,
        RANGED,
        CAVALRY,
        ELEPHANTS,
        SIEGE,
        /** Not hired at a muster table. */
        NONE;

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        /** The tabs, in order. */
        public static Branch[] tabs() {
            return new Branch[]{INFANTRY, KNIGHTS, RANGED, CAVALRY, ELEPHANTS, SIEGE};
        }

        /** The tabs that have men of {@code culture} to hire. */
        public static Branch[] tabs(Culture culture) {
            List<Branch> out = new ArrayList<>();
            for (Branch b : tabs()) {
                if (forHire(culture, b).length > 0) {
                    out.add(b);
                }
            }
            return out.toArray(new Branch[0]);
        }
    }

    /** What he rides into battle. */
    public enum Mount {
        NONE,
        HORSE,
        /** A war elephant: he drives it, and an archer shoots from the howdah on its back. */
        ELEPHANT,
        /** Siege engines: he moves it and works it, standing at it. */
        TREBUCHET,
        BALLISTA,
        RAM,
        MANGONEL,
        BOMBARD,
        SIEGE_TOWER,
        MANTLET;

        /** A siege engine rather than a beast. */
        public boolean engine() {
            return this.ordinal() >= TREBUCHET.ordinal();
        }
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
    public final Culture culture;

    Role(int cost, float health, float skill, boolean ranged, boolean bandit, int grade, Branch branch, Mount mount, Culture culture) {
        this.cost = cost;
        this.health = health;
        this.skill = skill;
        this.ranged = ranged;
        this.bandit = bandit;
        this.grade = grade;
        this.branch = branch;
        this.mount = mount;
        this.culture = culture;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The names his kind are given: those of the West, of the Saracens and the steppe, of the Greeks, of the Norsemen,
     * or (an empty string) those of the Rus.
     */
    public String names() {
        return switch (this) {
            case SARACEN, MAMLUK, STEPPE_ARCHER, HORSE_ARCHER, MAHOUT -> "east";
            case SKUTATOS, CATAPHRACT -> "greek";
            case VARANGIAN -> "norse";
            default -> this.culture == Culture.WEST ? "west" : "";
        };
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

    /** The roles of one tab of the muster table turned to {@code culture}. */
    public static Role[] forHire(Culture culture, Branch branch) {
        List<Role> out = new ArrayList<>();
        for (Role r : values()) {
            if (r.branch == branch && (r.culture == culture || r.culture == Culture.ANY)) {
                out.add(r);
            }
        }
        return out.toArray(new Role[0]);
    }

    /** Whether a player may lead one. */
    public boolean hired() {
        return !this.bandit;
    }

    /** On a horse or an elephant (not at a siege engine). */
    public boolean mounted() {
        return this.mount != Mount.NONE && !this.mount.engine();
    }

    /** He works a siege engine. */
    public boolean engineer() {
        return this.mount.engine();
    }

    /** Hired as a whole (an elephant with its crew, an engine with its man): the muster table names the whole. */
    public boolean unit() {
        return this.crew() > 1 || this.engineer();
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
