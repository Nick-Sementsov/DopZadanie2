package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.flag.TownFlags;
import com.nick.prestol.map.MapSync;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Wars;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Realms {
   public static final int NAME_MIN = 3;
   public static final int NAME_MAX = 32;
   public static final long INVITE_TICKS = 24000L;

   private Realms() {
   }

   public static long now(MinecraftServer server) {
      return server.overworld().getGameTime();
   }

   public static long day(long time) {
      return time / 24000L + 1L;
   }

   public static Realm realmOf(RealmData data, IColony colony) {
      return colony == null ? null : data.byMember(Settlements.key(colony));
   }

   public static Realm realmOf(MinecraftServer server, ServerPlayer player) {
      RealmData data = RealmData.get(server);
      Realm r = data.byRuler(player.getUUID());
      if (r != null) {
         return r;
      } else {
         for (IColony c : Settlements.ownedBy(player.getUUID())) {
            Realm m = realmOf(data, c);
            if (m != null) {
               return m;
            }
         }

         for (Realm m : data.all()) {
            for (IColony cx : members(m)) {
               if (Settlements.isManager(player, cx)) {
                  return m;
               }
            }
         }

         return null;
      }
   }

   public static boolean isRuler(Realm realm, ServerPlayer player) {
      return realm != null && realm.ruler().equals(player.getUUID());
   }

   public static List<IColony> members(Realm realm) {
      List<IColony> out = new ArrayList<>();

      for (String k : realm.members()) {
         IColony c = Settlements.byKey(k);
         if (c != null) {
            out.add(c);
         }
      }

      return out;
   }

   public static int population(Realm realm) {
      int n = 0;

      for (IColony c : members(realm)) {
         n += Settlements.stats(c).citizens();
      }

      return n;
   }

   public static int towns(Realm realm) {
      int n = 0;

      for (IColony c : members(realm)) {
         if (Settlements.key(c).equals(realm.capital()) || Settlements.tierOf(c) == Tier.TOWN) {
            n++;
         }
      }

      return n;
   }

   public static Rank rank(Realm realm) {
      return Rank.of(population(realm), towns(realm));
   }

   public static int maxSettlements(Realm realm) {
      int per = (Integer)PrestolConfig.CITIZENS_PER_SETTLEMENT.get();
      if (per <= 0) {
         return Integer.MAX_VALUE;
      } else {
         IColony cap = Settlements.byKey(realm.capital());
         int citizens = cap == null ? 0 : Settlements.stats(cap).citizens();
         return (Integer)PrestolConfig.BASE_SETTLEMENTS.get() + citizens / per;
      }
   }

   public static Component checkJoin(RealmData data, Realm realm, IColony colony) {
      Realm other = data.byMember(Settlements.key(colony));
      if (other != null) {
         return Msg.bad("err.already_member", colony.getName(), other.name());
      } else if (realm.members().size() >= maxSettlements(realm)) {
         return Msg.bad("err.realm_full", realm.name(), maxSettlements(realm));
      } else {
         IColony cap = Settlements.byKey(realm.capital());
         if (cap != null) {
            if ((Boolean)PrestolConfig.SAME_DIMENSION.get() && !cap.getDimension().equals(colony.getDimension())) {
               return Msg.bad("err.other_dimension");
            }

            int max = (Integer)PrestolConfig.MAX_DISTANCE.get();
            if (max > 0 && cap.getDimension().equals(colony.getDimension()) && Settlements.distance(cap, colony) > (double)max) {
               return Msg.bad("err.too_far", (int)Settlements.distance(cap, colony), max);
            }
         }

         return null;
      }
   }

   public static Component validateName(RealmData data, String name, Realm self) {
      String n = name.trim();
      if (n.length() >= 3 && n.length() <= 32) {
         Realm same = data.byName(n);
         return same != null && same != self ? Msg.bad("err.name_taken", n) : null;
      } else {
         return Msg.bad("err.name_length", 3, 32);
      }
   }

   public static Set<ServerPlayer> online(MinecraftServer server, Realm realm) {
      Set<ServerPlayer> out = new HashSet<>();
      List<IColony> cols = members(realm);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (p.getUUID().equals(realm.ruler())) {
            out.add(p);
         } else {
            for (IColony c : cols) {
               if (Settlements.isMember(p, c)) {
                  out.add(p);
                  break;
               }
            }
         }
      }

      return out;
   }

   public static void tell(MinecraftServer server, Realm realm, Component msg) {
      for (ServerPlayer p : online(server, realm)) {
         p.sendSystemMessage(msg);
      }
   }

   public static void tellColony(MinecraftServer server, IColony colony, Component msg) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (Settlements.isMember(p, colony)) {
            p.sendSystemMessage(msg);
         }
      }
   }

   public static void tellAll(MinecraftServer server, Component msg) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         p.sendSystemMessage(msg);
      }
   }

   public static void chronicle(MinecraftServer server, String key, List<String> args, Realm... realms) {
      List<UUID> ids = new ArrayList<>();

      for (Realm r : realms) {
         if (r != null) {
            ids.add(r.id());
         }
      }

      RealmData.get(server).write(now(server), key, args, ids);
   }

   public static void chronicleAll(MinecraftServer server, String key, String... args) {
      RealmData.get(server).write(now(server), key, Arrays.asList(args), List.of());
   }

   public static void changed(MinecraftServer server) {
      MapSync.markDirty();
      TownFlags.markDirty();
   }

   public static void removeRealm(MinecraftServer server, RealmData data, Realm realm, Component announce) {
      data.remove(realm);
      Hyw.onRealmRemoved(server, realm);
      Wars.onRealmRemoved(server, realm);
      if (announce != null) {
         tellAll(server, announce);
      }

      changed(server);
   }

   public static IColony pickCapital(Realm realm) {
      IColony best = null;
      IColony biggest = null;
      int bestN = -1;
      int bigN = -1;

      for (IColony c : members(realm)) {
         int n = Settlements.stats(c).citizens();
         if (n > bigN) {
            biggest = c;
            bigN = n;
         }

         if (Settlements.canBeCapital(c) && n > bestN) {
            best = c;
            bestN = n;
         }
      }

      return best != null ? best : biggest;
   }

   public static boolean fixCapital(MinecraftServer server, RealmData data, Realm realm) {
      if (realm.members().isEmpty()) {
         chronicleAll(server, "fell", realm.name());
         removeRealm(server, data, realm, Msg.info("realm.fell", realm.name()));
         return false;
      } else {
         if (!realm.members().contains(realm.capital())) {
            IColony cap = pickCapital(realm);
            if (cap == null) {
               chronicleAll(server, "fell", realm.name());
               removeRealm(server, data, realm, Msg.info("realm.fell", realm.name()));
               return false;
            }

            realm.setCapital(Settlements.key(cap));
            data.setDirty();
            tell(server, realm, Msg.info("realm.capital_moved", realm.name(), cap.getName()));
            chronicle(server, "capital_moved", List.of(realm.name(), cap.getName()), realm);
         }

         fixRuler(server, data, realm);
         return true;
      }
   }

   public static void fixRuler(MinecraftServer server, RealmData data, Realm realm) {
      for (IColony c : members(realm)) {
         if (realm.ruler().equals(Settlements.owner(c))) {
            return;
         }
      }

      IColony cap = Settlements.byKey(realm.capital());
      if (cap != null) {
         UUID owner = Settlements.owner(cap);
         if (owner != null && !owner.equals(realm.ruler())) {
            Realm theirs = data.byRuler(owner);
            if (theirs == null || theirs == realm) {
               realm.setRuler(owner);
               data.setDirty();
               tell(server, realm, Msg.info("realm.ruler_changed", realm.name(), Settlements.ownerName(cap)));
               chronicle(server, "ruler_changed", List.of(realm.name(), Settlements.ownerName(cap)), realm);
            }
         }
      }
   }

   /** How many recounts in a row a member colony may be missing before it is dropped (about five minutes). */
   private static final int MISSING_LIMIT = 60;
   private static final Map<String, Integer> MISSING = new HashMap<>();

   /**
    * Drops {@code gone} (a colony MineColonies reported as deleted) from its realm. A colony that is merely
    * not found is dropped only after it stays missing for a while, so one failed load does not break up a realm.
    */
   public static void cleanup(MinecraftServer server, RealmData data, String gone) {
      boolean managerWorks = !Settlements.all().isEmpty();

      for (Realm r : new ArrayList<>(data.all())) {
         boolean changed = false;
         Iterator<String> it = r.members().iterator();

         while (it.hasNext()) {
            String k = it.next();
            boolean drop = k.equals(gone);
            if (!drop) {
               if (Settlements.byKey(k) != null) {
                  MISSING.remove(k);
               } else if (managerWorks) {
                  int misses = MISSING.merge(k, 1, Integer::sum);
                  if (misses == 1) {
                     Prestol.LOGGER.warn("Prestol: colony {} of realm {} is not loaded; it leaves the realm if it stays missing", k, r.name());
                  }

                  drop = misses >= MISSING_LIMIT;
               }
            }

            if (drop) {
               MISSING.remove(k);
               it.remove();
               changed = true;
            }
         }

         if (changed) {
            data.setDirty();
            fixCapital(server, data, r);
            changed(server);
         }
      }
   }

   public static void forgetMissing() {
      MISSING.clear();
   }

   public static void detach(MinecraftServer server, RealmData data, Realm realm, IColony colony) {
      String key = Settlements.key(colony);
      if (realm.members().remove(key)) {
         data.setDirty();
         fixCapital(server, data, realm);
         changed(server);
      }
   }

   public static void attach(MinecraftServer server, RealmData data, Realm realm, IColony colony) {
      realm.members().add(Settlements.key(colony));
      data.applications(realm.id()).remove(Settlements.key(colony));
      data.invites(realm.id()).remove(Settlements.key(colony));
      data.setDirty();
      changed(server);
   }

   public static void onOwnerChanged(MinecraftServer server, RealmData data, IColony colony, UUID owner) {
      String key = Settlements.key(colony);
      Realm was = data.byMember(key);
      Realm to = data.byRuler(owner);
      if (to == null) {
         for (Realm r : data.all()) {
            for (IColony c : members(r)) {
               if (c != colony && owner.equals(Settlements.owner(c))) {
                  to = r;
                  break;
               }
            }

            if (to != null) {
               break;
            }
         }
      }

      if (to != null && to != was) {
         if (was != null) {
            was.members().remove(key);
            data.setDirty();
         }

         // checked after leaving the old realm: before, the old membership itself made the check fail
         boolean joins = checkJoin(data, to, colony) == null;
         if (was != null) {
            tell(server, was, joins ? Msg.bad("realm.lost_settlement", colony.getName(), to.name()) : Msg.bad("realm.left", colony.getName(), was.name()));
            fixCapital(server, data, was);
         }

         if (joins) {
            attach(server, data, to, colony);
            tellAll(server, Msg.good("realm.annexed", colony.getName(), to.name()));
            chronicle(server, "annexed", List.of(colony.getName(), to.name()), to, was);
         }

         changed(server);
      }
   }

   public static void onColonyDeleted(MinecraftServer server, IColony colony) {
      String key = Settlements.key(colony);
      RealmData data = RealmData.get(server);
      data.forget(key);
      cleanup(server, data, key);
   }

   public static IColony colonyAt(ServerPlayer player) {
      try {
         return IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), player.blockPosition());
      } catch (RuntimeException var2) {
         return null;
      }
   }

   public static record Result(boolean ok, Component message) {
      public static Realms.Result yes(Component m) {
         return new Realms.Result(true, m);
      }

      public static Realms.Result no(Component m) {
         return new Realms.Result(false, m);
      }
   }
}
