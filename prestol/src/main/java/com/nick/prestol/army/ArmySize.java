package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Rank;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import io.github.nicksementsov.druzhina.api.Hiring;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * How many soldiers a lord may lead (hire at Druzhina's muster table): as many as his realm can keep. Every lord has
 * his own retinue; the people of the realm's settlements, its barracks and its towers keep more, and a kingdom or an
 * empire more again. A lord without a realm counts his own colonies.
 */
public final class ArmySize {
    private static boolean hooked;

    private ArmySize() {
    }

    public static void hook() {
        if (!hooked) {
            hooked = true;
            Hiring.setLimit(ArmySize::limit);
        }
    }

    private static boolean on() {
        try {
            return PrestolConfig.ARMY_BY_REALM.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** What the realm keeps: the people, the levels of its barracks and of its towers. */
    private record Means(int citizens, int barracks, int towers) {
    }

    /** The limit for {@code player}, with what it comes from; null when the setting is off (Druzhina's own then). */
    @Nullable
    static Hiring.Limit limit(ServerPlayer player) {
        if (!on()) {
            return null;
        }
        try {
            Realm realm = Realms.realmOf(player.server, player);
            Means m = means(realm != null ? Realms.members(realm) : own(player));
            int perCitizens = Math.max(1, PrestolConfig.ARMY_CITIZENS.get());
            int fromPeople = m.citizens() / perCitizens;
            int fromBarracks = m.barracks() * PrestolConfig.ARMY_PER_BARRACKS.get();
            int fromTowers = m.towers() * PrestolConfig.ARMY_PER_TOWER.get();
            int base = PrestolConfig.ARMY_BASE.get();
            Rank rank = realm == null ? null : Realms.rank(realm);
            int bonus = rank == Rank.EMPIRE ? PrestolConfig.ARMY_EMPIRE_BONUS.get() : rank == Rank.KINGDOM ? PrestolConfig.ARMY_KINGDOM_BONUS.get() : 0;
            int max = (int) Math.floor((base + fromPeople + fromBarracks + fromTowers) * (100 + bonus) / 100.0);
            Component why = Msg.info(realm != null ? "army.limit_realm" : "army.limit_own", max, realm != null ? realm.name() : "",
                base, fromPeople, m.citizens(), fromBarracks, m.barracks(), fromTowers, m.towers(),
                bonus > 0 ? Msg.t("army.limit_bonus", Component.translatable(rank.key()), bonus) : Component.empty());
            return new Hiring.Limit(Math.max(0, max), why);
        } catch (RuntimeException e) {
            // colonies half loaded: leave it to Druzhina
            return null;
        }
    }

    /** The colonies a lord without a realm owns or governs. */
    private static List<IColony> own(ServerPlayer player) {
        List<IColony> out = new ArrayList<>();
        for (IColony c : Settlements.all()) {
            if (Settlements.isManager(player, c)) {
                out.add(c);
            }
        }
        return out;
    }

    private static Means means(List<IColony> colonies) {
        int citizens = 0;
        int barracks = 0;
        int towers = 0;
        for (IColony c : colonies) {
            citizens += Settlements.stats(c).citizens();
            try {
                for (IBuilding b : c.getServerBuildingManager().getBuildings().values()) {
                    int level = b.getBuildingLevel();
                    if (b instanceof BuildingBarracks) {
                        barracks += level;
                    } else if (b instanceof BuildingBarracksTower || b instanceof BuildingGuardTower) {
                        towers += level;
                    }
                }
            } catch (RuntimeException e) {
                // a colony being torn down keeps no men
            }
        }
        return new Means(citizens, barracks, towers);
    }
}
