package com.nick.prestol.compat;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.nick.prestol.Prestol;
import com.nick.prestol.army.Morale;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.CourtData;
import com.nick.prestol.realm.Fealty;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.war.Wars;
import io.github.nicksementsov.druzhina.api.DruzhinaApi;
import io.github.nicksementsov.druzhina.api.Relations;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

/**
 * The soldiers of the realms are the soldiers of Druzhina: men who fight by the rules of Knightly Combat, armed from
 * Epic Knights. Garrisons, militia, mercenaries and caravan guards are raised, ordered and trained through here, and
 * the realms' alliances, wars and outlaws decide whom they fight: once a war is on (after the mobilisation), the
 * soldiers of each side fall upon everyone who fights for the other on sight, its lords and its colonies' guards too,
 * and the guards of each side's colonies fall upon the other's soldiers.
 */
public final class Troops {
    /** The kinds of unit Prestol raises and the Druzhina role of each. */
    private static final Map<String, String> ROLES = Map.of(
        "warrior", "warrior",
        "spear_man", "spearman",
        "shieldman", "swordsman",
        "archer", "archer",
        "crossbowman", "crossbowman",
        "militia", "militia"
    );

    /** Who serves which realm, which realms stand together, which are at war, and whom each outlaws. */
    private record Snapshot(Map<UUID, UUID> realmOf, Set<Long> allied, Set<Long> atWar, Map<UUID, Set<UUID>> outlaws) {
    }

    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of(), Set.of(), Map.of());
    private static boolean hooked;

    private Troops() {
    }

    /** Druzhina is a required mod: the army is always there. */
    public static boolean present() {
        return true;
    }

    // ------------------------------------------------------------------ sides

    /** Called every realm tick: who stands with whom, for the soldiers to know their friends and foes. */
    public static void sync(MinecraftServer server, RealmData data) {
        if (!hooked) {
            hooked = true;
            Relations.setProvider(Troops::stance);
            Relations.setRouted(Morale::routed);
            Relations.setSideOf(Troops::sideOf);
        }
        try {
            Map<UUID, UUID> realmOf = new HashMap<>();
            for (Realm r : data.all()) {
                realmOf.putIfAbsent(r.ruler(), r.id());
            }
            for (Realm r : data.all()) {
                // and those who govern its settlements for it
                for (UUID p : Wars.people(r)) {
                    realmOf.putIfAbsent(p, r.id());
                }
            }
            for (Realm r : data.all()) {
                for (IColony c : Realms.members(r)) {
                    UUID owner = Settlements.owner(c);
                    if (owner != null) {
                        realmOf.putIfAbsent(owner, r.id());
                    }
                }
            }
            Set<Long> allied = new HashSet<>();
            Set<Long> atWar = new HashSet<>();
            List<Realm> realms = List.copyOf(data.all());
            for (int i = 0; i < realms.size(); i++) {
                for (int j = i + 1; j < realms.size(); j++) {
                    Realm a = realms.get(i);
                    Realm b = realms.get(j);
                    if (Wars.fighting(server, data, a, b)) {
                        // a war in earnest, not one still mobilising
                        atWar.add(pair(a.id(), b.id()));
                    } else if (a.allies().contains(b.id()) && b.allies().contains(a.id()) || Fealty.on() && Fealty.bound(server, a, b)) {
                        allied.add(pair(a.id(), b.id()));
                    }
                }
            }
            Map<UUID, Set<UUID>> outlaws = new HashMap<>();
            if (Court.on()) {
                CourtData court = CourtData.get(server);
                for (Realm r : realms) {
                    Set<UUID> o = court.outlawsOf(r.id());
                    if (!o.isEmpty()) {
                        outlaws.put(r.id(), Set.copyOf(o));
                    }
                }
            }
            snapshot = new Snapshot(Map.copyOf(realmOf), Set.copyOf(allied), Set.copyOf(atWar), Map.copyOf(outlaws));
        } catch (RuntimeException e) {
            Prestol.LOGGER.warn("Prestol: could not tell the soldiers who stands with whom", e);
        }
    }

    public static void onRealmRemoved(MinecraftServer server, Realm realm) {
        sync(server, RealmData.get(server));
    }

    private static long pair(UUID a, UUID b) {
        long x = a.getMostSignificantBits() ^ a.getLeastSignificantBits();
        long y = b.getMostSignificantBits() ^ b.getLeastSignificantBits();
        return x < y ? x * 31 + y : y * 31 + x;
    }

    /** The stance between two lords: one realm or allies stand together, realms at war and outlaws are foes. */
    private static Relations.Stance stance(UUID a, UUID b) {
        Snapshot s = snapshot;
        UUID ra = s.realmOf.get(a);
        UUID rb = s.realmOf.get(b);
        if (ra != null && s.outlaws.getOrDefault(ra, Set.of()).contains(b) || rb != null && s.outlaws.getOrDefault(rb, Set.of()).contains(a)) {
            return Relations.Stance.ENEMY;
        }
        if (ra == null || rb == null) {
            return Relations.Stance.NEUTRAL;
        }
        if (ra.equals(rb) || s.allied.contains(pair(ra, rb))) {
            return Relations.Stance.ALLY;
        }
        return s.atWar.contains(pair(ra, rb)) ? Relations.Stance.ENEMY : Relations.Stance.NEUTRAL;
    }

    /** The lord a colony's guard fights for: the owner of his colony. Other creatures fight for nobody. */
    @Nullable
    private static UUID sideOf(Entity e) {
        if (e instanceof AbstractEntityCitizen citizen && !e.level().isClientSide) {
            try {
                ICitizenData data = citizen.getCitizenData();
                if (data != null && data.getJob() instanceof AbstractJobGuard) {
                    IColony colony = citizen.getCitizenColonyHandler().getColonyOrRegister();
                    return colony == null ? null : Settlements.owner(colony);
                }
            } catch (RuntimeException ex) {
                // a citizen half loaded fights for nobody yet
            }
        }
        return null;
    }

    /**
     * Whether a guard of a colony falls upon {@code target}: a bandit, or a soldier (or a guard) of a side at war with
     * the colony's lord. Players are left to MineColonies (an enemy realm's people are made hostile in the colony).
     */
    public static boolean enemyOfGuard(AbstractEntityCitizen guard, @Nullable Entity target) {
        if (target == null || target instanceof Player || !target.isAlive()) {
            return false;
        }
        try {
            IColony colony = guard.getCitizenColonyHandler().getColonyOrRegister();
            UUID lord = colony == null ? null : Settlements.owner(colony);
            return lord != null && Relations.hostile(lord, target);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ raising and leading

    /**
     * Raises a soldier of {@code kind} (warrior, spear_man, shieldman, archer, crossbowman, militia) for {@code owner}:
     * {@code rank} is the level of the post that raises him (1 to 5) and {@code tier} the grade of his kit (0 to 2).
     */
    @Nullable
    public static Entity spawn(ServerLevel level, String kind, UUID owner, BlockPos at, BlockPos home, int rank, int tier) {
        String role = ROLES.get(kind);
        if (role == null) {
            return null;
        }
        try {
            Entity unit = DruzhinaApi.spawn(level, role, owner, at, Math.max(0, Math.min(2, tier)), Math.max(0, (rank - 1) / 2));
            if (unit != null) {
                DruzhinaApi.hold(unit, home);
                DruzhinaApi.setManaged(unit, true);
            }
            return unit;
        } catch (RuntimeException e) {
            Prestol.LOGGER.warn("Prestol: could not raise a {}", kind, e);
            return null;
        }
    }

    /** A soldier Prestol raised but leaves to his lord: mercenaries take orders from the commander's baton. */
    public static void release(Entity unit) {
        DruzhinaApi.setManaged(unit, false);
    }

    /** Gives a name to a soldier (a conscript keeps his own). */
    public static void name(Entity unit, Component name) {
        DruzhinaApi.setName(unit, name);
    }

    public static boolean orders(Entity unit, BlockPos home, List<BlockPos> patrol, @Nullable LivingEntity follow) {
        if (!DruzhinaApi.isSoldier(unit)) {
            return false;
        }
        if (follow != null) {
            DruzhinaApi.follow(unit, follow);
        } else if (patrol.size() > 1) {
            DruzhinaApi.patrol(unit, patrol);
        } else {
            DruzhinaApi.hold(unit, home);
        }
        return true;
    }

    /** His rank: 1 for a recruit, up to {@link #maxRank()}; -1 for anything but a soldier. */
    public static int rank(Entity unit) {
        int r = DruzhinaApi.rank(unit);
        return r < 0 ? -1 : r + 1;
    }

    public static int maxRank() {
        return DruzhinaApi.maxRank() + 1;
    }

    /** One rank up; false when he is at the top already. */
    public static boolean rankUp(Entity unit) {
        int r = DruzhinaApi.rank(unit);
        if (r < 0 || r >= DruzhinaApi.maxRank()) {
            return false;
        }
        DruzhinaApi.promote(unit);
        return true;
    }

    /** Soldiers that eat: alive soldiers who serve someone. */
    public static boolean eater(Entity entity) {
        return DruzhinaApi.isSoldier(entity) && entity.isAlive();
    }

    /** Like {@link #eater} but also true for a soldier who has just fallen. */
    public static boolean soldier(Entity entity) {
        return DruzhinaApi.isSoldier(entity);
    }

    public static boolean fighter(Entity entity) {
        return DruzhinaApi.isSoldier(entity);
    }

    @Nullable
    public static Attribute rangedDamage() {
        return DruzhinaApi.rangedDamage();
    }

    /** Druzhina has no supply of its own: provisions come from the settlements' stores. */
    public static void noSupply(Entity entity) {
    }

    public static double fromSupplyPoints(ServerLevel level, BlockPos pos, UUID owner, double points, int days) {
        return 0.0;
    }

    @Nullable
    public static UUID soldierOwner(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && DruzhinaApi.isSoldier(entity) ? DruzhinaApi.owner(entity) : null;
    }
}
