package com.nick.prestol.realm;

import com.nick.prestol.PrestolConfig;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Wars;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Vassalage. A realm swears fealty to another, or is made to by a peace treaty: the vassal pays its suzerain a share
 * of its taxes every day, the two stand together as allies, the suzerain's wars call the vassal in and an attack on
 * the vassal calls in the suzerain, and the vassal makes no wars of its own. The suzerain may release a vassal; a
 * vassal that breaks its oath goes to war with its suzerain.
 */
public final class Fealty {
   private Fealty() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.COURT.get() && PrestolConfig.FEALTY.get();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static Realm suzerainOf(MinecraftServer server, Realm vassal) {
      if (vassal == null || !on()) {
         return null;
      } else {
         UUID s = Fealty.Data.get(server).liege.get(vassal.id());
         return s == null ? null : RealmData.get(server).byId(s);
      }
   }

   public static List<Realm> vassalsOf(MinecraftServer server, Realm suzerain) {
      List<Realm> out = new ArrayList<>();
      if (suzerain != null && on()) {
         RealmData realms = RealmData.get(server);
         Fealty.Data.get(server).liege.forEach((v, s) -> {
            Realm r = realms.byId(v);
            if (s.equals(suzerain.id()) && r != null) {
               out.add(r);
            }
         });
      }

      return out;
   }

   /** Whether one of the two is the other's vassal. */
   public static boolean bound(MinecraftServer server, Realm a, Realm b) {
      return a != null && b != null && (suzerainOf(server, a) == b || suzerainOf(server, b) == a);
   }

   /** {@code fealty swear|demand|release <realm>} or {@code fealty break}. */
   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String[] w) {
      RealmData data = RealmData.get(server);
      Realm me = data.byRuler(player.getUUID());
      String verb = w.length > 1 ? w[1] : "";
      Realm them = w.length > 2 ? data.byId(uuid(w[2])) : null;
      if (!on()) {
         return Realms.Result.no(Msg.bad("err.court_off"));
      } else if (me == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (verb.equals("break")) {
         Realm liege = suzerainOf(server, me);
         if (liege == null) {
            return Realms.Result.no(Msg.bad("fealty.not_vassal"));
         } else {
            unbind(server, liege, me);
            Realms.tellAll(server, Msg.war("fealty.broken", me.name(), liege.name()));
            Realms.chronicle(server, "fealty_broken", List.of(me.name(), liege.name()), me, liege);
            Wars.begin(server, data, me, liege);
            return Realms.Result.yes(Msg.good("ok.fealty_broken", liege.name()));
         }
      } else if (them == null || them == me) {
         return Realms.Result.no(Msg.bad("err.no_realm"));
      } else if (data.war(me.id(), them.id()) != null) {
         return Realms.Result.no(Msg.bad("fealty.at_war", them.name()));
      } else {
         switch (verb) {
            case "swear": {
               String why = refusal(server, them, me);
               if (why != null) {
                  return Realms.Result.no(Msg.bad(why, me.name(), them.name()));
               } else if (data.hasOffer("demand", them.id(), me.id())) {
                  bind(server, them, me);
                  return Realms.Result.yes(Msg.good("ok.fealty_sworn", them.name()));
               } else {
                  data.setOffer("swear", me.id(), them.id(), true);
                  Realms.tell(server, them, Msg.info("fealty.swear_offered", me.name()));
                  return Realms.Result.yes(Msg.good("ok.fealty_offered", them.name()));
               }
            }
            case "demand": {
               String why = refusal(server, me, them);
               if (why != null) {
                  return Realms.Result.no(Msg.bad(why, them.name(), me.name()));
               } else if (data.hasOffer("swear", them.id(), me.id())) {
                  bind(server, me, them);
                  return Realms.Result.yes(Msg.good("ok.fealty_taken", them.name()));
               } else {
                  data.setOffer("demand", me.id(), them.id(), true);
                  Realms.tell(server, them, Msg.info("fealty.demanded", me.name()));
                  return Realms.Result.yes(Msg.good("ok.fealty_demand_sent", them.name()));
               }
            }
            case "release": {
               if (suzerainOf(server, them) != me) {
                  return Realms.Result.no(Msg.bad("fealty.not_your_vassal", them.name()));
               } else {
                  unbind(server, me, them);
                  Realms.tellAll(server, Msg.info("fealty.released", them.name(), me.name()));
                  Realms.chronicle(server, "fealty_released", List.of(them.name(), me.name()), me, them);
                  return Realms.Result.yes(Msg.good("ok.fealty_released", them.name()));
               }
            }
            default:
               return Realms.Result.no(Msg.bad("err.unknown"));
         }
      }
   }

   /**
    * Why {@code vassal} cannot become the vassal of {@code suzerain} (a message key taking the vassal's and the
    * suzerain's names), or null. One rank of vassals only: a vassal has none of its own and its lord is nobody's.
    */
   public static String refusal(MinecraftServer server, Realm suzerain, Realm vassal) {
      if (suzerainOf(server, vassal) != null) {
         return "fealty.already_vassal";
      } else if (!vassalsOf(server, vassal).isEmpty()) {
         return "fealty.has_vassals";
      } else {
         return suzerainOf(server, suzerain) != null ? "fealty.lord_is_vassal" : null;
      }
   }

   /** The oath is sworn: from now on {@code vassal} serves {@code suzerain}. */
   public static void bind(MinecraftServer server, Realm suzerain, Realm vassal) {
      RealmData data = RealmData.get(server);
      Fealty.Data f = Fealty.Data.get(server);
      f.liege.put(vassal.id(), suzerain.id());
      f.setDirty();
      for (String kind : new String[]{"swear", "demand", "ally"}) {
         data.setOffer(kind, vassal.id(), suzerain.id(), false);
         data.setOffer(kind, suzerain.id(), vassal.id(), false);
      }

      suzerain.allies().add(vassal.id());
      vassal.allies().add(suzerain.id());
      data.setDirty();
      Realms.tellAll(server, Msg.good("fealty.sworn", vassal.name(), suzerain.name(), PrestolConfig.FEALTY_SHARE.get()));
      Realms.chronicle(server, "fealty", List.of(vassal.name(), suzerain.name()), suzerain, vassal);
      // the lord's wars are the vassal's from the day of the oath
      for (RealmData.War w : new ArrayList<>(data.wars())) {
         if (w.involves(suzerain.id())) {
            Realm enemy = data.byId(w.other(suzerain.id()));
            if (enemy != null && enemy != vassal && data.war(vassal.id(), enemy.id()) == null) {
               vassal.allies().remove(enemy.id());
               enemy.allies().remove(vassal.id());
               data.addWar(new RealmData.War(vassal.id(), enemy.id(), Realms.now(server), w.activeAt()));
               Realms.tellAll(server, Msg.war("fealty.called", vassal.name(), suzerain.name(), enemy.name()));
            }
         }
      }

      Wars.applyHostility(server, data);
      Realms.changed(server);
   }

   private static void unbind(MinecraftServer server, Realm suzerain, Realm vassal) {
      RealmData data = RealmData.get(server);
      Fealty.Data f = Fealty.Data.get(server);
      f.liege.remove(vassal.id());
      f.setDirty();
      suzerain.allies().remove(vassal.id());
      vassal.allies().remove(suzerain.id());
      data.setDirty();
      Wars.applyHostility(server, data);
      Realms.changed(server);
   }

   /** The vassal's share of the day's taxes goes to its suzerain; returns what was paid. */
   public static long payShare(MinecraftServer server, EconomyData eco, Realm vassal, long income, long day) {
      Realm liege = suzerainOf(server, vassal);
      long share = liege == null ? 0L : income * PrestolConfig.FEALTY_SHARE.get() / 100L;
      if (share > 0L && eco.take(vassal.id(), share)) {
         eco.give(liege.id(), share);
         eco.log(vassal.id(), day, "fealty_paid", -share, liege.name());
         eco.log(liege.id(), day, "fealty_got", share, vassal.name());
         return share;
      } else {
         return 0L;
      }
   }

   /** A realm is gone: its vassals are free, and it no longer serves anyone. */
   public static void onRealmRemoved(MinecraftServer server, Realm realm) {
      Fealty.Data f = Fealty.Data.get(server);
      if (f.liege.remove(realm.id()) != null | f.liege.values().removeIf(s -> s.equals(realm.id()))) {
         f.setDirty();
      }
   }

   private static UUID uuid(String s) {
      try {
         return UUID.fromString(s);
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   /** Who serves whom: vassal realm to suzerain realm. */
   public static final class Data extends SavedData {
      private static final String NAME = "prestol_fealty";
      final Map<UUID, UUID> liege = new HashMap<>();

      public static Fealty.Data get(MinecraftServer server) {
         return server.overworld().getDataStorage().computeIfAbsent(Fealty.Data::load, Fealty.Data::new, NAME);
      }

      @Override
      public CompoundTag save(CompoundTag tag) {
         CompoundTag l = new CompoundTag();
         this.liege.forEach((v, s) -> l.putUUID(v.toString(), s));
         tag.put("liege", l);
         return tag;
      }

      private static Fealty.Data load(CompoundTag tag) {
         Fealty.Data d = new Fealty.Data();
         CompoundTag l = tag.getCompound("liege");

         for (String k : l.getAllKeys()) {
            try {
               d.liege.put(UUID.fromString(k), l.getUUID(k));
            } catch (IllegalArgumentException var5) {
            }
         }

         return d;
      }
   }
}
