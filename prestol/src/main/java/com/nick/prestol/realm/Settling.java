package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.MineColonies;
import com.nick.prestol.PrestolConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Where a new settlement may be founded. MineColonies keeps every colony 128 blocks (by default) from every other; a
 * realm is many settlements, so a lord may found one closer to his own and to those of his realm, as long as it stands
 * off their land and some way from their centres. Foreign colonies keep MineColonies' distance.
 */
public final class Settling {
    private Settling() {
    }

    /** Whether {@code player} may found a colony at {@code pos}, which MineColonies thinks too close to another. */
    public static boolean mayFoundNearOwn(@Nullable ServerPlayer player, Level level, BlockPos pos) {
        int own;
        try {
            own = PrestolConfig.OWN_SETTLEMENT_DISTANCE.get();
        } catch (IllegalStateException e) {
            return false;
        }
        if (player == null || own <= 0) {
            return false;
        }
        try {
            IColonyManager manager = IColonyManager.getInstance();
            if (manager.getColonyByPosFromWorld(level, pos) != null) {
                // on a colony's own land: nobody founds there
                return false;
            }
            var server = MineColonies.getConfig().getServer();
            int range = Math.max(server.minColonyDistance.get(), server.initialColonySize.get()) << 4;
            Realm mine = Realms.realmOf(player.server, player);
            RealmData data = RealmData.get(player.server);
            for (IColony c : manager.getColonies(level)) {
                double d = BlockPosUtil.getDistance(pos, c.getCenter());
                if (d >= range) {
                    continue;
                }
                if (!ours(player, mine, data, c) || d < own) {
                    return false;
                }
            }
            // and the land around the new town hall is not a foreign colony's
            int claim = server.initialColonySize.get();
            for (int dx = -claim; dx <= claim; dx++) {
                for (int dz = -claim; dz <= claim; dz++) {
                    IColony c = manager.getColonyByPosFromWorld(level, pos.offset(dx << 4, 0, dz << 4));
                    if (c != null && !ours(player, mine, data, c)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** His own colony, one he governs, or one of his realm. */
    private static boolean ours(ServerPlayer player, @Nullable Realm mine, RealmData data, IColony c) {
        UUID owner = Settlements.owner(c);
        if (player.getUUID().equals(owner) || Settlements.isManager(player, c)) {
            return true;
        }
        return mine != null && Realms.realmOf(data, c) == mine;
    }
}
