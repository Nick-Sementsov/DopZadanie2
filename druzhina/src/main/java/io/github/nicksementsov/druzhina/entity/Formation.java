package io.github.nicksementsov.druzhina.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How men stand when they are told to stand somewhere: in a line, in a column or in a wedge. The places are worked
 * out the same way on the client (to show where the men will go) and on the server (to send them there).
 */
public enum Formation {
    /** Ranks across the front, as wide as the commander drew it. */
    LINE,
    /** Two or three abreast, one behind the other: for roads and gates. */
    COLUMN,
    /** A point at the front and the ranks widening behind it: to break into a line. */
    WEDGE;

    /** Between men of a rank, and between ranks. */
    public static final double FILE = 1.4;
    public static final double RANK = 1.7;
    /** Men in a rank when the commander does not say how wide. */
    public static final int DEFAULT_FILES = 8;

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Formation byId(int ordinal) {
        Formation[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : LINE;
    }

    /** How many men stand side by side in a line {@code width} blocks wide (0: the usual). */
    public static int files(double width, int count) {
        int files = width < FILE ? DEFAULT_FILES : (int) Math.round(width / FILE) + 1;
        return Mth.clamp(files, 1, Math.max(1, count));
    }

    /**
     * The places of {@code count} men, the front first: {@code center} is the middle of the front rank, {@code yaw} the
     * way they face (Minecraft's yaw), {@code width} how wide the commander drew the front (0: the usual width).
     */
    public List<Vec3> slots(int count, Vec3 center, float yaw, double width) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        // to the right of a man who faces (fx, fz)
        double rx = -fz;
        double rz = fx;
        List<Vec3> out = new ArrayList<>(count);
        switch (this) {
            case LINE, COLUMN -> {
                int files = this == LINE ? files(width, count) : Math.min(count, count > 12 ? 3 : 2);
                for (int i = 0, rank = 0; i < count; rank++) {
                    int inRank = Math.min(files, count - i);
                    for (int k = 0; k < inRank; k++, i++) {
                        double side = (k - (inRank - 1) / 2.0) * FILE;
                        double back = rank * (this == COLUMN ? FILE + 0.2 : RANK);
                        out.add(new Vec3(center.x + rx * side - fx * back, center.y, center.z + rz * side - fz * back));
                    }
                }
            }
            case WEDGE -> {
                for (int i = 0, rank = 0; i < count; rank++) {
                    int inRank = Math.min(2 * rank + 1, count - i);
                    for (int k = 0; k < inRank; k++, i++) {
                        double side = (k - (inRank - 1) / 2.0) * FILE;
                        double back = rank * RANK * 0.8;
                        out.add(new Vec3(center.x + rx * side - fx * back, center.y, center.z + rz * side - fz * back));
                    }
                }
            }
        }
        return out;
    }

    /** How many places of {@link #slots} make up each rank, front first: to keep men from crossing within a rank. */
    public List<Integer> ranks(int count, double width) {
        List<Integer> out = new ArrayList<>();
        int files = this == LINE ? files(width, count) : Math.min(count, count > 12 ? 3 : 2);
        for (int i = 0, rank = 0; i < count; rank++) {
            int inRank = this == WEDGE ? Math.min(2 * rank + 1, count - i) : Math.min(files, count - i);
            out.add(inRank);
            i += inRank;
        }
        return out;
    }
}
