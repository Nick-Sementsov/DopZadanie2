package io.github.nicksementsov.druzhina.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may hire soldiers at a muster table. By itself Druzhina lets anyone with the gold hire; a mod with towns
 * (Prestol) may ask for more, such as a barracks to keep the men in.
 */
public final class Hiring {
    /** A condition for hiring. */
    @FunctionalInterface
    public interface Check {
        /** Why {@code player} may not hire at the muster table at {@code table}, or null when he may. */
        @Nullable
        Component refuse(ServerPlayer player, BlockPos table);
    }

    private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();

    private Hiring() {
    }

    /** Adds a condition every hire must meet. */
    public static void addCheck(Check check) {
        CHECKS.add(check);
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
