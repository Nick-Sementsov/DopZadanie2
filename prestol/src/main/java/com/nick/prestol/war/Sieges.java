package com.nick.prestol.war;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.army.Army;
import com.nick.prestol.army.Morale;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.map.MapSync;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class Sieges {
   private static final Map<String, Sieges.Siege> ACTIVE = new HashMap<>();
   private static final Map<String, Long> TOLD = new HashMap<>();
   private static long seconds;

   private Sieges() {
   }

   public static boolean isBesieged(String key) {
      return ACTIVE.containsKey(key);
   }

   public static Sieges.Siege get(String key) {
      return ACTIVE.get(key);
   }

   public static int mapState(String key) {
      Sieges.Siege s = ACTIVE.get(key);
      return s == null ? 0 : (s.kind == Sieges.Kind.REVOLT ? 2 : 1);
   }

   public static void tick(MinecraftServer server) {
      seconds++;
      RealmData data = RealmData.get(server);
      long now = Realms.now(server);
      Iterator<Sieges.Siege> it = ACTIVE.values().iterator();

      while (it.hasNext()) {
         Sieges.Siege s = it.next();
         IColony colony = Settlements.byKey(s.key);
         Realm defender = colony == null ? null : data.byMember(s.key);
         if (colony == null || defender == null || !defender.id().equals(s.defenderRealm)) {
            if (colony != null) {
               // the settlement left the besieged realm (mutiny, handover): say so instead of ending silently
               Realms.tellAll(server, Msg.info(s.kind == Sieges.Kind.REVOLT ? "revolt.failed" : "siege.lifted", colony.getName()));
            }

            end(s, server, null);
            it.remove();
         } else if (!step(server, data, colony, s)) {
            it.remove();
         }
      }

      if (!data.wars().isEmpty()) {
         for (IColony colony : Settlements.all()) {
            String key = Settlements.key(colony);
            if (!ACTIVE.containsKey(key) && !Conquest.pending(key)) {
               Realm target = data.byMember(key);
               if (target != null) {
                  ServerLevel level = server.getLevel(colony.getDimension());
                  if (level != null && level.isLoaded(colony.getCenter())) {
                     Sieges.Side attack = attackers(server, data, level, colony, target, null, null);
                     if (!attack.present.isEmpty()) {
                        Realm enemy = data.byId(attack.realm);
                        Component refusal = refusal(server, data, colony, target, now);
                        if (refusal != null) {
                           tellOnce(server, key, attack.players, refusal);
                        } else {
                           Sieges.Siege s = new Sieges.Siege(
                              key,
                              Sieges.Kind.SIEGE,
                              enemy.id(),
                              null,
                              target.id(),
                              (Integer)PrestolConfig.HOLD_SECONDS.get(),
                              Msg.t("siege.bar", colony.getName(), enemy.name())
                           );
                           s.commander = attack.firstPlayer != null ? attack.firstPlayer : attack.firstOwner;
                           ACTIVE.put(key, s);
                           Realms.tellAll(server, Msg.war("siege.started", colony.getName(), target.name(), enemy.name()));
                           Army.muster(server, colony);
                           Realms.chronicle(server, "siege", List.of(colony.getName(), enemy.name()), enemy, target);
                           MapSync.markDirty();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Component refusal(MinecraftServer server, RealmData data, IColony colony, Realm target, long now) {
      Holding h = data.peek(Settlements.key(colony));
      if (h != null && h.immuneUntil > now) {
         return Msg.bad("siege.immune", colony.getName(), Realms.day(h.immuneUntil));
      } else if ((Boolean)PrestolConfig.CAPITAL_LAST.get() && Settlements.key(colony).equals(target.capital()) && target.members().size() > 1) {
         return Msg.bad("siege.capital_last", colony.getName());
      } else {
         return PrestolConfig.DEFENDER_ONLINE.get() && !defenderOnline(server, colony) ? Msg.bad("siege.nobody_home", colony.getName()) : null;
      }
   }

   private static void tellOnce(MinecraftServer server, String key, Set<UUID> players, Component msg) {
      Long next = TOLD.get(key);
      if (next == null || next <= seconds) {
         TOLD.put(key, seconds + 30L);

         for (UUID id : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
               p.displayClientMessage(msg, true);
            }
         }
      }
   }

   private static boolean defenderOnline(MinecraftServer server, IColony colony) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (Settlements.isManager(p, colony)) {
            return true;
         }
      }

      return false;
   }

   private static boolean step(MinecraftServer server, RealmData data, IColony colony, Sieges.Siege s) {
      ServerLevel level = server.getLevel(colony.getDimension());
      if (level != null && level.isLoaded(colony.getCenter())) {
         Realm defender = data.byId(s.defenderRealm);
         Sieges.Side attack = attackers(server, data, level, colony, defender, s.kind == Sieges.Kind.REVOLT ? s.claimant : null, s.attackerRealm);
         boolean defended = defended(server, data, level, colony, defender);
         if (attack.present.isEmpty()) {
            s.absent++;
         } else {
            s.absent = 0;
            if (s.commander == null || !attack.present.contains(s.commander)) {
               s.commander = attack.firstPlayer != null ? attack.firstPlayer : attack.firstOwner;
            }

            s.contested = defended;
            if (!defended) {
               s.held++;
            }
         }

         updateBar(server, level, colony, s, defender);
      } else {
         s.absent++;
      }

      // a camp before the walls keeps the siege up while the army rests in it
      int grace = PrestolConfig.SIEGE_GRACE_SECONDS.get();
      if (s.kind == Sieges.Kind.SIEGE && s.attackerRealm != null && Camps.besieging(s.attackerRealm, s.key)) {
         grace = Math.max(grace, PrestolConfig.CAMP_HOLD_SECONDS.get());
      }

      if (s.absent >= grace) {
         Realms.tellAll(server, Msg.info(s.kind == Sieges.Kind.REVOLT ? "revolt.failed" : "siege.lifted", colony.getName()));
         Realms.chronicle(
            server,
            s.kind == Sieges.Kind.REVOLT ? "revolt_failed" : "siege_lifted",
            List.of(colony.getName()),
            data.byId(s.attackerRealm),
            data.byId(s.defenderRealm)
         );
         end(s, server, null);
         return false;
      } else if (s.held >= s.needed) {
         end(s, server, null);
         if (s.kind == Sieges.Kind.REVOLT) {
            Conquest.revolutionWon(server, colony, s.claimant);
         } else {
            Conquest.captured(server, colony, s.attackerRealm, s.commander);
         }

         return false;
      } else {
         return true;
      }
   }

   private static void updateBar(MinecraftServer server, ServerLevel level, IColony colony, Sieges.Siege s, Realm defender) {
      s.bar.setProgress(Math.min(1.0F, (float)s.held / (float)s.needed));
      int left = Math.max(0, s.needed - s.held);
      String time = left / 60 + ":" + String.format("%02d", left % 60);
      Realm attacker = RealmData.get(server).byId(s.attackerRealm);
      String attackerName = attacker != null ? attacker.name() : Owners.nameOf(server, s.claimant, colony.getPermissions());
      String state = s.contested ? "siege.bar_contested" : "siege.bar_running";
      s.bar
         .setName(Msg.t(s.kind == Sieges.Kind.REVOLT ? "revolt.bar" : "siege.bar", colony.getName(), attackerName).append(" — ").append(Msg.t(state, time)));
      Set<ServerPlayer> watchers = new LinkedHashSet<>();
      BlockPos c = colony.getCenter();

      for (ServerPlayer p : level.players()) {
         if (p.blockPosition().distSqr(c) < 9216.0) {
            watchers.add(p);
         }
      }

      if (defender != null) {
         watchers.addAll(Realms.online(server, defender));
      }

      if (attacker != null) {
         watchers.addAll(Realms.online(server, attacker));
      }

      for (ServerPlayer px : new ArrayList<>(s.bar.getPlayers())) {
         if (!watchers.contains(px)) {
            s.bar.removePlayer(px);
         }
      }

      for (ServerPlayer pxx : watchers) {
         s.bar.addPlayer(pxx);
      }
   }

   private static void end(Sieges.Siege s, MinecraftServer server, Component msg) {
      s.bar.removeAllPlayers();
      MapSync.markDirty();
      Army.disband(server, s.key);
   }

   private static AABB area(IColony colony) {
      int r = (Integer)PrestolConfig.SIEGE_RADIUS.get();
      BlockPos c = colony.getCenter();
      return new AABB(c).inflate((double)r, (double)Math.max(8, r), (double)r);
   }

   private static boolean inRange(IColony colony, Entity e) {
      int r = (Integer)PrestolConfig.SIEGE_RADIUS.get();
      BlockPos c = colony.getCenter();
      double dx = e.getX() - (double)c.getX() - 0.5;
      double dz = e.getZ() - (double)c.getZ() - 0.5;
      return dx * dx + dz * dz <= (double)(r * r) && Math.abs(e.getY() - (double)c.getY()) <= (double)Math.max(8, r);
   }

   private static Sieges.Side attackers(MinecraftServer server, RealmData data, ServerLevel level, IColony colony, Realm target, UUID claimant, UUID onlyRealm) {
      Sieges.Side side = new Sieges.Side();
      Map<UUID, Realm> cache = new HashMap<>();

      for (ServerPlayer p : level.players()) {
         if (p.isAlive() && !p.isSpectator() && inRange(colony, p)) {
            Realm r = cache.computeIfAbsent(p.getUUID(), k -> Realms.realmOf(server, p));
            boolean attacker;
            if (claimant != null) {
               attacker = p.getUUID().equals(claimant) || onlyRealm != null && r != null && r.id().equals(onlyRealm);
            } else {
               attacker = r != null && (onlyRealm == null || r.id().equals(onlyRealm)) && Wars.fighting(server, data, r, target);
            }

            if (attacker) {
               if (side.realm == null && r != null) {
                  side.realm = r.id();
               }

               if (onlyRealm != null || claimant != null || r == null || r.id().equals(side.realm)) {
                  side.present.add(p.getUUID());
                  side.players.add(p.getUUID());
                  if (side.firstPlayer == null) {
                     side.firstPlayer = p.getUUID();
                  }
               }
            }
         }
      }

      if (Hyw.present()) {
         for (Entity e : level.getEntities((Entity)null, area(colony), ex -> Hyw.soldierOwner(ex) != null && !Morale.routed(ex))) {
            if (inRange(colony, e)) {
               UUID owner = Hyw.soldierOwner(e);
               boolean attackerx;
               if (claimant != null) {
                  attackerx = owner.equals(claimant) || onlyRealm != null && inRealm(server, data, owner, onlyRealm);
               } else {
                  Realm rx = realmOfPlayer(server, data, owner);
                  attackerx = rx != null
                     && (onlyRealm == null || rx.id().equals(onlyRealm))
                     && Wars.fighting(server, data, rx, target)
                     && (side.realm == null || rx.id().equals(side.realm));
                  if (attackerx && side.realm == null) {
                     side.realm = rx.id();
                  }
               }

               if (attackerx) {
                  side.present.add(owner);
                  if (side.firstOwner == null) {
                     side.firstOwner = owner;
                  }
               }
            }
         }
      }

      if (onlyRealm != null && side.realm == null) {
         side.realm = onlyRealm;
      }

      return side;
   }

   private static boolean defended(MinecraftServer server, RealmData data, ServerLevel level, IColony colony, Realm defender) {
      for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
         if (citizen.getJob() instanceof AbstractJobGuard) {
            Optional<AbstractEntityCitizen> entity = citizen.getEntity();
            if (entity.isPresent() && entity.get().isAlive() && inRange(colony, (Entity)entity.get())) {
               return true;
            }
         }
      }

      for (ServerPlayer p : level.players()) {
         if (p.isAlive() && !p.isSpectator() && inRange(colony, p)) {
            Realm r = Realms.realmOf(server, p);
            if (r != null && defender != null && (r == defender || Wars.allied(r, defender))) {
               return true;
            }

            if (Settlements.isManager(p, colony)) {
               return true;
            }
         }
      }

      if (Hyw.present() && defender != null) {
         for (Entity e : level.getEntities((Entity)null, area(colony), ex -> Hyw.soldierOwner(ex) != null && !Morale.routed(ex))) {
            if (inRange(colony, e)) {
               Realm rx = realmOfPlayer(server, data, Hyw.soldierOwner(e));
               if (rx != null && (rx == defender || Wars.allied(rx, defender))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static Realm realmOfPlayer(MinecraftServer server, RealmData data, UUID player) {
      ServerPlayer online = server.getPlayerList().getPlayer(player);
      if (online != null) {
         return Realms.realmOf(server, online);
      } else {
         Realm r = data.byRuler(player);
         if (r != null) {
            return r;
         } else {
            for (IColony c : Settlements.ownedBy(player)) {
               Realm m = data.byMember(Settlements.key(c));
               if (m != null) {
                  return m;
               }
            }

            return null;
         }
      }
   }

   private static boolean inRealm(MinecraftServer server, RealmData data, UUID player, UUID realm) {
      Realm r = realmOfPlayer(server, data, player);
      return r != null && r.id().equals(realm);
   }

   public static Realms.Result revolt(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      IColony colony = Settlements.byKey(key);
      Holding h = data.peek(key);
      if (colony != null && h != null && player.getUUID().equals(h.formerOwner)) {
         long now = Realms.now(server);
         long ready = h.capturedAt + (long)((Integer)PrestolConfig.REVOLT_AFTER_DAYS.get()).intValue() * 24000L;
         if (now < ready) {
            return Realms.Result.no(Msg.bad("revolt.too_early", Realms.day(ready)));
         } else {
            double happy = Settlements.happiness(colony);
            if (happy >= (Double)PrestolConfig.REVOLT_HAPPINESS.get()) {
               return Realms.Result.no(Msg.bad("revolt.too_happy", String.format("%.1f", happy), String.format("%.1f", PrestolConfig.REVOLT_HAPPINESS.get())));
            } else if (ACTIVE.containsKey(key) || Conquest.pending(key)) {
               return Realms.Result.no(Msg.bad("err.besieged"));
            } else if (player.level().dimension().equals(colony.getDimension()) && inRange(colony, player)) {
               Realm current = data.byMember(key);
               if (current == null) {
                  return Realms.Result.no(Msg.bad("revolt.free_already"));
               } else {
                  Realm own = realmOfPlayer(server, data, player.getUUID());
                  Sieges.Siege s = new Sieges.Siege(
                     key,
                     Sieges.Kind.REVOLT,
                     own == null ? null : own.id(),
                     player.getUUID(),
                     current.id(),
                     (Integer)PrestolConfig.REVOLT_HOLD_SECONDS.get(),
                     Msg.t("revolt.bar", colony.getName(), player.getGameProfile().getName())
                  );
                  s.commander = player.getUUID();
                  ACTIVE.put(key, s);
                  Realms.tellAll(server, Msg.war("revolt.started", colony.getName(), player.getGameProfile().getName(), current.name()));
                  Realms.chronicle(server, "revolt", List.of(colony.getName(), player.getGameProfile().getName()), current, own);
                  MapSync.markDirty();
                  return Realms.Result.yes(Msg.good("ok.revolt", colony.getName()));
               }
            } else {
               return Realms.Result.no(Msg.bad("revolt.come_closer", PrestolConfig.SIEGE_RADIUS.get()));
            }
         }
      } else {
         return Realms.Result.no(Msg.bad("revolt.not_yours"));
      }
   }

   public static void endBetween(MinecraftServer server, UUID a, UUID b) {
      Iterator<Sieges.Siege> it = ACTIVE.values().iterator();

      while (it.hasNext()) {
         Sieges.Siege s = it.next();
         if (s.kind == Sieges.Kind.SIEGE && (a.equals(s.attackerRealm) && b.equals(s.defenderRealm) || b.equals(s.attackerRealm) && a.equals(s.defenderRealm))) {
            end(s, server, null);
            it.remove();
         }
      }
   }

   public static void endFor(MinecraftServer server, UUID realm) {
      Iterator<Sieges.Siege> it = ACTIVE.values().iterator();

      while (it.hasNext()) {
         Sieges.Siege s = it.next();
         if (realm.equals(s.attackerRealm) || realm.equals(s.defenderRealm)) {
            end(s, server, null);
            it.remove();
         }
      }
   }

   public static boolean lift(MinecraftServer server, String key) {
      Sieges.Siege s = ACTIVE.remove(key);
      if (s == null) {
         return false;
      } else {
         end(s, server, null);
         return true;
      }
   }

   /** Sieges survive restarts: {@link WarData} writes them with the world and hands them back on start. */
   static ListTag saveAll() {
      ListTag out = new ListTag();

      for (Sieges.Siege s : ACTIVE.values()) {
         out.add(s.save());
      }

      return out;
   }

   static void restore(ListTag list) {
      for (int i = 0; i < list.size(); i++) {
         Sieges.Siege s = Sieges.Siege.load(list.getCompound(i));
         if (!s.key.isEmpty() && s.defenderRealm != null) {
            ACTIVE.put(s.key, s);
         }
      }
   }

   public static Map<String, Sieges.Siege> active() {
      return ACTIVE;
   }

   public static void clear() {
      for (Sieges.Siege s : ACTIVE.values()) {
         s.bar.removeAllPlayers();
      }

      ACTIVE.clear();
      TOLD.clear();
   }

   public static enum Kind {
      SIEGE,
      REVOLT;
   }

   static final class Side {
      final Set<UUID> present = new LinkedHashSet<>();
      final Set<UUID> players = new LinkedHashSet<>();
      UUID firstPlayer;
      UUID firstOwner;
      UUID realm;
   }

   public static final class Siege {
      final String key;
      final Sieges.Kind kind;
      final UUID attackerRealm;
      final UUID claimant;
      final UUID defenderRealm;
      UUID commander;
      int held;
      int absent;
      boolean contested;
      final int needed;
      final ServerBossEvent bar;

      Siege(String key, Sieges.Kind kind, UUID attackerRealm, UUID claimant, UUID defenderRealm, int needed, Component title) {
         this.key = key;
         this.kind = kind;
         this.attackerRealm = attackerRealm;
         this.claimant = claimant;
         this.defenderRealm = defenderRealm;
         this.needed = needed;
         this.bar = new ServerBossEvent(title, kind == Sieges.Kind.REVOLT ? BossBarColor.PURPLE : BossBarColor.RED, BossBarOverlay.NOTCHED_10);
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putString("key", this.key);
         t.putString("kind", this.kind.name());
         if (this.attackerRealm != null) {
            t.putUUID("attacker", this.attackerRealm);
         }

         if (this.claimant != null) {
            t.putUUID("claimant", this.claimant);
         }

         if (this.defenderRealm != null) {
            t.putUUID("defender", this.defenderRealm);
         }

         if (this.commander != null) {
            t.putUUID("commander", this.commander);
         }

         t.putInt("held", this.held);
         t.putInt("absent", this.absent);
         t.putInt("needed", this.needed);
         return t;
      }

      static Sieges.Siege load(CompoundTag t) {
         Sieges.Kind kind = "REVOLT".equals(t.getString("kind")) ? Sieges.Kind.REVOLT : Sieges.Kind.SIEGE;
         Sieges.Siege s = new Sieges.Siege(
            t.getString("key"),
            kind,
            t.hasUUID("attacker") ? t.getUUID("attacker") : null,
            t.hasUUID("claimant") ? t.getUUID("claimant") : null,
            t.hasUUID("defender") ? t.getUUID("defender") : null,
            Math.max(1, t.getInt("needed")),
            Component.empty()
         );
         s.commander = t.hasUUID("commander") ? t.getUUID("commander") : null;
         s.held = t.getInt("held");
         s.absent = t.getInt("absent");
         return s;
      }

      public int held() {
         return this.held;
      }

      public int needed() {
         return this.needed;
      }

      public boolean contested() {
         return this.contested;
      }

      public Sieges.Kind kind() {
         return this.kind;
      }
   }
}
