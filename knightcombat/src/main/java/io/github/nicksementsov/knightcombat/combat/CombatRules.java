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
    /** Ticks without spending stamina before it starts to come back. */
    public static final int REGEN_DELAY = 20;

    /** Half-angle of the cone in front of a fighter that a block covers, in degrees. */
    public static final float BLOCK_HALF_ANGLE = 75;
    public static final float KICK_RANGE = 2.4f;
    public static final float KICK_HALF_ANGLE = 50;
    /** Walls stop slashes only in this part of the arc (degrees from the centre of the view). */
    public static final float WALL_ARC = 40;

    private CombatRules() {
    }
}
