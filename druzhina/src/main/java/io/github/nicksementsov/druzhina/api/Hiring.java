package io.github.nicksementsov.druzhina.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may hire soldiers at a muster table, and how many he may lead. By itself Druzhina lets anyone with the gold hire
 * as many men as he can pay for (unless the server's setting says otherwise); a mod with towns (Prestol) may ask for
 * more, such as a barracks to keep the men in, and may tie the size of the army to how far the player's realm has
 * grown.
 */
public final class Hiring {
    /** A condition for hiring. */
    @FunctionalInterface
    public interface Check {
        /** Why {@code player} may not hire at the muster table at {@code table}, or null when he may. */
        @Nullable
        Component refuse(ServerPlayer player, BlockPos table);
    }

    /** How many soldiers a player may lead ({@link Integer#MAX_VALUE}: no limit), and why no more. */
    public record Limit(int max, @Nullable Component why) {
        public static final Limit NONE = new Limit(Integer.MAX_VALUE, null);

        public boolean unlimited() {
            return this.max == Integer.MAX_VALUE;
        }
    }

    /** Says how many soldiers a player may lead, or null to leave it to Druzhina's setting. */
    @FunctionalInterface
    public interface LimitSource {
        @Nullable
        Limit limit(ServerPlayer player);
    }

    private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();
    private static volatile LimitSource limitSource;

    private Hiring() {
    }

    /** Adds a condition every hire must meet. */
    public static void addCheck(Check check) {
        CHECKS.add(check);
    }

    /** Sets who says how many soldiers a player may lead (null: Druzhina's setting alone). */
    public static void setLimit(@Nullable LimitSource source) {
        limitSource = source;
    }

    /** How many soldiers {@code player} may lead: as the limit source says, or else as the server's setting says. */
    public static Limit limit(ServerPlayer player) {
        LimitSource source = limitSource;
        if (source != null) {
            Limit l = source.limit(player);
            if (l != null) {
                return l;
            }
        }
        int cap = io.github.nicksementsov.druzhina.DruzhinaConfig.MAX_SOLDIERS.get();
        return cap <= 0 ? Limit.NONE : new Limit(cap, null);
    }

    /** Why {@code player} may not hire at {@code table} (the first condition he fails), or null when he may. */
    @Nullable
    public static Component refusal(ServerPlayer player, BlockPos table) {
        for (Check check : CHECKS) {
            Component reason = check.refuse(player, table);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }
}
