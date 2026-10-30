package com.nick.prestol.trade;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.army.Supplies;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Camps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

/**
 * A trader who seeks his own bargains. Each trip starts and ends in his home settlement: at home he hands in what
 * he brought, looks over the markets within reach and picks the one trip worth the most — to sell the home
 * surplus where it is wanted, to buy what home lacks where it lies in plenty, or, inside the realm, to carry goods
 * from a settlement that has them to one that needs them. If nothing is worth the road he waits and looks again.
 */
public final class AutoTrade {
   /** What goods the home settlement has to spare are worth to it, against their price. */
   private static final double SPARE_WORTH = 0.3;
   /** What goods the realm needs are worth to it, against their price. */
   private static final double NEED_WORTH = 1.6;
   /** Carried inside the realm, goods are worth this much of their price. */
   private static final double CARRY_WORTH = 0.6;
   /** Distance at which a trip is worth half as much. */
   private static final double ROAD = 500.0;
   private static final double MIN_GAIN = 1.0;
   private static final long REPLAN_TICKS = 2400L;
   private static final int MAX_LINES = 8;

   private AutoTrade() {
   }

   /** A trader set to plan his own trips: back home first, then on his way. */
   static void start(MinecraftServer server, Trader t) {
      if (t.home.isEmpty() || Settlements.byKey(t.home) == null) {
         Realm r = RealmData.get(server).byId(t.realm);
         t.home = r == null ? "" : r.capital();
      }

      t.route.clear();
      t.route.add(new Stop(t.home));
      t.route.add(new Stop(t.home));
      t.target = 1;
      t.wp = 0;
      t.dwellUntil = 0L;
      t.plan.clear();
   }

   /** Every second for a trader with no trip: once in a while he looks for one again. */
   static void idle(MinecraftServer server, TradeData data, Trader t, long now) {
      if (t.auto && t.running && t.route.size() < 2 && now >= t.nextPlan) {
         IColony home = Settlements.byKey(t.home);
         if (home == null) {
            start(server, t);
         } else {
            plan(server, t, home, now);
            if (t.route.size() >= 2) {
               t.target = 1;
               t.leaving = true;
            }
         }
      }
   }

   /** Home again: he hands in his load, closes the books of the trip and plans the next one. */
   static void atHome(MinecraftServer server, Trader t, IColony home, long now) {
      List<IItemHandler> stores = Supplies.stores(home, null);
      List<Component> handed = new ArrayList<>();

      for (Map.Entry<String, Integer> e : t.cargo.summary().entrySet()) {
         Item item = Cargo.item(e.getKey());
         int put = 0;

         for (ItemStack s : t.cargo.take(item, e.getValue())) {
            ItemStack left = Cargo.insert(stores, s);
            put += s.getCount() - left.getCount();
            if (!left.isEmpty()) {
               t.cargo.add(left.getItem(), left.getCount());
            }
         }

         if (put > 0) {
            handed.add(Msg.t("trade.unloaded", Trading.itemName(item), put));
         }
      }

      if (t.cargo.used() > 0) {
         t.note(Trading.json(Msg.bad("trade.home_full", home.getName())));
      }

      Markets.touched(t.home);
      Realm mine = RealmData.get(server).byId(t.realm);
      if (!t.plan.isEmpty() && mine != null) {
         t.trips++;
         t.lastProfit = t.tripEarned - t.tripSpent;
         MutableComponent report = Msg.t("trade.trip_done", t.name, Coins.fmt(t.tripEarned), Coins.fmt(t.tripSpent)).withStyle(ChatFormatting.GOLD);
         for (int i = 0; i < handed.size(); i++) {
            report.append(i == 0 ? ": " : ", ").append(handed.get(i).copy().withStyle(ChatFormatting.YELLOW));
         }

         t.note(Trading.json(report));
         if (t.tripEarned > 0L || !handed.isEmpty()) {
            Realms.tell(server, mine, report);
         }
      }

      t.tripEarned = 0L;
      t.tripSpent = 0L;
      plan(server, t, home, now);
   }

   /** One line of a trip: goods to sell or to buy there (foreign market), or to drop or to take (own settlement). */
   private record Line(Item item, int qty, double price, double gain, boolean out, TradeOrder.Kind kind) {
      int slots() {
         return (this.qty + this.item.getMaxStackSize() - 1) / this.item.getMaxStackSize();
      }
   }

   private record Trip(String key, IColony colony, List<Line> lines, double score) {
   }

   /** Finds the best trip from home, loads for it and sets the route; with no trip worth it he stays. */
   static void plan(MinecraftServer server, Trader t, IColony home, long now) {
      t.route.clear();
      t.route.add(new Stop(t.home));
      t.target = 0;
      t.wp = 0;
      t.plan.clear();
      t.nextPlan = now + REPLAN_TICKS;
      RealmData realms = RealmData.get(server);
      Realm mine = realms.byId(t.realm);
      if (mine == null) {
         return;
      }

      EconomyData eco = EconomyData.get(server);
      TradeData data = TradeData.get(server);
      Markets.Market here = claimed(data, t, t.home, Markets.of(server, t.home), true);
      Trip best = null;
      BlockPos from = Trading.anchor(home, t.kind);
      int range = PrestolConfig.TRADE_RANGE.get();

      for (IColony c : Settlements.all()) {
         String key = Settlements.key(c);
         Realm owner = realms.byMember(key);
         if (key.equals(t.home) || owner == null || !Trading.dim(c).equals(t.dim)) {
            continue;
         }

         if (t.kind == Trader.Kind.SHIP && !Trading.port(c)) {
            continue;
         }

         boolean own = owner.id().equals(mine.id());
         if (!own && (realms.war(mine.id(), owner.id()) != null)) {
            continue;
         }

         if (Camps.blockade(realms, key, mine.id()) != null) {
            continue;
         }

         BlockPos to = Trading.anchor(c, t.kind);
         double dist = Math.sqrt(from.distSqr(to));
         if (dist > range) {
            continue;
         }

         Markets.Market there = claimed(data, t, key, Markets.of(server, key), false);
         List<Line> lines = own ? carry(here, there) : barter(here, there, Trading.duty(eco, mine, owner), eco.balance(mine.id()) / 2L);
         List<Line> chosen = fit(lines, t.cargo.size() - t.cargo.used());
         double gain = 0.0;

         for (Line l : chosen) {
            gain += l.gain();
         }

         double score = gain / (1.0 + dist / ROAD) - (double)t.guards * PrestolConfig.ESCORT_COST.get() * 2;
         if (!chosen.isEmpty() && score >= MIN_GAIN && (best == null || score > best.score())) {
            best = new Trip(key, c, chosen, score);
         }
      }

      if (best == null) {
         t.status("auto_waiting", home.getName());
         t.plan.add(Trading.json(Msg.t("trade.plan_none")));
         return;
      }

      // load what goes out, as much as the stores really give
      List<IItemHandler> stores = Supplies.stores(home, null);
      Stop stop = new Stop(best.key());
      List<Component> carried = new ArrayList<>();
      List<Component> sought = new ArrayList<>();

      for (Line l : best.lines()) {
         if (l.out()) {
            int got = 0;

            for (ItemStack s : Cargo.extract(stores, l.item(), l.qty())) {
               int in = t.cargo.add(s.getItem(), s.getCount());
               got += in;
               if (in < s.getCount()) {
                  Cargo.insert(stores, new ItemStack(s.getItem(), s.getCount() - in));
               }
            }

            if (got > 0) {
               stop.orders.add(new TradeOrder(l.kind(), Cargo.id(l.item()), got, l.kind() == TradeOrder.Kind.SELL ? l.price() * 0.8 : 0.0));
               carried.add(Msg.t("trade.plan_item", Trading.itemName(l.item()), got));
            }
         } else {
            stop.orders.add(new TradeOrder(l.kind(), Cargo.id(l.item()), l.qty(), l.kind() == TradeOrder.Kind.BUY ? l.price() * 1.2 : 0.0));
            sought.add(Msg.t("trade.plan_item", Trading.itemName(l.item()), l.qty()));
         }
      }

      Markets.touched(t.home);
      if (stop.orders.isEmpty()) {
         t.status("auto_waiting", home.getName());
         t.plan.add(Trading.json(Msg.t("trade.plan_none")));
         return;
      }

      t.route.add(stop);
      t.status("auto_planned", best.colony().getName());
      t.plan.add(Trading.json(Msg.t("trade.plan_to", best.colony().getName())));
      if (!carried.isEmpty()) {
         t.plan.add(Trading.json(join(Msg.t("trade.plan_carries"), carried)));
      }

      if (!sought.isEmpty()) {
         t.plan.add(Trading.json(join(Msg.t("trade.plan_seeks"), sought)));
      }

      t.note(Trading.json(join(Msg.t("trade.plan_note", t.name, best.colony().getName()).withStyle(ChatFormatting.GOLD), carried)));
   }

   /**
    * A market as it will be once the other traders on the road have done what they set out to do: goods already
    * on their way to a settlement are not wanted there twice. {@code home} counts what other traders of the same
    * home bring back to it; otherwise what is brought to the settlement as a foreign or an own stop.
    */
   private static Markets.Market claimed(TradeData data, Trader me, String key, Markets.Market m, boolean home) {
      Map<Item, Integer> coming = new java.util.HashMap<>();

      for (Trader o : data.traders.values()) {
         if (o == me || !o.running || !o.auto || o.route.size() < 2) {
            continue;
         }

         for (Stop st : o.route) {
            boolean toThem = !home && st.settlement.equals(key);
            boolean toUs = home && o.home.equals(key) && !st.settlement.equals(key);

            for (TradeOrder ord : st.orders) {
               boolean brings = toThem && (ord.kind() == TradeOrder.Kind.SELL || ord.kind() == TradeOrder.Kind.UNLOAD)
                  || toUs && (ord.kind() == TradeOrder.Kind.BUY || ord.kind() == TradeOrder.Kind.LOAD);
               if (brings) {
                  coming.merge(Cargo.item(ord.item()), ord.amount(), Integer::sum);
               }
            }
         }
      }

      if (coming.isEmpty()) {
         return m;
      } else {
         Map<Item, Integer> needs = new java.util.LinkedHashMap<>(m.needs());
         int hunger = m.hunger();
         Map<Item, Integer> stock = new java.util.HashMap<>(m.stock());

         for (Map.Entry<Item, Integer> e : coming.entrySet()) {
            stock.merge(e.getKey(), e.getValue(), Integer::sum);
            Integer n = needs.get(e.getKey());
            if (n != null) {
               if (n > e.getValue()) {
                  needs.put(e.getKey(), n - e.getValue());
               } else {
                  needs.remove(e.getKey());
               }
            } else if (e.getKey().isEdible()) {
               hunger = Math.max(0, hunger - e.getValue());
            }
         }

         return new Markets.Market(m.offers(), stock, m.surplus(), needs, hunger, m.at());
      }
   }

   private static MutableComponent join(MutableComponent head, List<Component> parts) {
      for (int i = 0; i < parts.size(); i++) {
         head.append(i == 0 ? " " : ", ").append(parts.get(i));
      }

      return head;
   }

   /** Trade with a foreign market: our spare goods for its coins, its spare goods for what we lack. */
   private static List<Line> barter(Markets.Market here, Markets.Market there, int duty, long budget) {
      List<Line> out = new ArrayList<>();
      double spend = 0.0;
      int hunger = here.hunger();

      for (Offer o : there.offers()) {
         Item item = Cargo.item(o.item());
         double base = Prices.of(item);
         if (base <= 0.0) {
            continue;
         }

         if (!o.sell()) {
            int want = o.amount() - there.stockOf(item);
            int have = here.surplus().getOrDefault(item, 0);
            int qty = Math.min(want, have);
            double net = o.price() * (100 - duty) / 100.0;
            double gain = qty * (net - base * SPARE_WORTH);
            if (qty > 0 && gain > 0.0 && Math.round(qty * o.price()) > 0L) {
               out.add(new Line(item, qty, o.price(), gain, true, TradeOrder.Kind.SELL));
            }
         } else {
            int avail = there.stockOf(item) - o.amount();
            int need = here.needs().getOrDefault(item, 0);
            if (need == 0 && item.isEdible() && hunger > 0) {
               need = hunger;
            }

            int qty = Math.min(avail, need);
            double unit = o.price() * (100 + duty) / 100.0;
            if (unit > 0.0) {
               qty = (int)Math.min((long)qty, (long)((budget - spend) / unit));
            }

            double gain = qty * (base * NEED_WORTH - unit);
            if (qty > 0 && gain > 0.0) {
               spend += qty * unit;
               if (!here.needs().containsKey(item)) {
                  hunger -= qty;
               }

               out.add(new Line(item, qty, o.price(), gain, false, TradeOrder.Kind.BUY));
            }
         }
      }

      return out;
   }

   /** Inside the realm: home's spare goods to a settlement that needs them, and its spare goods home. */
   private static List<Line> carry(Markets.Market here, Markets.Market there) {
      List<Line> out = new ArrayList<>();

      for (Map.Entry<Item, Integer> e : there.needs().entrySet()) {
         int qty = Math.min(e.getValue(), here.surplus().getOrDefault(e.getKey(), 0));
         if (qty > 0) {
            out.add(new Line(e.getKey(), qty, 0.0, qty * Prices.of(e.getKey()) * CARRY_WORTH, true, TradeOrder.Kind.UNLOAD));
         }
      }

      for (Map.Entry<Item, Integer> e : here.needs().entrySet()) {
         int qty = Math.min(e.getValue(), there.surplus().getOrDefault(e.getKey(), 0));
         if (qty > 0) {
            out.add(new Line(e.getKey(), qty, 0.0, qty * Prices.of(e.getKey()) * CARRY_WORTH, false, TradeOrder.Kind.LOAD));
         }
      }

      // food for a hungry larder, either way
      food(out, here, there.hunger(), true);
      food(out, there, here.hunger(), false);
      return out;
   }

   private static void food(List<Line> out, Markets.Market from, int hunger, boolean outbound) {
      for (Map.Entry<Item, Integer> e : from.surplus().entrySet()) {
         if (hunger <= 0) {
            break;
         }

         if (e.getKey().isEdible()) {
            int qty = Math.min(hunger, e.getValue());
            hunger -= qty;
            out.add(new Line(e.getKey(), qty, 0.0, qty * Prices.of(e.getKey()) * CARRY_WORTH, outbound, outbound ? TradeOrder.Kind.UNLOAD : TradeOrder.Kind.LOAD));
         }
      }
   }

   /** The richest lines that fit in the packs: the way out and the way back each have the whole room. */
   private static List<Line> fit(List<Line> lines, int room) {
      List<Line> sorted = new ArrayList<>(lines);
      sorted.sort(Comparator.comparingDouble((Line l) -> -l.gain() / Math.max(1, l.slots())));
      List<Line> out = new ArrayList<>();
      int outbound = room;
      int inbound = room;

      for (Line l : sorted) {
         if (out.size() >= MAX_LINES) {
            break;
         }

         int left = l.out() ? outbound : inbound;
         if (left <= 0) {
            continue;
         }

         Line take = l;
         if (l.slots() > left) {
            int qty = left * l.item().getMaxStackSize();
            take = new Line(l.item(), qty, l.price(), l.gain() * qty / l.qty(), l.out(), l.kind());
         }

         if (take.out()) {
            outbound -= take.slots();
         } else {
            inbound -= take.slots();
         }

         out.add(take);
      }

      return out;
   }

   /** Where a trader of this realm can make his home: a settlement with a warehouse, or a harbour for a ship. */
   static boolean homeFor(IColony c, Trader.Kind kind) {
      return kind == Trader.Kind.SHIP ? Trading.port(c) : Trading.warehouse(c);
   }

   static Vec3 homePos(IColony c, Trader.Kind kind) {
      return Vec3.atBottomCenterOf(Trading.anchor(c, kind));
   }
}
