package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.land.Lands;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import io.github.nicksementsov.druzhina.api.Hiring;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soldiers are hired only where there is a barracks to keep them: at Druzhina's muster table the player's colony (or a
 * colony of his realm) must have one, and a town hall hires mercenaries only for a colony that has one.
 */
public final class Barracks {
    private static boolean hooked;

    private Barracks() {
    }

    public static boolean on() {
        try {
            return PrestolConfig.HIRE_NEEDS_BARRACKS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Makes Druzhina's muster tables ask for a barracks. */
    public static void hook() {
        if (!hooked) {
            hooked = true;
            Hiring.addCheck(Barracks::refuseMuster);
        }
    }

    /** Whether the colony has a built barracks. */
    public static boolean has(IColony colony) {
        try {
            for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
                if (b instanceof BuildingBarracks && b.getBuildingLevel() > 0) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            // a colony half loaded or being torn down has no barracks to speak of
        }
        return false;
    }

    /** Whether the player has a barracks: in a colony he belongs to, or in a colony of his realm. */
    public static boolean has(ServerPlayer player) {
        for (IColony colony : Settlements.all()) {
            if (Settlements.isMember(player, colony) && has(colony)) {
                return true;
            }
        }
        for (IColony colony : Lands.colonies(player.server, player)) {
            if (has(colony)) {
                return true;
            }
        }
        return false;
    }

    /** Why a muster table will not hire for {@code player}, or null when it will. */
    @Nullable
    private static Component refuseMuster(ServerPlayer player, BlockPos table) {
        if (!on() || has(player)) {
            return null;
        }
        return Msg.bad("barracks.need_muster");
    }
}
