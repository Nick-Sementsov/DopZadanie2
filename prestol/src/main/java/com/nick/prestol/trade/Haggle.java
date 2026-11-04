package com.nick.prestol.trade;

import com.nick.prestol.PrestolConfig;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.war.Wars;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Haggling with a merchant on the road. He sells what he carries a little dearer than the markets, and buys for his
 * realm what its home settlement lacks; the gold goes to and comes from the realm's treasury, the goods to and from
 * his packs. He has nothing to sell to the enemies of his realm or to its outlaws.
 */
public final class Haggle {
   private static final int MAX_SELLS = 9;
   private static final int MAX_BUYS = 6;

   private Haggle() {
   }

   /** One line of his offers: {@code lot} pieces for {@code coins}, sold to the player or bought from him, up to {@code most} pieces. */
   record Deal(Item item, int lot, long coins, boolean sell, int most) {
   }

   /** Why he will not trade with {@code player}, as a message key, or null. */
   static String refusal(MinecraftServer server, Trader t, ServerPlayer player) {
      if (t.stranger) {
         return null;
      }

      RealmData realms = RealmData.get(server);
      Realm his = realms.byId(t.realm);
      Realm theirs = Realms.realmOf(server, player);
      if (his == null) {
         return "trade.haggle_no";
      } else if (theirs != null && theirs != his && Wars.atWar(realms, his, theirs)) {
         return "trade.haggle_enemy";
      } else {
         return Court.outlawed(server, his, player.getUUID()) ? "trade.haggle_outlaw" : null;
      }
   }

   /** What he offers just now: the deals, and the same as vanilla offers, line for line. */
   static List<Deal> deals(MinecraftServer server, Trader t) {
      List<Deal> out = new ArrayList<>();
      double markup = 1.0 + PrestolConfig.MERCHANT_MARKUP.get() / 100.0;

      for (Map.Entry<String, Integer> e : t.cargo.summary().entrySet()) {
         if (out.size() >= MAX_SELLS) {
            break;
         }

         Item item = Cargo.item(e.getKey());
         double unit = Prices.of(item) * markup;
         if (unit > 0.0) {
            int lot = lot(item, unit);
            if (e.getValue() >= lot) {
               out.add(new Deal(item, lot, Math.max(1L, Math.round(lot * unit)), true, e.getValue()));
            }
         }
      }

      if (t.stranger) {
         Strangers.wants(t, out);
         return out;
      }

      Markets.Market home = t.home.isEmpty() ? null : Markets.of(server, t.home);
      if (home != null && home.hunger() > 0 && home.needs().isEmpty()) {
         // a hungry home: he buys bread for it
         Item bread = Items.BREAD;
         double unit = Prices.of(bread);
         int lot = lot(bread, unit);
         out.add(new Deal(bread, lot, Math.max(1L, (long)Math.floor(lot * unit)), false, home.hunger()));
      }

      if (home != null) {
         home.needs()
            .entrySet()
            .stream()
            .sorted(Comparator.comparingDouble((Map.Entry<Item, Integer> e) -> -e.getValue() * Prices.of(e.getKey())))
            .limit(MAX_BUYS)
            .forEach(e -> {
               double unit = Prices.of(e.getKey());
               if (unit > 0.0) {
                  int lot = lot(e.getKey(), unit);
                  long coins = (long)Math.floor(lot * unit);
                  if (coins >= 1L && e.getValue() >= lot) {
                     out.add(new Deal(e.getKey(), lot, coins, false, e.getValue()));
                  }
               }
            });
      }

      return out;
   }

   /** Cheap goods go by the handful: as many as make at least a coin. */
   private static int lot(Item item, double unit) {
      return Math.max(1, Math.min(item.getMaxStackSize(), (int)Math.ceil(1.0 / unit - 1.0E-9)));
   }

   static MerchantOffers offers(MinecraftServer server, Trader t, List<Deal> deals) {
      MerchantOffers out = new MerchantOffers();
      long treasury = t.stranger ? t.purse : EconomyData.get(server).balance(t.realm);
      Map<String, Integer> carried = t.cargo.summary();

      for (Deal d : deals) {
         List<ItemStack> gold = gold(d.coins());
         int uses;
         if (d.sell()) {
            uses = Math.min(carried.getOrDefault(Cargo.id(d.item()), 0), d.most()) / d.lot();
            out.add(new MerchantOffer(gold.get(0), gold.size() > 1 ? gold.get(1) : ItemStack.EMPTY, new ItemStack(d.item(), d.lot()), 0, Math.max(0, uses), 0, 0.0F));
         } else {
            uses = (int)Math.min((long)(Math.min(t.cargo.room(d.item()), d.most()) / d.lot()), treasury / d.coins());
            out.add(new MerchantOffer(new ItemStack(d.item(), d.lot()), ItemStack.EMPTY, gold.get(0), 0, Math.max(0, uses), 0, 0.0F));
         }
      }

      return out;
   }

   /** Coins as one or two stacks of gold: nuggets, ingots and nuggets, or blocks and ingots (rounded up). */
   static List<ItemStack> gold(long coins) {
      List<ItemStack> out = new ArrayList<>();
      if (coins <= 64L) {
         out.add(new ItemStack(Items.GOLD_NUGGET, (int)Math.max(1L, coins)));
      } else if (coins <= 64L * Coins.INGOT) {
         out.add(new ItemStack(Items.GOLD_INGOT, (int)(coins / Coins.INGOT)));
         if (coins % Coins.INGOT > 0L) {
            out.add(new ItemStack(Items.GOLD_NUGGET, (int)(coins % Coins.INGOT)));
         }
      } else {
         out.add(new ItemStack(Items.GOLD_BLOCK, (int)Math.min(64L, coins / Coins.BLOCK)));
         long ingots = (coins % Coins.BLOCK + Coins.INGOT - 1) / Coins.INGOT;
         if (ingots > 0L) {
            out.add(new ItemStack(Items.GOLD_INGOT, (int)ingots));
         }
      }

      return out;
   }

   /** A deal was struck in the trading window: the packs and the treasury follow. */
   static void done(MinecraftServer server, Trader t, Deal d, String who) {
      EconomyData eco = EconomyData.get(server);
      long day = Realms.day(Realms.now(server));
      long coins = d.sell() ? coinsOf(gold(d.coins())) : d.coins();
      if (t.stranger) {
         // a stranger's purse is his own
         if (d.sell()) {
            t.cargo.take(d.item(), d.lot());
            t.purse += coins;
         } else {
            t.cargo.add(d.item(), d.lot());
            t.purse = Math.max(0L, t.purse - coins);
         }

         TradeWorld.pushCargo(server, t);
         return;
      }

      if (d.sell()) {
         t.cargo.take(d.item(), d.lot());
         eco.give(t.realm, coins);
         eco.log(t.realm, day, "haggle_sold", coins, t.name, who, d.item().getDescriptionId(), String.valueOf(d.lot()));
         t.earned += coins;
         t.tripEarned += coins;
      } else {
         t.cargo.add(d.item(), d.lot());
         if (!eco.take(t.realm, coins)) {
            eco.take(t.realm, eco.balance(t.realm));
         }

         eco.log(t.realm, day, "haggle_bought", -coins, t.name, who, d.item().getDescriptionId(), String.valueOf(d.lot()));
         t.spent += coins;
         t.tripSpent += coins;
      }

      TradeWorld.pushCargo(server, t);
   }

   /** What the gold stacks of a price are really worth (blocks and ingots round a price up). */
   private static long coinsOf(List<ItemStack> gold) {
      long n = 0L;

      for (ItemStack s : gold) {
         n += (long)Coins.value(s) * s.getCount();
      }

      return n;
   }
}
