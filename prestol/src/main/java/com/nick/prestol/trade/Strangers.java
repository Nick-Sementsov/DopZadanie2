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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

/**
 * Merchants from far lands. Now and then a caravan comes by road, or a ship by sea to a settlement with a harbour,
 * to every settlement of a realm: it sells the common goods the settlement lacks, buys up its surplus, stays a
 * while for anyone who wants to haggle, and goes back where it came from. So trade lives even in a world of one
 * realm. Strangers serve no realm: they pay and are paid from their own purse.
 */
public final class Strangers {
   /** The realm id strangers carry: no realm at all. */
   static final UUID NOBODY = new UUID(0L, 0x5354524E47455253L);
   private static final String[] NAMES = {
      "Marco", "Jacopo", "Lorenzo", "Matteo", "Hans", "Klaus", "Wilhelm", "Piet", "Gunnar", "Sven", "Arnaud", "Yusuf", "Ibn Said", "Dmitro", "Bogdan", "Olaf"
   };
   /** What strangers bring besides what a settlement asks for. */
   private static final Item[] WARES = {
      Items.GLASS, Items.PAPER, Items.LEATHER, Items.IRON_INGOT, Items.COAL, Items.STRING, Items.WHITE_WOOL, Items.BRICK, Items.RED_DYE, Items.BLUE_DYE,
      Items.YELLOW_DYE, Items.BOOK, Items.HONEY_BOTTLE, Items.SUGAR, Items.COCOA_BEANS, Items.COPPER_INGOT, Items.LANTERN, Items.CANDLE
   };
   /** What strangers are glad to take home, besides a settlement's surplus. */
   private static final Item[] WANTS = {
      Items.WHEAT, Items.LEATHER, Items.OAK_LOG, Items.SPRUCE_LOG, Items.IRON_INGOT, Items.WHITE_WOOL, Items.BREAD, Items.COAL, Items.COPPER_INGOT,
      Items.EMERALD, Items.LAPIS_LAZULI, Items.HONEYCOMB, Items.PUMPKIN, Items.CARROT, Items.POTATO
   };
   private static final double SELL = 1.3;
   private static final double BUY = 0.75;
   /** Strangers bring common goods only: nothing dearer than this apiece. */
   private static final double DEAREST = 8.0;
   private static final int MAX = 6;
   private static final int CHECK_TICKS = 400;

   private Strangers() {
   }

   public static boolean on() {
      try {
         return Trading.on() && PrestolConfig.STRANGER_DAYS.get() > 0.0;
      } catch (IllegalStateException e) {
         return false;
      }
   }

   // ------------------------------------------------------------------ coming

   /** Every second: now and then a stranger sets out for a settlement whose turn it is. */
   static void tick(MinecraftServer server, TradeData data, long now) {
      if (!on() || now % CHECK_TICKS != 0L) {
         return;
      }

      int count = 0;
      List<String> visited = new ArrayList<>();

      for (Trader t : data.traders.values()) {
         if (t.stranger) {
            count++;
            if (!t.route.isEmpty()) {
               visited.add(t.route.get(0).settlement);
            }
         }
      }

      RandomSource rnd = server.overworld().random;
      long every = (long)(PrestolConfig.STRANGER_DAYS.get() * 24000.0);
      RealmData realms = RealmData.get(server);

      for (IColony c : Settlements.all()) {
         String key = Settlements.key(c);
         if (realms.byMember(key) == null || visited.contains(key)) {
            continue;
         }

         Long next = data.nextVisit.get(key);
         if (next == null) {
            data.nextVisit.put(key, now + (long)(every * (0.3 + rnd.nextDouble())));
         } else if (now >= next && count < MAX) {
            data.nextVisit.put(key, now + (long)(every * (0.6 + 0.8 * rnd.nextDouble())));
            if (send(server, data, c, now, rnd)) {
               count++;
            }
         }
      }
   }

   /** A stranger comes to {@code c} now (a fair called him). */
   public static void visit(MinecraftServer server, IColony c) {
      if (on()) {
         send(server, TradeData.get(server), c, server.overworld().getGameTime(), server.overworld().random);
      }
   }

   /** A stranger sets out for {@code c} from somewhere beyond the horizon. */
   static boolean send(MinecraftServer server, TradeData data, IColony c, long now, RandomSource rnd) {
      String key = Settlements.key(c);
      Realm realm = RealmData.get(server).byMember(key);
      if (realm == null) {
         return false;
      }

      boolean ship = Trading.port(c) && rnd.nextBoolean();
      Trader.Kind kind = ship ? Trader.Kind.SHIP : Trader.Kind.CARAVAN;
      Vec3 at = Vec3.atBottomCenterOf(Trading.anchor(c, kind));
      String name = NAMES[rnd.nextInt(NAMES.length)];
      Trader t = new Trader(UUID.randomUUID(), name, NOBODY, kind);
      t.stranger = true;
      t.running = true;
      t.animals = ship ? 0 : 2 + rnd.nextInt(2);
      t.cargo.size = t.capacity();
      t.route.add(new Stop(key));
      t.dim = Trading.dim(c);
      t.pos = far(at, rnd);
      t.exit = far(at, rnd);
      t.purse = 150L + rnd.nextInt(250);
      Markets.Market mk = Markets.of(server, key);
      // what the settlement asks for, as far as it is common goods
      for (Map.Entry<Item, Integer> e : mk.needs().entrySet()) {
         if (common(e.getKey())) {
            t.cargo.add(e.getKey(), Math.min(e.getValue(), e.getKey().getMaxStackSize() * 2));
         }
      }

      if (mk.hunger() > 0) {
         t.cargo.add(Items.BREAD, Math.min(mk.hunger(), 128));
      }

      for (int i = 0, n = 2 + rnd.nextInt(3); i < n; i++) {
         Item w = WARES[rnd.nextInt(WARES.length)];
         t.cargo.add(w, 8 + rnd.nextInt(41));
      }

      mk.surplus()
         .entrySet()
         .stream()
         .sorted(Comparator.comparingDouble((Map.Entry<Item, Integer> e) -> -e.getValue() * Prices.of(e.getKey())))
         .limit(3)
         .forEach(e -> t.wanted.add(Cargo.id(e.getKey())));

      for (int i = 0; i < 2; i++) {
         String w = Cargo.id(WANTS[rnd.nextInt(WANTS.length)]);
         if (!t.wanted.contains(w)) {
            t.wanted.add(w);
         }
      }

      t.status("stranger_coming", c.getName());
      data.traders.put(t.id, t);
      Realms.tell(server, realm, Msg.info("trade.stranger_coming", Msg.t("trade.kind." + kind.id()), name, c.getName()));
      return true;
   }

   private static Vec3 far(Vec3 at, RandomSource rnd) {
      double a = rnd.nextDouble() * Math.PI * 2.0;
      double d = 350.0 + rnd.nextDouble() * 200.0;
      return new Vec3(at.x + Math.cos(a) * d, at.y, at.z + Math.sin(a) * d);
   }

   /** Plain, common goods: what a passing merchant would carry. */
   private static boolean common(Item item) {
      double p = Prices.of(item);
      return p > 0.0 && p <= DEAREST && new ItemStack(item).getRarity() == Rarity.COMMON && !item.canBeDepleted();
   }

   // ------------------------------------------------------------------ the road

   /** Whether he is on his way just now (to the settlement or back home). */
   static boolean moving(Trader t, long now) {
      return now >= t.dwellUntil && t.phase != 1;
   }

   /** Where he is heading: the settlement, or home beyond the horizon; null while he stays. */
   static Vec3 goal(Trader t) {
      if (t.phase == 2) {
         return t.exit;
      } else if (t.phase == 1 || t.route.isEmpty()) {
         return null;
      } else {
         IColony c = Settlements.byKey(t.route.get(0).settlement);
         return c == null ? t.exit : Vec3.atBottomCenterOf(Trading.anchor(c, t.kind));
      }
   }

   /** A step along his way, like a trader's: {@code moved} is where he really is, or null to travel in the books. */
   static void advance(MinecraftServer server, TradeData data, Trader t, long now, double seconds, Vec3 moved) {
      if (now < t.dwellUntil) {
         return;
      }

      if (t.phase == 1) {
         t.phase = 2;
         t.status("stranger_leaving");
      }

      IColony colony = t.route.isEmpty() ? null : Settlements.byKey(t.route.get(0).settlement);
      if (colony == null && t.phase == 0) {
         t.phase = 2;
      }

      Vec3 goal = goal(t);
      double reach;
      if (moved != null) {
         t.pos = moved;
         reach = t.phase == 2 ? 12.0 : (t.kind == Trader.Kind.SHIP ? 20.0 : 8.0);
      } else {
         reach = 0.5;
         double dx = goal.x - t.pos.x;
         double dz = goal.z - t.pos.z;
         double d = Math.sqrt(dx * dx + dz * dz);
         double step = (t.kind == Trader.Kind.SHIP ? PrestolConfig.SHIP_SPEED.get() : PrestolConfig.CARAVAN_SPEED.get()) * seconds;
         t.pos = d <= step ? goal : new Vec3(t.pos.x + dx / d * step, t.pos.y + (goal.y - t.pos.y) * step / d, t.pos.z + dz / d * step);
      }

      double hx = goal.x - t.pos.x;
      double hz = goal.z - t.pos.z;
      if (hx * hx + hz * hz <= reach * reach) {
         if (t.phase == 2) {
            TradeWorld.despawn(server, t, true);
            data.traders.remove(t.id);
         } else {
            arrive(server, t, colony);
            if (t.physical()) {
               TradeWorld.pushCargo(server, t);
            }

            t.phase = 1;
            t.dwellUntil = now + PrestolConfig.DWELL_SECONDS.get() * 60L;
            t.status("stranger_trading", colony.getName());
         }
      } else if (t.phase == 0) {
         t.status("stranger_coming", colony.getName());
      }
   }

   // ------------------------------------------------------------------ the market

   /** In the settlement: he sells it what it lacks and buys up what it has to spare, as far as purses allow. */
   static void arrive(MinecraftServer server, Trader t, IColony colony) {
      String key = Settlements.key(colony);
      Realm realm = RealmData.get(server).byMember(key);
      if (realm == null) {
         return;
      }

      EconomyData eco = EconomyData.get(server);
      long day = Realms.day(Realms.now(server));
      Markets.Market mk = Markets.of(server, key);
      List<IItemHandler> stores = Supplies.stores(colony, null);
      List<Component> sold = new ArrayList<>();
      List<Component> bought = new ArrayList<>();
      int hunger = mk.hunger();

      for (Map.Entry<String, Integer> e : t.cargo.summary().entrySet()) {
         Item item = Cargo.item(e.getKey());
         int need = mk.needs().getOrDefault(item, 0);
         if (need == 0 && item.isEdible()) {
            need = hunger;
         }

         double unit = Prices.of(item) * SELL;
         int qty = Math.min(e.getValue(), need);
         if (unit > 0.0) {
            qty = (int)Math.min((long)qty, (long)(eco.balance(realm.id()) / unit));
         }

         if (qty <= 0) {
            continue;
         }

         int put = 0;

         for (ItemStack s : t.cargo.take(item, qty)) {
            ItemStack left = Cargo.insert(stores, s);
            put += s.getCount() - left.getCount();
            if (!left.isEmpty()) {
               t.cargo.add(left.getItem(), left.getCount());
            }
         }

         long cost = Math.max(1L, Math.round(put * unit));
         if (put > 0 && eco.take(realm.id(), cost)) {
            if (item.isEdible() && !mk.needs().containsKey(item)) {
               hunger -= put;
            }

            t.purse += cost;
            eco.log(realm.id(), day, "stranger_sold", -cost, t.name, colony.getName(), item.getDescriptionId(), String.valueOf(put));
            sold.add(Msg.t("trade.plan_item", Trading.itemName(item), put));
         }
      }

      List<Map.Entry<Item, Integer>> spare = new ArrayList<>(mk.surplus().entrySet());
      spare.sort(Comparator.comparingDouble((Map.Entry<Item, Integer> e) -> -e.getValue() * Prices.of(e.getKey())));

      for (Map.Entry<Item, Integer> e : spare) {
         Item item = e.getKey();
         double unit = Prices.of(item) * BUY;
         if (unit <= 0.0 || t.purse < 1L) {
            continue;
         }

         int qty = Math.min(Math.min(e.getValue(), t.cargo.room(item)), item.getMaxStackSize() * 4);
         qty = (int)Math.min((long)qty, (long)(t.purse / unit));
         if (qty <= 0 || Math.round(qty * unit) < 1L) {
            continue;
         }

         int got = 0;

         for (ItemStack s : Cargo.extract(stores, item, qty)) {
            int in = t.cargo.add(s.getItem(), s.getCount());
            got += in;
            if (in < s.getCount()) {
               Cargo.insert(stores, new ItemStack(s.getItem(), s.getCount() - in));
            }
         }

         long cost = Math.round(got * unit);
         if (got > 0 && cost > 0L) {
            t.purse -= cost;
            eco.give(realm.id(), cost);
            eco.log(realm.id(), day, "stranger_bought", cost, t.name, colony.getName(), item.getDescriptionId(), String.valueOf(got));
            bought.add(Msg.t("trade.plan_item", Trading.itemName(item), got));
         }
      }

      Markets.touched(key);
      MutableComponent msg = Msg.t("trade.stranger_arrived", Msg.t("trade.kind." + t.kind.id()), t.name, colony.getName()).withStyle(ChatFormatting.GOLD);
      if (!sold.isEmpty()) {
         msg.append(" ").append(join(Msg.t("trade.stranger_sold"), sold).withStyle(ChatFormatting.YELLOW));
      }

      if (!bought.isEmpty()) {
         msg.append(" ").append(join(Msg.t("trade.stranger_bought"), bought).withStyle(ChatFormatting.YELLOW));
      }

      if (sold.isEmpty() && bought.isEmpty()) {
         msg.append(" ").append(Msg.t("trade.stranger_nothing").withStyle(ChatFormatting.GRAY));
      }

      t.note(Trading.json(msg));
      Realms.tell(server, realm, msg);
   }

   private static MutableComponent join(MutableComponent head, List<Component> parts) {
      for (int i = 0; i < parts.size(); i++) {
         head.append(i == 0 ? " " : ", ").append(parts.get(i));
      }

      return head;
   }

   /** A stranger's offers to players: he buys what he came for, at his price, while his purse lasts. */
   static void wants(Trader t, List<Haggle.Deal> out) {
      for (String id : t.wanted) {
         Item item = Cargo.item(id);
         double unit = Prices.of(item) * BUY;
         if (unit > 0.0) {
            int lot = Math.max(1, Math.min(item.getMaxStackSize(), (int)Math.ceil(1.0 / unit - 1.0E-9)));
            long coins = (long)Math.floor(lot * unit);
            if (coins >= 1L) {
               out.add(new Haggle.Deal(item, lot, coins, false, item.getMaxStackSize() * 2));
            }
         }
      }
   }

   static String coins(long n) {
      return Coins.fmt(n);
   }
}
