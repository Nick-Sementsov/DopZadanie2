package io.github.nicksementsov.knightcombat.combat;

/** Timings (in ticks, 20 per second) and costs shared by all weapons. */
public final class CombatRules {
    /** Ticks after pressing block during which a block counts as a parry. */
    public static final int PARRY_WINDOW = 6;
    /** Ticks after a parry during which an attack becomes a riposte. */
    public static final int RIPOSTE_WINDOW = 14;
    /** First ticks of a wind-up that counter an incoming attack of the same kind. */
    public static final int COUNTER_WINDOW = 5;
    /** Wind-up ticks left after a successful counter. */
    public static final int COUNTER_REMAINING = 4;
    /** Pause before the next block after a block that caught nothing. */
    public static final int BLOCK_RECOVERY = 6;
    public static final int FEINT_RECOVERY = 5;
    /** Stagger after being hit during a wind-up. */
    public static final int FLINCH = 7;
    /** Stagger after running out of stamina while blocking. */
    public static final int GUARD_BREAK = 26;
    public static final int KICK_WINDUP = 6;
    public static final int KICK_RECOVERY = 9;
    /** Stagger of a blocking fighter who was kicked. */
    public static final int KICK_STAGGER = 14;
    public static final int CRY_LENGTH = 22;
    /** Shortest time between two effort shouts of a fighter. */
    public static final int YELL_COOLDOWN = 30;
    public static final int CRY_EFFECT_TICK = 4;
    /** Extra recovery when the attack was blocked, parried or hit a wall. */
    public static final int REBOUND_BLOCK = 3;
    public static final int REBOUND_PARRY = 6;
    public static final int REBOUND_WALL = 5;
    /** Wind-up multipliers of ripostes and combo attacks. */
    public static final float RIPOSTE_WINDUP = 0.6f;
    public static final float COMBO_WINDUP = 0.8f;
    /** A morph (switching to another attack during the wind-up) is allowed in this share of the wind-up. */
    public static final float MORPH_LIMIT = 0.7f;

    /** A heavy attack (the key held through the wind-up) winds up this much longer, as a share of its wind-up. */
    public static final float HEAVY_WINDUP = 0.6f;
    public static final float HEAVY_DAMAGE = 1.45f;
    /** Multiplier of the stamina a blocker loses to a heavy attack. */
    public static final float HEAVY_STAMINA_DAMAGE = 1.6f;
    public static final float HEAVY_KNOCKBACK = 1.5f;
    public static final int HEAVY_RECOVERY = 3;
    /** An attack started while sprinting: stronger, a little faster, with a longer recovery. */
    public static final float SPRINT_DAMAGE = 1.25f;
    public static final float SPRINT_WINDUP = 0.9f;
    public static final int SPRINT_RECOVERY = 3;
    /** The strongest an attack can get from being heavy, a finisher or a running attack together. */
    public static final float MAX_DAMAGE_BONUS = 2.0f;
    /** Ticks after a kick that landed during which an attack is a fast follow-up. */
    public static final int KICK_FOLLOW_WINDOW = 16;
    /** Ticks a hamstring slows the one it cuts. */
    public static final int HAMSTRING_SLOW = 40;
    public static final int DODGE_LENGTH = 7;
    /** Pause after a dodge before the next one. */
    public static final int DODGE_COOLDOWN = 8;
    public static final int THROW_RECOVERY = 8;
    /** A shield bash comes out faster than a kick, reaches a little less and pushes harder. */
    public static final int SHIELD_BASH_WINDUP = 4;
    public static final float SHIELD_BASH_RANGE = 2.2f;
    public static final float SHIELD_BASH_DAMAGE = 2;
    /** Axes, maces and polearms that break shields drain this much more stamina from a shield that stops them. */
    public static final float SHIELD_BREAKER = 1.5f;
    /** Projectiles cost a shield bearer only this share of what they cost a weapon block. */
    public static final float SHIELD_PROJECTILE = 0.5f;
    /** Stagger of a fighter whose shield was knocked aside by a heavy blow. */
    public static final int SHIELD_KNOCKED = 12;
    /** Ticks a knocked-aside shield cannot be raised (Minecraft's own axe disables it for 5 seconds). */
    public static final int SHIELD_KICKED_COOLDOWN = 60;
    /** Damage of a couched lance as a multiple of the weapon's attack damage: at a trot and at a full gallop. */
    public static final float LANCE_SLOW = 0.8f;
    public static final float LANCE_FAST = 3.0f;
    public static final float LANCE_MAX_DAMAGE = 40;
    /** Stamina a guard loses to a lance at full gallop. */
    public static final float LANCE_DRAIN = 60;
    /** A lance strike this strong throws a rider out of the saddle. */
    public static final float LANCE_DISMOUNT = 0.55f;
    /** A lance strike this strong cracks the shaft (and the shield it meets). */
    public static final float LANCE_SPLINTERS = 0.6f;
    /** Ticks the lance is thrown up after it strikes, and before it can strike again. */
    public static final int LANCE_RECOIL = 12;
    public static final int LANCE_COOLDOWN = 18;
    /** Degrees a couched lance dips below the rider's look: aimed at the head of a man on foot, it takes him in the chest. */
    public static final float LANCE_DIP = 4;

    public static final float FEINT_COST = 10;
    public static final float MORPH_COST = 6;
    public static final float MISS_COST = 6;
    public static final float KICK_COST = 12;
    /** Stamina a blocker loses when kicked. */
    public static final float KICK_DRAIN = 22;
    public static final float PARRY_FACTOR = 0.5f;
    public static final float COUNTER_FACTOR = 0.5f;
    public static final float SHIELD_FACTOR = 0.4f;
    /** Stamina lost per point of damage when blocking a mob or a projectile. */
    public static final float MOB_BLOCK_COST = 4;
    public static final float PROJECTILE_BLOCK_COST = 2.5f;
    public static final float CRY_STAMINA = 35;
    public static final float HEAVY_COST = 8;
    public static final float DODGE_COST = 14;
    public static final float THROW_COST = 12;
    /** Ticks without spending stamina before it starts to come back. */
    public static final int REGEN_DELAY = 20;
    /** Share of the stamina below which a fighter is out of breath, above which they have got it back, and the ticks between two gasps. */
    public static final float WINDED = 0.15f;
    public static final float RECOVERED = 0.45f;
    public static final int BREATH_INTERVAL = 40;

    /** Half-angle of the cone in front of a fighter that a block covers, in degrees. */
    public static final float BLOCK_HALF_ANGLE = 75;
    public static final float KICK_RANGE = 2.4f;
    public static final float KICK_HALF_ANGLE = 50;
    /** Walls stop slashes only in this part of the arc (degrees from the centre of the view). */
    public static final float WALL_ARC = 40;

    private CombatRules() {
    }

    /** Ticks a fighter draws the weapon back before throwing it: heavy weapons take longer. */
    public static int throwWindup(WeaponStats weapon) {
        return 7 + Math.round(5 * weapon.weight());
    }

    /** Wind-up ticks of an attack: ripostes, combos, finishers and running attacks come out faster. */
    public static int windup(WeaponStats weapon, AttackType type, boolean riposte, boolean combo, Combo finisher, boolean sprint) {
        float length = weapon.attack(type).windup();
        if (riposte) {
            length *= RIPOSTE_WINDUP;
        } else if (combo) {
            length *= COMBO_WINDUP;
        }
        length *= finisher.windup;
        if (sprint) {
            length *= SPRINT_WINDUP;
        }
        return Math.max(3, Math.round(length));
    }

    /** Heavy and running attacks take longer to recover from. */
    public static int extraRecovery(int flags) {
        return ((flags & CombatState.HEAVY) != 0 ? HEAVY_RECOVERY : 0) + ((flags & CombatState.SPRINT) != 0 ? SPRINT_RECOVERY : 0);
    }

    /** Extra wind-up of a heavy attack. */
    public static int heavyWindup(WeaponStats weapon, AttackType attack) {
        return Math.max(4, Math.round(weapon.attack(attack).windup() * HEAVY_WINDUP));
    }
}
