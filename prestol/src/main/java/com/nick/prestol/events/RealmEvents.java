package com.nick.prestol.events;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.research.util.ResearchConstants;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.army.Supplies;
import com.nick.prestol.decree.DecreeType;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.trade.Cargo;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What befalls a realm. Now and then, while its ruler is about, something happens in the realm or in one of its
 * settlements — a failed harvest, a fair, a knight seeking service, bandits on the roads, a fever, a rich vein,
 * a year of plenty, a wedding — and the ruler (or his chancellor) chooses one of two answers, in the chat or in
 * the book. The answers cost or bring coins and goods and leave their mark for a while: happier or sulkier
 * people, faster or slower work, safer or wilder roads. An event nobody answers within a day takes its second,
 * easier course.
 */
public final class RealmEvents {
   private static final long DAY = 24000L;
   private static final int CHECK_TICKS = 200;

   private RealmEvents() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.EVENT_DAYS.get() > 0.0;
      } catch (IllegalStateException e) {
         return false;
      }
   }

   /** The kinds of events, each with its two answers. */
   public enum Kind {
      BAD_HARVEST(true, 30, 0),
      FAIR(true, 0, 0),
      KNIGHT(false, 40, 0),
      BANDITS(false, 30, 0),
      FEVER(true, 35, 0),
      VEIN(true, 0, 0),
      PLENTY(true, 0, 0),
      WEDDING(false, 50, 0);

      /** Whether it happens in one settlement (else in the whole realm). */
      final boolean local;
      /** What each answer costs the treasury. */
      final int costA;
      final int costB;

      Kind(boolean local, int costA, int costB) {
         this.local = local;
         this.costA = costA;
         this.costB = costB;
      }

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      static Kind byId(String id) {
         for (Kind k : values()) {
            if (k.id().equals(id)) {
               return k;
            }
         }

         return null;
      }
   }

   // ------------------------------------------------------------------ the course of time

   /** Every second: events come, and lapse when nobody answers them. */
   public static void tick(MinecraftServer server) {
      long now = Realms.now(server);
      if (!on() || now % CHECK_TICKS != 0L) {
         return;
      }

      Data d = Data.get(server);
      RealmData realms = RealmData.get(server);
      RandomSource rnd = server.overworld().random;
      long every = (long)(PrestolConfig.EVENT_DAYS.get() * DAY);
      d.effects.removeIf(e -> e.until <= now);
      d.current.keySet().removeIf(id -> realms.byId(id) == null);

      for (Realm r : realms.all()) {
         Offer o = d.current.get(r.id());
         if (o != null) {
            if (now >= o.until) {
               resolve(server, r, o, false, null);
            }
         } else if (deciderOnline(server, r)) {
            Long next = d.nextAt.get(r.id());
            if (next == null) {
               d.nextAt.put(r.id(), now + (long)(every * (0.4 + 0.4 * rnd.nextDouble())));
               d.setDirty();
            } else if (now >= next) {
               d.nextAt.put(r.id(), now + (long)(every * (0.7 + 0.6 * rnd.nextDouble())));
               offer(server, r, rnd, now);
               d.setDirty();
            }
         }
      }
   }

   private static boolean deciderOnline(MinecraftServer server, Realm r) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         if (Court.may(server, r, p, Court.Office.CHANCELLOR)) {
            return true;
         }
      }

      return false;
   }

   /** An operator stages an event of {@code kind} (or any) in {@code r}; false if one is already open or none can be. */
   public static boolean stage(MinecraftServer server, Realm r, String kind) {
      if (Data.get(server).current.containsKey(r.id()) || Realms.members(r).isEmpty()) {
         return false;
      } else {
         offer(server, r, server.overworld().random, Realms.now(server), Kind.byId(kind));
         return true;
      }
   }

   /** Something happens in {@code r}. */
   static void offer(MinecraftServer server, Realm r, RandomSource rnd, long now) {
      offer(server, r, rnd, now, null);
   }

   private static void offer(MinecraftServer server, Realm r, RandomSource rnd, long now, Kind chosen) {
      List<IColony> members = Realms.members(r);
      if (members.isEmpty()) {
         return;
      }

      Kind kind = chosen != null ? chosen : Kind.values()[rnd.nextInt(Kind.values().length)];
      IColony where = members.get(rnd.nextInt(members.size()));
      Offer o = new Offer(kind, kind.local ? Settlements.key(where) : "", now + DAY);
      Data d = Data.get(server);
      d.current.put(r.id(), o);
      d.setDirty();
      tellOffer(server, r, o);
   }

   private static String place(Offer o) {
      IColony c = o.settlement.isEmpty() ? null : Settlements.byKey(o.settlement);
      return c == null ? "" : c.getName();
   }

   private static void tellOffer(MinecraftServer server, Realm r, Offer o) {
      String k = "event." + o.kind.id();
      MutableComponent msg = Msg.t("event.title", Msg.t(k + ".title"), r.name()).withStyle(ChatFormatting.GOLD);
      msg.append("\n").append(Msg.t(k + ".text", place(o)).withStyle(ChatFormatting.YELLOW));
      msg.append("\n").append(button(o, true)).append("  ").append(button(o, false));
      Realms.tell(server, r, msg);
   }

   private static Component button(Offer o, boolean a) {
      String k = "event." + o.kind.id() + (a ? ".a" : ".b");
      int cost = a ? o.kind.costA : o.kind.costB;
      MutableComponent label = Msg.t(k);
      if (cost > 0) {
         label.append(" (" + Coins.fmt(cost) + ")");
      }

      return Component.literal("[")
         .append(label)
         .append("]")
         .withStyle(
            s -> s.withColor(a ? ChatFormatting.GREEN : ChatFormatting.AQUA)
               .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/prestol event " + (a ? "a" : "b")))
               .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Msg.t(k + ".tip")))
         );
   }

   // ------------------------------------------------------------------ answering

   /** {@code event a|b}: the ruler or his chancellor answers the realm's event. */
   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String[] w) {
      Realm r = Realms.realmOf(server, player);
      Offer o = r == null ? null : Data.get(server).current.get(r.id());
      if (o == null) {
         return Realms.Result.no(Msg.bad("event.none"));
      } else if (!Court.may(server, r, player, Court.Office.CHANCELLOR)) {
         return Realms.Result.no(Msg.bad("err.not_chancellor"));
      } else {
         boolean a = w.length > 1 && w[1].equals("a");
         int cost = a ? o.kind.costA : o.kind.costB;
         EconomyData eco = EconomyData.get(server);
         if (cost > 0 && eco.balance(r.id()) < cost) {
            return Realms.Result.no(Msg.bad("err.treasury_short", Coins.fmt(eco.balance(r.id()))));
         } else {
            resolve(server, r, o, a, player.getGameProfile().getName());
            return Realms.Result.yes(null);
         }
      }
   }

   /** The answer takes effect; {@code who} is null when the day ran out and the easier course was taken. */
   private static void resolve(MinecraftServer server, Realm r, Offer o, boolean a, String who) {
      Data d = Data.get(server);
      d.current.remove(r.id());
      d.setDirty();
      EconomyData eco = EconomyData.get(server);
      long now = Realms.now(server);
      long day = Realms.day(now);
      int cost = a ? o.kind.costA : o.kind.costB;
      if (cost > 0 && eco.take(r.id(), cost)) {
         eco.log(r.id(), day, "event", -cost, Msg.t("event." + o.kind.id() + ".title").getString());
      }

      String target = o.kind.local ? o.settlement : "realm:" + r.id();
      IColony c = o.settlement.isEmpty() ? null : Settlements.byKey(o.settlement);
      switch (o.kind) {
         case BAD_HARVEST:
            if (a) {
               effect(d, target, now + DAY, ResearchConstants.HAPPINESS, 0.1, ResearchConstants.GROWTH, 0.25);
            } else {
               effect(d, target, now + DAY, ResearchConstants.HAPPINESS, -0.15, ResearchConstants.FARMING, -0.3);
            }
            break;
         case FAIR:
            if (a) {
               decree(d, target, now + DAY, DecreeType.FESTIVAL);
               if (c != null) {
                  com.nick.prestol.trade.Strangers.visit(server, c);
               }
            } else {
               gain(server, r, 60, o);
            }
            break;
         case KNIGHT:
            if (a) {
               effect(d, target, now + 2 * DAY, ResearchConstants.MELEE_DAMAGE, 1.0, ResearchConstants.MELEE_ARMOR, 0.1, ResearchConstants.ARCHER_DAMAGE, 1.0);
            }
            break;
         case BANDITS:
            if (a) {
               d.roads.put(r.id(), now + 2 * DAY);
            } else {
               d.roads.put(r.id(), -(now + 2 * DAY));
            }
            break;
         case FEVER:
            if (a) {
               decree(d, target, now + DAY, DecreeType.MEDIC);
            } else {
               effect(d, target, now + DAY, ResearchConstants.HAPPINESS, -0.2, ResearchConstants.GROWTH, -0.5);
            }
            break;
         case VEIN:
            if (a) {
               if (c != null) {
                  Cargo.insert(Supplies.stores(c, null), new ItemStack(Items.IRON_INGOT, 24));
                  Cargo.insert(Supplies.stores(c, null), new ItemStack(Items.COAL, 32));
               }

               gain(server, r, 20, o);
            } else {
               decree(d, target, now + 2 * DAY, DecreeType.MINING);
               effect(d, target, now + 2 * DAY, ResearchConstants.HAPPINESS, 0.05);
            }
            break;
         case PLENTY:
            if (a) {
               decree(d, target, now + DAY, DecreeType.FESTIVAL);
            } else {
               gain(server, r, 50, o);
            }
            break;
         case WEDDING:
            if (a) {
               effect(d, target, now + DAY, ResearchConstants.HAPPINESS, 0.15);
            }
      }

      String k = "event." + o.kind.id();
      MutableComponent msg = (who == null ? Msg.t("event.lapsed", Msg.t(k + ".title")) : Msg.t("event.chosen", who, Msg.t(k + ".title")))
         .withStyle(ChatFormatting.GOLD);
      msg.append(" ").append(Msg.t(k + (a ? ".a_done" : ".b_done"), place(o)).withStyle(ChatFormatting.YELLOW));
      Realms.tell(server, r, msg);
      Realms.chronicle(server, "event_" + o.kind.id() + (a ? "_a" : "_b"), List.of(r.name(), place(o).isEmpty() ? r.name() : place(o)), r);
   }

   private static void gain(MinecraftServer server, Realm r, int coins, Offer o) {
      EconomyData eco = EconomyData.get(server);
      eco.give(r.id(), coins);
      eco.log(r.id(), Realms.day(Realms.now(server)), "event", coins, Msg.t("event." + o.kind.id() + ".title").getString());
   }

   private static void effect(Data d, String target, long until, Object... pairs) {
      Map<ResourceLocation, Double> m = new HashMap<>();

      for (int i = 0; i + 1 < pairs.length; i += 2) {
         m.merge((ResourceLocation)pairs[i], ((Number)pairs[i + 1]).doubleValue(), Double::sum);
      }

      d.effects.add(new Effect(target, m, until));
      d.setDirty();
   }

   private static void decree(Data d, String target, long until, DecreeType t) {
      Map<ResourceLocation, Double> m = new HashMap<>();

      for (DecreeType.Boost b : t.boosts) {
         m.merge(b.effect(), b.amount(), Double::sum);
      }

      d.effects.add(new Effect(target, m, until));
      d.setDirty();
   }

   // ------------------------------------------------------------------ what lingers

   /** The marks events left on a settlement, merged into its decree effects. */
   public static void addEffects(MinecraftServer server, String key, Map<ResourceLocation, Double> out) {
      if (!on()) {
         return;
      }

      Data d = Data.get(server);
      if (d.effects.isEmpty()) {
         return;
      }

      Realm r = RealmData.get(server).byMember(key);
      String realm = r == null ? "-" : "realm:" + r.id();
      long now = Realms.now(server);

      for (Effect e : d.effects) {
         if (e.until > now && (e.target.equals(key) || e.target.equals(realm))) {
            e.boosts.forEach((k, v) -> out.merge(k, v, Double::sum));
         }
      }
   }

   /** How the roads of a realm are for its traders: 0 safe (patrolled), 1 as usual, 3 wild (bandits let be). */
   public static int roads(MinecraftServer server, UUID realm) {
      if (!on()) {
         return 1;
      }

      Long until = Data.get(server).roads.get(realm);
      long now = Realms.now(server);
      if (until == null) {
         return 1;
      } else if (until > 0L) {
         return until > now ? 0 : 1;
      } else {
         return -until > now ? 3 : 1;
      }
   }

   /** The open event of {@code r} for the book, or null. */
   public static CompoundTag describe(MinecraftServer server, Realm r, boolean decider) {
      Offer o = r == null ? null : Data.get(server).current.get(r.id());
      if (o == null) {
         return null;
      } else {
         CompoundTag t = new CompoundTag();
         t.putString("kind", o.kind.id());
         t.putString("place", place(o));
         t.putInt("costA", o.kind.costA);
         t.putInt("costB", o.kind.costB);
         t.putBoolean("decider", decider);
         t.putInt("left", (int)Math.max(0L, (o.until - Realms.now(server)) / 20L));
         return t;
      }
   }

   // ------------------------------------------------------------------ memory

   record Offer(Kind kind, String settlement, long until) {
   }

   record Effect(String target, Map<ResourceLocation, Double> boosts, long until) {
   }

   public static final class Data extends SavedData {
      private static final String NAME = "prestol_events";
      final Map<UUID, Offer> current = new HashMap<>();
      final Map<UUID, Long> nextAt = new HashMap<>();
      /** Patrolled roads (a positive time) or roads left to bandits (a negative one), until then. */
      final Map<UUID, Long> roads = new HashMap<>();
      final List<Effect> effects = new ArrayList<>();

      public static Data get(MinecraftServer server) {
         return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
      }

      @Override
      public CompoundTag save(CompoundTag tag) {
         CompoundTag cur = new CompoundTag();
         this.current.forEach((id, o) -> {
            CompoundTag x = new CompoundTag();
            x.putString("kind", o.kind.id());
            x.putString("at", o.settlement);
            x.putLong("until", o.until);
            cur.put(id.toString(), x);
         });
         tag.put("current", cur);
         CompoundTag next = new CompoundTag();
         this.nextAt.forEach((id, t) -> next.putLong(id.toString(), t));
         tag.put("next", next);
         CompoundTag rd = new CompoundTag();
         this.roads.forEach((id, t) -> rd.putLong(id.toString(), t));
         tag.put("roads", rd);
         ListTag ef = new ListTag();

         for (Effect e : this.effects) {
            CompoundTag x = new CompoundTag();
            x.putString("target", e.target);
            x.putLong("until", e.until);
            CompoundTag b = new CompoundTag();
            e.boosts.forEach((k, v) -> b.putDouble(k.toString(), v));
            x.put("boosts", b);
            ef.add(x);
         }

         tag.put("effects", ef);
         return tag;
      }

      private static Data load(CompoundTag tag) {
         Data d = new Data();
         CompoundTag cur = tag.getCompound("current");

         for (String k : cur.getAllKeys()) {
            CompoundTag x = cur.getCompound(k);
            Kind kind = Kind.byId(x.getString("kind"));
            if (kind != null) {
               d.current.put(UUID.fromString(k), new Offer(kind, x.getString("at"), x.getLong("until")));
            }
         }

         CompoundTag next = tag.getCompound("next");

         for (String k : next.getAllKeys()) {
            d.nextAt.put(UUID.fromString(k), next.getLong(k));
         }

         CompoundTag rd = tag.getCompound("roads");

         for (String k : rd.getAllKeys()) {
            d.roads.put(UUID.fromString(k), rd.getLong(k));
         }

         for (Tag t : tag.getList("effects", 10)) {
            CompoundTag x = (CompoundTag)t;
            Map<ResourceLocation, Double> b = new HashMap<>();
            CompoundTag bt = x.getCompound("boosts");

            for (String k : bt.getAllKeys()) {
               ResourceLocation rl = ResourceLocation.tryParse(k);
               if (rl != null) {
                  b.put(rl, bt.getDouble(k));
               }
            }

            d.effects.add(new Effect(x.getString("target"), b, x.getLong("until")));
         }

         return d;
      }
   }
}
