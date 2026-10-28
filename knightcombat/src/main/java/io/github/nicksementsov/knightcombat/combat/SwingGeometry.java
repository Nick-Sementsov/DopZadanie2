package io.github.nicksementsov.knightcombat.combat;

/**
 * Where the weapon is during the release, as angles relative to the view of the attacker.
 * The server traces rays along these angles to find what the weapon hits.
 * Horizontal angles are positive to the right, vertical angles are positive downwards.
 */
public final class SwingGeometry {
    /** Slashes sweep from this angle on one side to the same angle on the other side. */
    public static final float SLASH_ARC = 80;
    /** The slash of a whirlwind sweeps wider. */
    public static final float WIDE_ARC = 115;
    /** Slashes go slightly downwards: from this angle above the view to the same angle below it. */
    public static final float SLASH_TILT = 12;
    /** The slash of a hamstring goes low, from the hips down to the shins of an enemy in front. */
    public static final float LOW_FROM = 10;
    public static final float LOW_TO = 34;
    public static final float OVERHEAD_START = -75;
    public static final float OVERHEAD_END = 40;
    public static final float OVERHEAD_DRIFT = 6;
    /** A stab starts at this share of the reach and ends at the full reach. */
    public static final float STAB_START = 0.45f;
    /** Spread of the side rays of a stab, in degrees. */
    public static final float STAB_SPREAD = 4;
    private static final float SAMPLE_STEP = 5;

    private SwingGeometry() {
    }

    /** Horizontal angle of the weapon at release progress {@code p} (0..1). */
    public static float yaw(AttackType type, boolean mirrored, float p) {
        return yaw(type, mirrored, p, Combo.NONE);
    }

    /** Horizontal angle of the weapon at release progress {@code p} (0..1) of an attack that may be a finisher. */
    public static float yaw(AttackType type, boolean mirrored, float p, Combo combo) {
        float arc = combo == Combo.WHIRLWIND ? WIDE_ARC : SLASH_ARC;
        float yaw = switch (type) {
            case SLASH -> lerp(arc, -arc, p);
            case OVERHEAD -> lerp(OVERHEAD_DRIFT, -OVERHEAD_DRIFT, p);
            default -> 0;
        };
        return mirrored ? -yaw : yaw;
    }

    /** Vertical angle of the weapon at release progress {@code p} (0..1). */
    public static float pitch(AttackType type, float p) {
        return pitch(type, p, Combo.NONE);
    }

    /** Vertical angle of the weapon at release progress {@code p} (0..1) of an attack that may be a finisher. */
    public static float pitch(AttackType type, float p, Combo combo) {
        return switch (type) {
            case SLASH -> combo == Combo.HAMSTRING ? lerp(LOW_FROM, LOW_TO, p) : lerp(-SLASH_TILT, SLASH_TILT, p);
            case OVERHEAD -> lerp(OVERHEAD_START, OVERHEAD_END, p);
            default -> 0;
        };
    }

    /** Share of the weapon reach covered at release progress {@code p}. Only stabs grow over the release. */
    public static float reachFactor(AttackType type, float p) {
        if (type != AttackType.STAB) {
            return 1;
        }
        float eased = 1 - (1 - p) * (1 - p);
        return STAB_START + (1 - STAB_START) * eased;
    }

    /** How many rays to trace between release progress p0 and p1 so that rays are at most a few degrees apart. */
    public static int samples(AttackType type, float p0, float p1) {
        return samples(type, p0, p1, Combo.NONE);
    }

    public static int samples(AttackType type, float p0, float p1, Combo combo) {
        float degrees = switch (type) {
            case SLASH -> 2 * (combo == Combo.WHIRLWIND ? WIDE_ARC : SLASH_ARC);
            case OVERHEAD -> OVERHEAD_END - OVERHEAD_START;
            default -> 3 * SAMPLE_STEP;
        };
        return Math.max(1, (int) Math.ceil(degrees * Math.abs(p1 - p0) / SAMPLE_STEP));
    }

    /** Whether a wall met at this point of the arc stops the attack (walls at the far ends of a slash do not). */
    public static boolean wallStops(AttackType type, boolean mirrored, float p) {
        return wallStops(type, mirrored, p, Combo.NONE);
    }

    public static boolean wallStops(AttackType type, boolean mirrored, float p, Combo combo) {
        return type != AttackType.SLASH || Math.abs(yaw(type, mirrored, p, combo)) <= CombatRules.WALL_ARC;
    }

    /**
     * Direction of a ray at the given angles in the frame of the view: {@code forward}, {@code up} and {@code right}
     * are unit vectors, the result is written to {@code out}.
     */
    public static void direction(double[] forward, double[] up, double[] right, float yawDeg, float pitchDeg, double[] out) {
        double h = Math.toRadians(yawDeg);
        double v = Math.toRadians(pitchDeg);
        double ch = Math.cos(h), sh = Math.sin(h), cv = Math.cos(v), sv = Math.sin(v);
        for (int i = 0; i < 3; i++) {
            out[i] = cv * (ch * forward[i] + sh * right[i]) - sv * up[i];
        }
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
