package com.nick.prestol.trade;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

/**
 * Markets and traders as the realm book sees them: the orders that set them up, the trades a trader makes when
 * he comes to a stop, and the page that shows them.
 */
public final class Trading {
   private Trading() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.TRADE.get() && PrestolConfig.ECONOMY.get();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   // ------------------------------------------------------------------ places

   static boolean has(IColony colony, String building) {
      for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
         if (b.getClass().getSimpleName().equals(building) && b.getBuildingLevel() > 0) {
            return true;
         }
      }

      return false;
   }

   static boolean port(IColony colony) {
      return has(colony, "BuildingFisherman");
   }

   static boolean warehouse(IColony colony) {
      return has(colony, "BuildingWareHouse");
   }

   /** Where a trader goes in a settlement: the town hall, or for a ship the fisherman's hut (the harbour). */
   static BlockPos anchor(IColony colony, Trader.Kind kind) {
      if (kind == Trader.Kind.SHIP) {
         for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
            if (b.getClass().getSimpleName().equals("BuildingFisherman") && b.getBuildingLevel() > 0) {
               return b.getPosition();
            }
         }
      }

      IBuilding hall = colony.getServerBuildingManager().getTownHall();
      return hall != null ? hall.getPosition() : colony.getCenter();
   }

   static String dim(IColony colony) {
      return colony.getDimension().location().toString();
   }

   static boolean allied(Realm a, Realm b) {
      return a != null && b != null && a.allies().contains(b.id()) && b.allies().contains(a.id());
   }

   /** Duty a trader of {@code mine} pays in a settlement of {@code there}, in percent. */
   static int duty(EconomyData eco, Realm mine, Realm there) {
      return there == null || mine == null || there.id().equals(mine.id()) || allied(mine, there) ? 0 : eco.duty(there.id());
   }

   static Component itemName(Item item) {
      return Component.translatable(item.getDescriptionId());
   }

   // ------------------------------------------------------------------ arrival

   /** The trader has come to a stop: unloads and sells first, then loads and buys. */
   static void arrive(MinecraftServer server, TradeData data, Trader trader, Stop stop, IColony colony) {
      RealmData realms = RealmData.get(server);
      EconomyData eco = EconomyData.get(server);
      Realm mine = realms.byId(trader.realm);
      String key = stop.settlement;
      Realm there = realms.byMember(key);
      boolean own = there != null && there.id().equals(trader.realm);
      long day = Realms.day(Realms.now(server));
      List<Component> done = new ArrayList<>();
      List<Component> theirs = new ArrayList<>();
      long earned = 0L;
      long spent = 0L;
      if (mine == null) {
         return;
      }

      if (!own && there != null && realms.war(trader.realm, there.id()) != null) {
         trader.note(json(Msg.bad("trade.at_war", colony.getName())));
         return;
      }

      List<IItemHandler> stores = Supplies.stores(colony, null);
      if (stores.isEmpty()) {
         trader.note(json(Msg.bad("trade.no_stores", colony.getName())));
         return;
      }

      List<Offer> market = data.market(key);
      int duty = duty(eco, mine, there);

      for (int pass = 0; pass < 2; pass++) {
         for (TradeOrder o : stop.orders) {
            boolean out = o.kind() == TradeOrder.Kind.UNLOAD || o.kind() == TradeOrder.Kind.SELL;
            if (out == (pass == 0)) {
               Item item = Cargo.item(o.item());
               if (item != Items.AIR) {
                  // in his own realm a trader just loads and unloads: there is nothing to pay
                  TradeOrder.Kind kind = own && o.kind().market() ? (o.kind() == TradeOrder.Kind.BUY ? TradeOrder.Kind.LOAD : TradeOrder.Kind.UNLOAD) : o.kind();
                  switch (kind) {
                     case LOAD:
                        if (own) {
                           int n = Math.min(o.amount(), Math.min(Cargo.stock(stores, item), trader.cargo.room(item)));
                           if (n > 0) {
                              for (ItemStack s : Cargo.extract(stores, item, n)) {
                                 trader.cargo.add(s.getItem(), s.getCount());
                              }

                              done.add(Msg.t("trade.loaded", itemName(item), n));
                           }
                        }
                        break;
                     case UNLOAD:
                        if (own) {
                           int n = Math.min(o.amount(), trader.cargo.count(item));
                           int put = 0;

                           for (ItemStack s : trader.cargo.take(item, n)) {
                              ItemStack left = Cargo.insert(stores, s);
                              put += s.getCount() - left.getCount();
                              if (!left.isEmpty()) {
                                 trader.cargo.add(left.getItem(), left.getCount());
                              }
                           }

                           if (put > 0) {
                              done.add(Msg.t("trade.unloaded", itemName(item), put));
                           }
                        }
                        break;
                     case SELL: {
                        Offer offer = find(market, o.item(), false);
                        if (there != null && offer != null && offer.price() >= o.price() && offer.price() > 0) {
                           int want = offer.amount() - Cargo.stock(stores, item);
                           long money = eco.balance(there.id());
                           int n = (int)Math.min(
                              (long)Math.min(Math.min(o.amount(), trader.cargo.count(item)), Math.min(want, Cargo.space(stores, item))), money / offer.price()
                           );
                           if (n > 0) {
                              int put = 0;

                              for (ItemStack s : trader.cargo.take(item, n)) {
                                 ItemStack left = Cargo.insert(stores, s);
                                 put += s.getCount() - left.getCount();
                                 if (!left.isEmpty()) {
                                    trader.cargo.add(left.getItem(), left.getCount());
                                 }
                              }

                              if (put > 0) {
                                 long cost = (long)put * offer.price();
                                 long net = cost - cost * duty / 100L;
                                 eco.take(there.id(), net);
                                 eco.give(mine.id(), net);
                                 eco.log(mine.id(), day, "sold", net, trader.name, colony.getName(), item.getDescriptionId(), String.valueOf(put));
                                 eco.log(there.id(), day, "bought", -net, mine.name(), colony.getName(), item.getDescriptionId(), String.valueOf(put));
                                 earned += net;
                                 done.add(Msg.t("trade.sold", itemName(item), put, Coins.fmt(net)));
                                 theirs.add(Msg.t("trade.they_sold", itemName(item), put, Coins.fmt(net)));
                              }
                           }
                        }
                        break;
                     }
                     case BUY: {
                        Offer offer = find(market, o.item(), true);
                        if (there != null && offer != null && offer.price() <= o.price()) {
                           int avail = Cargo.stock(stores, item) - offer.amount();
                           long unit = (long)offer.price() * (100 + duty);
                           long money = eco.balance(mine.id()) * 100L;
                           int n = Math.min(Math.min(o.amount(), avail), trader.cargo.room(item));
                           if (unit > 0L) {
                              n = (int)Math.min((long)n, money / unit);
                           }

                           if (n > 0) {
                              int got = 0;

                              for (ItemStack s : Cargo.extract(stores, item, n)) {
                                 int in = trader.cargo.add(s.getItem(), s.getCount());
                                 got += in;
                                 if (in < s.getCount()) {
                                    Cargo.insert(stores, new ItemStack(s.getItem(), s.getCount() - in));
                                 }
                              }

                              if (got > 0) {
                                 long cost = (long)got * offer.price();
                                 long total = cost + cost * duty / 100L;
                                 eco.take(mine.id(), total);
                                 eco.give(there.id(), total);
                                 eco.log(mine.id(), day, "purchase", -total, trader.name, colony.getName(), item.getDescriptionId(), String.valueOf(got));
                                 eco.log(there.id(), day, "sale", total, mine.name(), colony.getName(), item.getDescriptionId(), String.valueOf(got));
                                 spent += total;
                                 done.add(Msg.t("trade.bought", itemName(item), got, Coins.fmt(total)));
                                 theirs.add(Msg.t("trade.they_bought", itemName(item), got, Coins.fmt(total)));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      MutableComponent report = Msg.t("trade.arrived", trader.name, colony.getName()).withStyle(ChatFormatting.GOLD);
      if (done.isEmpty()) {
         report.append(" ").append(Msg.t("trade.nothing").withStyle(ChatFormatting.GRAY));
      } else {
         for (int i = 0; i < done.size(); i++) {
            report.append(i == 0 ? ": " : ", ").append(done.get(i).copy().withStyle(ChatFormatting.YELLOW));
         }
      }

      trader.note(json(report));
      if (!done.isEmpty()) {
         Realms.tell(server, mine, report);
      }

      if (!theirs.isEmpty() && there != null) {
         MutableComponent msg = Msg.t("trade.visit", mine.name(), colony.getName()).withStyle(ChatFormatting.GOLD);

         for (int i = 0; i < theirs.size(); i++) {
            msg.append(i == 0 ? ": " : ", ").append(theirs.get(i).copy().withStyle(ChatFormatting.YELLOW));
         }

         Realms.tell(server, there, msg);
      }
   }

   static Offer find(List<Offer> market, String item, boolean sell) {
      for (Offer o : market) {
         if (o.sell() == sell && o.item().equals(item)) {
            return o;
         }
      }

      return null;
   }

   static String json(Component c) {
      return Serializer.toJson(c);
   }

   // ------------------------------------------------------------------ orders

   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String[] w) {
      if (!on()) {
         return Realms.Result.no(Msg.bad("err.trade_off"));
      } else if (w.length < 2) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      } else if (w[1].equals("market")) {
         return market(server, player, w);
      } else {
         Realm realm = Realms.realmOf(server, player);
         if (realm == null) {
            return Realms.Result.no(Msg.bad("err.need_realm"));
         } else if (!Realms.isRuler(realm, player)) {
            return Realms.Result.no(Msg.bad("err.not_ruler"));
         } else {
            TradeData data = TradeData.get(server);
            if (w[1].equals("new")) {
               return create(server, data, player, realm, w);
            } else {
               Trader t = w.length > 2 ? byId(data, w[2]) : null;
               if (t == null || !t.realm.equals(realm.id())) {
                  return Realms.Result.no(Msg.bad("err.no_trader"));
               } else {
                  return edit(server, data, player, realm, t, w);
               }
            }
         }
      }
   }

   private static Trader byId(TradeData data, String id) {
      try {
         return data.traders.get(UUID.fromString(id));
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   private static int num(String[] w, int i) {
      try {
         return w.length > i ? Integer.parseInt(w[i]) : -1;
      } catch (NumberFormatException var3) {
         return -1;
      }
   }

   private static String rest(String[] w, int from) {
      StringBuilder b = new StringBuilder();

      for (int i = from; i < w.length; i++) {
         if (i > from) {
            b.append(' ');
         }

         b.append(w[i]);
      }

      return b.toString().trim();
   }

   private static Realms.Result create(MinecraftServer server, TradeData data, ServerPlayer player, Realm realm, String[] w) {
      Trader.Kind kind = w.length > 2 ? Trader.Kind.byId(w[2]) : null;
      String name = rest(w, 3);
      if (kind == null) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      } else if (name.length() < 2 || name.length() > 24) {
         return Realms.Result.no(Msg.bad("err.name_length", 2, 24));
      } else if (data.of(realm.id()).size() >= PrestolConfig.MAX_TRADERS.get()) {
         return Realms.Result.no(Msg.bad("err.too_many_traders", PrestolConfig.MAX_TRADERS.get()));
      } else {
         IColony home = Settlements.byKey(realm.capital());
         if (home == null) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         } else if (kind == Trader.Kind.CARAVAN && !warehouse(home)) {
            return Realms.Result.no(Msg.bad("err.need_warehouse", home.getName()));
         } else if (kind == Trader.Kind.SHIP && !port(home)) {
            return Realms.Result.no(Msg.bad("err.need_port", home.getName()));
         } else {
            long cost = kind == Trader.Kind.SHIP ? PrestolConfig.SHIP_COST.get() : PrestolConfig.CARAVAN_COST.get() + PrestolConfig.PACK_ANIMAL_COST.get();
            EconomyData eco = EconomyData.get(server);
            if (!eco.take(realm.id(), cost)) {
               return Realms.Result.no(Msg.bad("err.treasury_short", Coins.fmt(eco.balance(realm.id()))));
            } else {
               Trader t = new Trader(UUID.randomUUID(), name, realm.id(), kind);
               t.route.add(new Stop(realm.capital()));
               t.dim = dim(home);
               t.pos = Vec3.atBottomCenterOf(anchor(home, kind));
               t.status("idle");
               data.traders.put(t.id, t);
               eco.log(realm.id(), Realms.day(Realms.now(server)), "trader", -cost, name);
               return Realms.Result.yes(Msg.good("ok.trader_new", Msg.t("trade.kind." + kind.id()), name, Coins.fmt(cost)));
            }
         }
      }
   }

   private static Realms.Result edit(MinecraftServer server, TradeData data, ServerPlayer player, Realm realm, Trader t, String[] w) {
      EconomyData eco = EconomyData.get(server);
      switch (w[1]) {
         case "run": {
            boolean on = w.length > 3 && w[3].equals("on");
            if (on && t.route.size() < 2) {
               return Realms.Result.no(Msg.bad("err.route_short"));
            }

            t.running = on;
            if (!on) {
               t.status("stopped");
            }

            return Realms.Result.yes(Msg.good(on ? "ok.trader_go" : "ok.trader_stop", t.name));
         }
         case "rename": {
            String name = rest(w, 3);
            if (name.length() < 2 || name.length() > 24) {
               return Realms.Result.no(Msg.bad("err.name_length", 2, 24));
            }

            t.name = name;
            return Realms.Result.yes(Msg.good("ok.trader_renamed", name));
         }
         case "animals": {
            int n = num(w, 3);
            if (t.kind != Trader.Kind.CARAVAN || n < 1 || n > Trader.MAX_ANIMALS) {
               return Realms.Result.no(Msg.bad("err.unknown"));
            } else if (n < t.animals && t.cargo.used() > n * Trader.SLOTS_PER_ANIMAL) {
               return Realms.Result.no(Msg.bad("err.cargo_too_big"));
            } else {
               long cost = n > t.animals ? (long)(n - t.animals) * PrestolConfig.PACK_ANIMAL_COST.get() : 0L;
               if (!eco.take(realm.id(), cost)) {
                  return Realms.Result.no(Msg.bad("err.treasury_short", Coins.fmt(eco.balance(realm.id()))));
               }

               if (cost > 0L) {
                  eco.log(realm.id(), Realms.day(Realms.now(server)), "mules", -cost, t.name);
               }

               t.animals = n;
               t.cargo.size = t.capacity();
               TradeWorld.refresh(server, t);
               return Realms.Result.yes(Msg.good("ok.animals", t.name, n));
            }
         }
         case "guards": {
            int n = num(w, 3);
            if (n < 0 || n > Trader.MAX_GUARDS) {
               return Realms.Result.no(Msg.bad("err.unknown"));
            }

            t.guards = n;
            return Realms.Result.yes(Msg.good("ok.guards", t.name, n));
         }
         case "dismiss": {
            TradeWorld.despawn(server, t, true);
            data.traders.remove(t.id);
            IColony home = Settlements.byKey(realm.capital());
            if (home != null) {
               List<IItemHandler> stores = Supplies.stores(home, null);

               for (ItemStack s : t.cargo.stacks()) {
                  ItemStack left = Cargo.insert(stores, s);
                  if (!left.isEmpty()) {
                     player.getInventory().placeItemBackInInventory(left);
                  }
               }
            }

            return Realms.Result.yes(Msg.good("ok.trader_gone", t.name));
         }
         case "stop":
            return stop(server, data, realm, t, w);
         case "order":
            return orderEdit(player, t, w);
         case "wp": {
            int i = num(w, 3);
            if (i < 0 || i >= t.route.size()) {
               return Realms.Result.no(Msg.bad("err.unknown"));
            }

            Stop s = t.route.get(i);
            if (w.length > 4 && w[4].equals("clear")) {
               s.waypoints.clear();
               return Realms.Result.yes(Msg.good("ok.wp_clear"));
            } else if (s.waypoints.size() >= Stop.MAX_WAYPOINTS) {
               return Realms.Result.no(Msg.bad("err.too_many_wp", Stop.MAX_WAYPOINTS));
            } else if (!player.level().dimension().location().toString().equals(t.dim)) {
               return Realms.Result.no(Msg.bad("err.other_world"));
            } else {
               s.waypoints.add(player.blockPosition());
               return Realms.Result.yes(Msg.good("ok.wp_add", s.waypoints.size()));
            }
         }
         default:
            return Realms.Result.no(Msg.bad("err.unknown"));
      }
   }

   private static Realms.Result stop(MinecraftServer server, TradeData data, Realm realm, Trader t, String[] w) {
      String what = w.length > 3 ? w[3] : "";
      if (what.equals("add")) {
         String key = w.length > 4 ? w[4] : "";
         IColony c = Settlements.byKey(key);
         if (c == null) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         } else if (t.route.size() >= Trader.MAX_STOPS) {
            return Realms.Result.no(Msg.bad("err.too_many_stops", Trader.MAX_STOPS));
         } else if (!dim(c).equals(t.dim)) {
            return Realms.Result.no(Msg.bad("err.other_world"));
         } else if (t.kind == Trader.Kind.SHIP && !port(c)) {
            return Realms.Result.no(Msg.bad("err.need_port", c.getName()));
         } else {
            t.route.add(new Stop(key));
            return Realms.Result.yes(Msg.good("ok.stop_add", c.getName()));
         }
      } else {
         int i = num(w, 4);
         if (i < 0 || i >= t.route.size()) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         }

         switch (what) {
            case "del":
               if (t.route.size() <= 1) {
                  return Realms.Result.no(Msg.bad("err.route_short"));
               }

               t.route.remove(i);
               if (t.route.size() < 2) {
                  t.running = false;
                  t.status("stopped");
               }

               t.target = Math.min(t.target, t.route.size() - 1);
               t.wp = 0;
               return Realms.Result.yes(Msg.good("ok.stop_del"));
            case "up":
            case "down":
               int j = what.equals("up") ? i - 1 : i + 1;
               if (j >= 0 && j < t.route.size()) {
                  Stop a = t.route.get(i);
                  t.route.set(i, t.route.get(j));
                  t.route.set(j, a);
                  t.wp = 0;
               }

               return Realms.Result.yes(null);
            default:
               return Realms.Result.no(Msg.bad("err.unknown"));
         }
      }
   }

   private static Realms.Result orderEdit(ServerPlayer player, Trader t, String[] w) {
      int si = num(w, 3);
      if (si < 0 || si >= t.route.size()) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      }

      Stop s = t.route.get(si);
      String what = w.length > 4 ? w[4] : "";
      if (what.equals("del")) {
         int oi = num(w, 5);
         if (oi >= 0 && oi < s.orders.size()) {
            s.orders.remove(oi);
         }

         return Realms.Result.yes(null);
      } else if (what.equals("add")) {
         TradeOrder.Kind kind = w.length > 5 ? TradeOrder.Kind.byId(w[5]) : null;
         int amount = num(w, 6);
         int price = Math.max(0, num(w, 7));
         ItemStack hand = player.getMainHandItem();
         if (kind == null || amount <= 0) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         } else if (hand.isEmpty()) {
            return Realms.Result.no(Msg.bad("err.hold_item"));
         } else if (s.orders.size() >= Stop.MAX_ORDERS) {
            return Realms.Result.no(Msg.bad("err.too_many_orders", Stop.MAX_ORDERS));
         } else {
            s.orders.add(new TradeOrder(kind, Cargo.id(hand.getItem()), amount, price));
            return Realms.Result.yes(Msg.good("ok.order_add", itemName(hand.getItem())));
         }
      } else {
         return Realms.Result.no(Msg.bad("err.unknown"));
      }
   }

   private static Realms.Result market(MinecraftServer server, ServerPlayer player, String[] w) {
      IColony colony = w.length > 2 ? Settlements.byKey(w[2]) : null;
      if (colony == null) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      } else {
         Realm realm = RealmData.get(server).byMember(w[2]);
         if (!Settlements.isOwner(player, colony) && !Realms.isRuler(realm, player)) {
            return Realms.Result.no(Msg.bad("err.not_your_settlement"));
         } else if (realm == null) {
            return Realms.Result.no(Msg.bad("err.market_needs_realm"));
         } else {
            TradeData data = TradeData.get(server);
            List<Offer> offers = data.marketForEdit(w[2]);
            String what = w.length > 3 ? w[3] : "";
            if (what.equals("del")) {
               int i = num(w, 4);
               if (i >= 0 && i < offers.size()) {
                  offers.remove(i);
               }

               return Realms.Result.yes(null);
            } else if (!what.equals("sell") && !what.equals("buy")) {
               return Realms.Result.no(Msg.bad("err.unknown"));
            } else {
               int price = num(w, 4);
               int amount = num(w, 5);
               ItemStack hand = player.getMainHandItem();
               if (price < 0 || amount < 0) {
                  return Realms.Result.no(Msg.bad("err.unknown"));
               } else if (hand.isEmpty()) {
                  return Realms.Result.no(Msg.bad("err.hold_item"));
               } else {
                  boolean sell = what.equals("sell");
                  String item = Cargo.id(hand.getItem());
                  offers.removeIf(o -> o.sell() == sell && o.item().equals(item));
                  if (offers.size() >= TradeData.MAX_OFFERS) {
                     return Realms.Result.no(Msg.bad("err.too_many_offers", TradeData.MAX_OFFERS));
                  } else {
                     offers.add(new Offer(item, sell, price, amount));
                     return Realms.Result.yes(Msg.good(sell ? "ok.offer_sell" : "ok.offer_buy", itemName(hand.getItem()), price));
                  }
               }
            }
         }
      }
   }

   // ------------------------------------------------------------------ book page

   public static CompoundTag describe(MinecraftServer server, ServerPlayer player, Realm realm) {
      CompoundTag t = new CompoundTag();
      TradeData data = TradeData.get(server);
      RealmData realms = RealmData.get(server);
      EconomyData eco = EconomyData.get(server);
      t.putBoolean("ruler", Realms.isRuler(realm, player));
      t.putInt("max", PrestolConfig.MAX_TRADERS.get());
      t.putInt("caravanCost", PrestolConfig.CARAVAN_COST.get() + PrestolConfig.PACK_ANIMAL_COST.get());
      t.putInt("animalCost", PrestolConfig.PACK_ANIMAL_COST.get());
      t.putInt("shipCost", PrestolConfig.SHIP_COST.get());
      t.putInt("escortCost", PrestolConfig.ESCORT_COST.get());
      long now = Realms.now(server);
      ListTag traders = new ListTag();

      for (Trader tr : data.of(realm.id())) {
         CompoundTag x = new CompoundTag();
         x.putString("id", tr.id.toString());
         x.putString("name", tr.name);
         x.putString("kind", tr.kind.id());
         x.putInt("animals", tr.animals);
         x.putInt("guards", tr.guards);
         x.putBoolean("running", tr.running);
         x.putBoolean("near", tr.physical());
         x.putInt("x", (int)tr.pos.x);
         x.putInt("z", (int)tr.pos.z);
         x.putString("status", tr.status);
         ListTag sa = new ListTag();

         for (String a : tr.statusArgs) {
            sa.add(StringTag.valueOf(a));
         }

         x.put("statusArgs", sa);
         x.putInt("target", tr.target);
         x.putInt("slots", tr.cargo.size());
         x.putInt("used", tr.cargo.used());
         ListTag cargo = new ListTag();

         for (Map.Entry<String, Integer> e : tr.cargo.summary().entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("item", e.getKey());
            c.putInt("n", e.getValue());
            cargo.add(c);
         }

         x.put("cargo", cargo);
         ListTag route = new ListTag();
         BlockPos prev = null;

         for (Stop s : tr.route) {
            IColony c = Settlements.byKey(s.settlement);
            CompoundTag r = new CompoundTag();
            r.putString("key", s.settlement);
            r.putString("name", c == null ? "?" : c.getName());
            Realm owner = realms.byMember(s.settlement);
            r.putString("realm", owner == null ? "" : owner.name());
            r.putBoolean("own", owner != null && owner.id().equals(realm.id()));
            r.putInt("wps", s.waypoints.size());
            if (c != null) {
               BlockPos a = anchor(c, tr.kind);
               if (prev != null) {
                  double d = 0.0;
                  BlockPos p = prev;

                  for (BlockPos wp : s.waypoints) {
                     d += Math.sqrt(p.distSqr(wp));
                     p = wp;
                  }

                  d += Math.sqrt(p.distSqr(a));
                  r.putInt("dist", (int)d);
               }

               prev = a;
            }

            ListTag orders = new ListTag();

            for (TradeOrder o : s.orders) {
               orders.add(o.save());
            }

            r.put("orders", orders);
            route.add(r);
         }

         x.put("route", route);
         ListTag j = new ListTag();

         for (String line : tr.journal) {
            j.add(StringTag.valueOf(line));
         }

         x.put("journal", j);
         traders.add(x);
      }

      t.put("traders", traders);
      ListTag places = new ListTag();

      for (IColony c : Settlements.all()) {
         String key = Settlements.key(c);
         Realm owner = realms.byMember(key);
         boolean own = owner != null && owner.id().equals(realm.id());
         List<Offer> offers = data.market(key);
         if (own || owner != null && !offers.isEmpty()) {
            CompoundTag p = new CompoundTag();
            p.putString("key", key);
            p.putString("name", c.getName());
            p.putBoolean("own", own);
            p.putString("realm", owner.name());
            p.putBoolean("war", !own && realms.war(realm.id(), owner.id()) != null);
            p.putInt("duty", duty(eco, realm, owner));
            p.putBoolean("port", port(c));
            if (!offers.isEmpty()) {
               List<IItemHandler> stores = Supplies.stores(c, null);
               ListTag l = new ListTag();

               for (Offer o : offers) {
                  CompoundTag ot = o.save();
                  ot.putInt("stock", Cargo.stock(stores, Cargo.item(o.item())));
                  l.add(ot);
               }

               p.put("offers", l);
            }

            places.add(p);
         }
      }

      t.put("places", places);
      ListTag markets = new ListTag();

      for (IColony c : Settlements.all()) {
         String key = Settlements.key(c);
         Realm owner = realms.byMember(key);
         if (owner != null && owner.id().equals(realm.id()) && (Settlements.isOwner(player, c) || Realms.isRuler(realm, player))) {
            CompoundTag m = new CompoundTag();
            m.putString("key", key);
            m.putString("name", c.getName());
            List<IItemHandler> stores = Supplies.stores(c, null);
            ListTag l = new ListTag();

            for (Offer o : data.market(key)) {
               CompoundTag ot = o.save();
               ot.putInt("stock", Cargo.stock(stores, Cargo.item(o.item())));
               l.add(ot);
            }

            m.put("offers", l);
            markets.add(m);
         }
      }

      t.put("markets", markets);
      t.putLong("now", now);
      return t;
   }
}
