package com.nick.prestol.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.CourtData;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Fealty;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class Wars {
   private Wars() {
   }

   private static Realm ruled(MinecraftServer server, ServerPlayer player) {
      return RealmData.get(server).byRuler(player.getUUID());
   }

   public static boolean atWar(RealmData data, Realm a, Realm b) {
      return a != null && b != null && a != b && data.war(a.id(), b.id()) != null;
   }

   public static boolean fighting(MinecraftServer server, RealmData data, Realm a, Realm b) {
      if (a != null && b != null && a != b) {
         RealmData.War w = data.war(a.id(), b.id());
         return w != null && Realms.now(server) >= w.activeAt();
      } else {
         return false;
      }
   }

   public static Realms.Result declare(MinecraftServer server, ServerPlayer player, UUID targetId) {
      RealmData data = RealmData.get(server);
      Realm me = ruled(server, player);
      Realm them = data.byId(targetId);
      if (me == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (them != null && them != me) {
         if (data.war(me.id(), them.id()) != null) {
            return Realms.Result.no(Msg.bad("war.already", them.name()));
         } else if (Fealty.suzerainOf(server, me) != null) {
            // a vassal fights its lord's wars, not its own
            return Realms.Result.no(Msg.bad("fealty.vassal_no_war", Fealty.suzerainOf(server, me).name()));
         } else if (Fealty.bound(server, me, them)) {
            return Realms.Result.no(Msg.bad("fealty.bound_no_war", them.name()));
         } else {
            long now = Realms.now(server);
            long truce = data.truceUntil(me.id(), them.id());
            if (truce > now) {
               return Realms.Result.no(Msg.bad("war.truce", them.name(), Realms.day(truce)));
            } else {
               begin(server, data, me, them);
               return Realms.Result.yes(Msg.good("ok.war", them.name()));
            }
         }
      } else {
         return Realms.Result.no(Msg.bad("err.no_realm"));
      }
   }

   /**
    * War between {@code me} and {@code them}. The attacker's vassals march with it; the defender's vassals, and its
    * suzerain with that suzerain's other vassals, come to its defence.
    */
   public static void begin(MinecraftServer server, RealmData data, Realm me, Realm them) {
      long now = Realms.now(server);
      long active = now + (long)((Integer)PrestolConfig.MOBILIZE_MINUTES.get()).intValue() * 1200L;
      List<Realm> attack = new ArrayList<>();
      attack.add(me);
      attack.addAll(Fealty.vassalsOf(server, me));
      List<Realm> defend = new ArrayList<>();
      defend.add(them);
      defend.addAll(Fealty.vassalsOf(server, them));
      Realm liege = Fealty.suzerainOf(server, them);
      if (liege != null && liege != me) {
         defend.add(liege);

         for (Realm v : Fealty.vassalsOf(server, liege)) {
            if (v != them && v != me) {
               defend.add(v);
            }
         }
      }

      for (Realm a : attack) {
         for (Realm d : defend) {
            if (a != d && data.war(a.id(), d.id()) == null) {
               a.allies().remove(d.id());
               d.allies().remove(a.id());
               data.setOffer("ally", a.id(), d.id(), false);
               data.setOffer("ally", d.id(), a.id(), false);
               data.addWar(new RealmData.War(a.id(), d.id(), now, active));
            }
         }
      }

      Realms.tellAll(server, Msg.war("war.declared", me.name(), them.name(), PrestolConfig.MOBILIZE_MINUTES.get()));
      for (Realm a : attack) {
         if (a != me) {
            Realms.tellAll(server, Msg.war("fealty.called", a.name(), me.name(), them.name()));
         }
      }

      for (Realm d : defend) {
         if (d != them) {
            Realms.tellAll(server, Msg.war("fealty.defends", d.name(), them.name()));
         }
      }

      for (Realm d : defend) {
         for (ServerPlayer p : Realms.online(server, d)) {
            p.level().playSound(null, p.blockPosition(), (SoundEvent)SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 2.0F, 1.0F);
         }
      }

      Realms.chronicle(server, "war_declared", List.of(me.name(), them.name()), me, them);
      Realms.changed(server);
      applyHostility(server, data);
   }

   public static Realms.Result peace(MinecraftServer server, ServerPlayer player, UUID targetId) {
      RealmData data = RealmData.get(server);
      Realm me = ruled(server, player);
      Realm them = data.byId(targetId);
      if (me == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (them == null) {
         return Realms.Result.no(Msg.bad("err.no_realm"));
      } else {
         RealmData.War w = data.war(me.id(), them.id());
         if (w == null) {
            return Realms.Result.no(Msg.bad("war.none", them.name()));
         } else if (data.hasOffer("peace", them.id(), me.id())) {
            // accepting their offer means accepting their terms
            Treaties.execute(server, them, me);
            makePeace(server, data, w);
            return Realms.Result.yes(Msg.good("ok.peace", them.name()));
         } else {
            Treaties.plain(me.id(), them.id());
            data.setOffer("peace", me.id(), them.id(), true);
            Realms.tell(server, them, Msg.info("war.peace_offered", me.name()));
            return Realms.Result.yes(Msg.good("ok.peace_offered", them.name()));
         }
      }
   }

   public static void makePeace(MinecraftServer server, RealmData data, RealmData.War w) {
      data.endWar(w);
      // a lord's peace is his vassals' peace with the same enemy
      Realm sideA = data.byId(w.attacker());
      Realm sideB = data.byId(w.defender());
      for (Realm va : Fealty.vassalsOf(server, sideA)) {
         endAlso(server, data, va, sideB);
      }

      for (Realm vb : Fealty.vassalsOf(server, sideB)) {
         endAlso(server, data, sideA, vb);

         for (Realm va : Fealty.vassalsOf(server, sideA)) {
            endAlso(server, data, va, vb);
         }
      }

      data.setOffer("peace", w.attacker(), w.defender(), false);
      data.setOffer("peace", w.defender(), w.attacker(), false);
      Treaties.forgetOffers(w.attacker(), w.defender());
      data.setTruce(w.attacker(), w.defender(), Realms.now(server) + (long)((Integer)PrestolConfig.TRUCE_DAYS.get()).intValue() * 24000L);
      Realm a = data.byId(w.attacker());
      Realm b = data.byId(w.defender());
      String an = a == null ? "?" : a.name();
      String bn = b == null ? "?" : b.name();
      Sieges.endBetween(server, w.attacker(), w.defender());
      Realms.tellAll(server, Msg.good("war.peace", an, bn));
      Realms.chronicle(server, "peace", List.of(an, bn), a, b);
      applyHostility(server, data);
      Realms.changed(server);
   }

   private static void endAlso(MinecraftServer server, RealmData data, Realm a, Realm b) {
      RealmData.War x = a == null || b == null ? null : data.war(a.id(), b.id());
      if (x != null) {
         data.endWar(x);
         data.setOffer("peace", a.id(), b.id(), false);
         data.setOffer("peace", b.id(), a.id(), false);
         Treaties.forgetOffers(a.id(), b.id());
         data.setTruce(a.id(), b.id(), Realms.now(server) + (long)((Integer)PrestolConfig.TRUCE_DAYS.get()).intValue() * 24000L);
         Sieges.endBetween(server, a.id(), b.id());
         Realms.tellAll(server, Msg.good("war.peace", a.name(), b.name()));
      }
   }

   public static Realms.Result alliance(MinecraftServer server, ServerPlayer player, UUID targetId, boolean make) {
      RealmData data = RealmData.get(server);
      Realm me = ruled(server, player);
      Realm them = data.byId(targetId);
      if (me == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (them == null || them == me) {
         return Realms.Result.no(Msg.bad("err.no_realm"));
      } else if (!make && Fealty.bound(server, me, them)) {
         return Realms.Result.no(Msg.bad("fealty.bound_ally", them.name()));
      } else if (!make) {
         boolean had = me.allies().remove(them.id()) | them.allies().remove(me.id());
         data.setOffer("ally", me.id(), them.id(), false);
         data.setDirty();
         if (had) {
            Realms.tellAll(server, Msg.info("war.alliance_broken", me.name(), them.name()));
            Realms.chronicle(server, "alliance_broken", List.of(me.name(), them.name()), me, them);
         }

         return Realms.Result.yes(Msg.info("ok.unallied", them.name()));
      } else if (data.war(me.id(), them.id()) != null) {
         return Realms.Result.no(Msg.bad("war.already", them.name()));
      } else if (data.hasOffer("ally", them.id(), me.id())) {
         me.allies().add(them.id());
         them.allies().add(me.id());
         data.setOffer("ally", them.id(), me.id(), false);
         data.setDirty();
         Realms.tellAll(server, Msg.good("war.alliance", me.name(), them.name()));
         Realms.chronicle(server, "alliance", List.of(me.name(), them.name()), me, them);
         return Realms.Result.yes(Msg.good("ok.allied", them.name()));
      } else {
         data.setOffer("ally", me.id(), them.id(), true);
         Realms.tell(server, them, Msg.info("war.alliance_offered", me.name()));
         return Realms.Result.yes(Msg.good("ok.alliance_offered", them.name()));
      }
   }

   public static boolean allied(Realm a, Realm b) {
      return a != null && b != null && a.allies().contains(b.id()) && b.allies().contains(a.id());
   }

   public static void applyHostility(MinecraftServer server, RealmData data) {
      boolean on = (Boolean)PrestolConfig.HOSTILE_RANK.get();
      Set<String> handled = new HashSet<>();

      for (Realm realm : data.all()) {
         Set<UUID> enemies = new HashSet<>();
         // outlaws are hostile in the realm's settlements whatever the war setting
         if (Court.on()) {
            enemies.addAll(CourtData.get(server).outlawsOf(realm.id()));
         }

         if (on) {
            for (RealmData.War w : data.wars()) {
               if (w.involves(realm.id())) {
                  Realm other = data.byId(w.other(realm.id()));
                  if (other != null) {
                     enemies.addAll(people(other));
                  }
               }
            }
         }

         for (IColony colony : Realms.members(realm)) {
            String key = Settlements.key(colony);
            handled.add(key);
            Holding h = data.holding(key);
            IPermissions perms = colony.getPermissions();
            Rank hostile = perms.getRankHostile();

            for (UUID id : enemies) {
               ColonyPlayer cp = (ColonyPlayer)perms.getPlayers().get(id);
               if ((cp == null || !cp.getRank().isColonyManager()) && (cp == null || cp.getRank() != hostile)) {
                  h.hostileWas.put(id, cp == null ? -1 : cp.getRank().getId());
                  perms.addPlayer(id, Owners.nameOf(server, id, perms), hostile);
                  h.hostile.add(id);
                  data.setDirty();
               }
            }

            for (UUID idx : new HashSet<>(h.hostile)) {
               if (!enemies.contains(idx)) {
                  release(perms, h, idx);
                  data.setDirty();
               }
            }
         }
      }

      // Settlements that left a realm, were taken or whose realm fell keep no war-time hostility.
      for (Entry<String, Holding> e : data.holdings().entrySet()) {
         Holding h = e.getValue();
         if (!h.hostile.isEmpty() && !handled.contains(e.getKey())) {
            IColony colony = Settlements.byKey(e.getKey());
            if (colony != null) {
               IPermissions perms = colony.getPermissions();

               for (UUID id : new HashSet<>(h.hostile)) {
                  release(perms, h, id);
               }
            }

            h.hostile.clear();
            h.hostileWas.clear();
            data.setDirty();
         }
      }
   }

   /** Gives a no longer hostile player back the rank he had before the war. */
   private static void release(IPermissions perms, Holding h, UUID id) {
      ColonyPlayer cp = (ColonyPlayer)perms.getPlayers().get(id);
      Integer was = h.hostileWas.remove(id);
      if (cp != null && cp.getRank() == perms.getRankHostile()) {
         Rank before = was == null || was < 0 ? null : perms.getRanks().get(was);
         if (before != null && before != perms.getRankHostile()) {
            perms.addPlayer(id, cp.getName(), before);
         } else if (was != null && was < 0) {
            perms.removePlayer(id);
         } else {
            perms.addPlayer(id, cp.getName(), perms.getRankNeutral());
         }
      }

      h.hostile.remove(id);
   }

   public static Set<UUID> people(Realm realm) {
      Set<UUID> out = new HashSet<>();
      out.add(realm.ruler());

      for (IColony c : Realms.members(realm)) {
         for (ColonyPlayer p : c.getPermissions().getPlayers().values()) {
            if (p.getRank().isColonyManager()) {
               out.add(p.getID());
            }
         }
      }

      return out;
   }

   public static void onRealmRemoved(MinecraftServer server, Realm realm) {
      Sieges.endFor(server, realm.id());
      applyHostility(server, RealmData.get(server));
   }

   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String verb, UUID target) {
      return switch (verb) {
         case "war" -> declare(server, player, target);
         case "peace" -> peace(server, player, target);
         case "ally" -> alliance(server, player, target, true);
         case "unally" -> alliance(server, player, target, false);
         default -> Realms.Result.no(Msg.bad("err.unknown"));
      };
   }
}
