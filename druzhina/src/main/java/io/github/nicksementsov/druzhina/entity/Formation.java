package io.github.nicksementsov.druzhina.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How men stand when they are told to stand somewhere: in a line, a column, a wedge, a square or loose. The places are
 * worked out the same way on the client (to show where the men will go) and on the server (to send them there). Men
 * on foot stand in front; riders, who need more room, stand in ranks of their own behind them.
 */
public enum Formation {
    /** Ranks across the front, as wide as the commander drew it. */
    LINE,
    /** Two or three abreast, one behind the other: for roads and gates. */
    COLUMN,
    /** A point at the front and the ranks widening behind it: to break into a line. */
    WEDGE,
    /** As many ranks as files: a block that holds against horse and is hard to go round. */
    SQUARE,
    /** A line with wide gaps: archers against archers, few struck by one blow or one ball. */
    LOOSE;

    /** Between men of a rank, and between ranks. */
    public static final double FILE = 1.4;
    public static final double RANK = 1.7;
    /** How much more room a rider needs than a man on foot. */
    public static final double HORSE = 1.6;
    /** Men in a rank when the commander does not say how wide. */
    public static final int DEFAULT_FILES = 8;

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Formation byId(int ordinal) {
        Formation[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : LINE;
    }

    public Formation next() {
        Formation[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    /** How many men stand side by side in a line {@code width} blocks wide (0: the usual). */
    public static int files(double width, int count) {
        return files(width, count, 1.0);
    }

    private static int files(double width, int count, double spacing) {
        int files = width < FILE * spacing ? DEFAULT_FILES : (int) Math.round(width / (FILE * spacing)) + 1;
        return Mth.clamp(files, 1, Math.max(1, count));
    }

    /** How much room between men: wide for a loose line. */
    private double spacing() {
        return this == LOOSE ? 2.2 : 1.0;
    }

    /** Men in each rank, the front first. */
    private List<Integer> rankSizes(int count, double width, double spacing) {
        List<Integer> out = new ArrayList<>();
        int files = switch (this) {
            case LINE, LOOSE -> files(width, count, spacing);
            case COLUMN -> Math.min(count, count > 12 ? 3 : 2);
            case SQUARE -> Math.max(1, (int) Math.ceil(Math.sqrt(count)));
            case WEDGE -> 0;
        };
        for (int i = 0, rank = 0; i < count; rank++) {
            int inRank = this == WEDGE ? Math.min(2 * rank + 1, count - i) : Math.min(files, count - i);
            out.add(inRank);
            i += inRank;
        }
        return out;
    }

    private double rankStep(double spacing) {
        return switch (this) {
            case COLUMN -> FILE + 0.2;
            case WEDGE -> RANK * 0.8;
            case SQUARE -> FILE * 1.05;
            default -> RANK;
        } * spacing;
    }

    /** From the front rank to the last one. */
    private double depth(int count, double width, double spacing) {
        return Math.max(0, this.rankSizes(count, width, spacing).size() - 1) * this.rankStep(spacing);
    }

    /**
     * The places of {@code count} men on foot, the front first: {@code center} is the middle of the front rank,
     * {@code yaw} the way they face (Minecraft's yaw), {@code width} how wide the commander drew the front (0: the
     * usual width).
     */
    public List<Vec3> slots(int count, Vec3 center, float yaw, double width) {
        return this.slots(count, 0, center, yaw, width);
    }

    /** The places of {@code foot} men on foot in front, then of {@code horse} riders in ranks behind them. */
    public List<Vec3> slots(int foot, int horse, Vec3 center, float yaw, double width) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        List<Vec3> out = new ArrayList<>(foot + horse);
        this.place(out, foot, center, fx, fz, width, this.spacing());
        if (horse > 0) {
            double back = foot == 0 ? 0 : this.depth(foot, width, this.spacing()) + RANK * HORSE;
            Vec3 behind = new Vec3(center.x - fx * back, center.y, center.z - fz * back);
            this.place(out, horse, behind, fx, fz, width, this.spacing() * HORSE);
        }
        return out;
    }

    private void place(List<Vec3> out, int count, Vec3 center, double fx, double fz, double width, double spacing) {
        // to the right of a man who faces (fx, fz)
        double rx = -fz;
        double rz = fx;
        double step = this.rankStep(spacing);
        int rank = 0;
        for (int inRank : this.rankSizes(count, width, spacing)) {
            for (int k = 0; k < inRank; k++) {
                double side = (k - (inRank - 1) / 2.0) * FILE * spacing;
                double back = rank * step;
                out.add(new Vec3(center.x + rx * side - fx * back, center.y, center.z + rz * side - fz * back));
            }
            rank++;
        }
    }

    /** How many places of {@link #slots} make up each rank, front first: to keep men from crossing within a rank. */
    public List<Integer> ranks(int count, double width) {
        return this.ranks(count, 0, width);
    }

    public List<Integer> ranks(int foot, int horse, double width) {
        List<Integer> out = new ArrayList<>(this.rankSizes(foot, width, this.spacing()));
        out.addAll(this.rankSizes(horse, width, this.spacing() * HORSE));
        return out;
    }
}
