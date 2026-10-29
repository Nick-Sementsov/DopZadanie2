package com.nick.prestol.economy;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.research.util.ResearchConstants;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.army.ArmyData;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Sieges;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The treasury of a realm. Every game day the settlements pay tax (coins per citizen, by the realm's tax rate)
 * and the treasury pays the garrison soldiers. Subjects bring gold in; the ruler takes it out, sets the tax
 * and the duty foreign traders pay. Low taxes make people happier, high ones gloomier.
 */
public final class Treasury {
   public static final int[] DUTIES = {0, 5, 10, 25};

   private Treasury() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.ECONOMY.get();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String[] w) {
      if (!on()) {
         return Realms.Result.no(Msg.bad("err.economy_off"));
      } else {
         Realm realm = Realms.realmOf(server, player);
         if (realm == null) {
            return Realms.Result.no(Msg.bad("err.need_realm"));
         } else {
            EconomyData eco = EconomyData.get(server);
            long day = Realms.day(Realms.now(server));
            String name = player.getGameProfile().getName();
            switch (w.length > 1 ? w[1] : "") {
               case "deposit": {
                  long n = Coins.takeAll(player);
                  if (n <= 0L) {
                     return Realms.Result.no(Msg.bad("err.no_gold"));
                  }

                  eco.give(realm.id(), n);
                  eco.log(realm.id(), day, "deposit", n, name);
                  return Realms.Result.yes(Msg.good("ok.deposit", Coins.fmt(n), Coins.fmt(eco.balance(realm.id()))));
               }
               case "withdraw": {
                  if (!Realms.isRuler(realm, player)) {
                     return Realms.Result.no(Msg.bad("err.not_ruler"));
                  }

                  long n = parse(w, 2);
                  if (n <= 0L) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else if (!eco.take(realm.id(), n)) {
                     return Realms.Result.no(Msg.bad("err.treasury_short", Coins.fmt(eco.balance(realm.id()))));
                  }

                  Coins.give(player, n);
                  eco.log(realm.id(), day, "withdraw", -n, name);
                  return Realms.Result.yes(Msg.good("ok.withdraw", Coins.fmt(n), Coins.fmt(eco.balance(realm.id()))));
               }
               case "tax": {
                  Tax t = w.length > 2 ? Tax.byId(w[2]) : null;
                  if (t == null) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  } else if (!Realms.isRuler(realm, player)) {
                     return Realms.Result.no(Msg.bad("err.not_ruler"));
                  }

                  eco.tax.put(realm.id(), t);
                  eco.setDirty();
                  Realms.tell(server, realm, Msg.info("treasury.tax_set", name, Msg.t("tax." + t.id())));
                  return Realms.Result.yes(Msg.good("ok.tax", Msg.t("tax." + t.id())));
               }
               case "duty": {
                  long d = parse(w, 2);
                  if (!Realms.isRuler(realm, player)) {
                     return Realms.Result.no(Msg.bad("err.not_ruler"));
                  } else if (d < 0L || d > 50L) {
                     return Realms.Result.no(Msg.bad("err.unknown"));
                  }

                  eco.duty.put(realm.id(), (int)d);
                  eco.setDirty();
                  return Realms.Result.yes(Msg.good("ok.duty", d));
               }
               default:
                  return Realms.Result.no(Msg.bad("err.unknown"));
            }
         }
      }
   }

   private static long parse(String[] w, int i) {
      try {
         return w.length > i ? Long.parseLong(w[i]) : -1L;
      } catch (NumberFormatException var3) {
         return -1L;
      }
   }

   /** Taxes a settlement would pay today: nothing while besieged or enslaved. */
   static long taxOf(RealmData realms, String key, Tax tax) {
      IColony c = Settlements.byKey(key);
      Holding h = realms.peek(key);
      return c != null && !Sieges.isBesieged(key) && (h == null || !h.enslaved) ? (long)Settlements.stats(c).citizens() * tax.perCitizen() : 0L;
   }

   /** Runs every second; does the day's accounts once a new game day begins. */
   public static void tick(MinecraftServer server) {
      if (on()) {
         EconomyData eco = EconomyData.get(server);
         long day = Realms.day(Realms.now(server));
         if (eco.lastDay < 0L || day < eco.lastDay) {
            eco.lastDay = day;
            eco.setDirty();
         } else if (day != eco.lastDay) {
            eco.lastDay = day;
            eco.setDirty();
            RealmData realms = RealmData.get(server);
            ArmyData army = ArmyData.get(server);
            Set<UUID> alive = new HashSet<>();

            for (Realm r : realms.all()) {
               alive.add(r.id());
               Tax tax = eco.tax(r.id());
               long income = 0L;

               for (String key : r.members()) {
                  income += taxOf(realms, key, tax);
               }

               if (income > 0L) {
                  eco.give(r.id(), income);
                  eco.log(r.id(), day, "tax", income, tax.id());
               }

               int soldiers = army.soldiersOf(r.members());
               long pay = (long)soldiers * PrestolConfig.SOLDIER_PAY.get();
               long paid = pay;
               if (pay > 0L) {
                  if (eco.take(r.id(), pay)) {
                     eco.unpaid.remove(r.id());
                  } else {
                     paid = eco.balance(r.id());
                     eco.take(r.id(), paid);
                     eco.unpaid.merge(r.id(), 1, Integer::sum);
                     Realms.tell(server, r, Msg.bad("treasury.unpaid", Coins.fmt(pay - paid)));
                  }

                  eco.log(r.id(), day, paid < pay ? "pay_short" : "pay", -paid, String.valueOf(soldiers));
               }

               if (income > 0L || pay > 0L) {
                  Realms.tell(server, r, Msg.info("treasury.day", day, Coins.fmt(income), Coins.fmt(paid), Coins.fmt(eco.balance(r.id()))));
               }
            }

            for (UUID id : new HashSet<>(eco.coins.keySet())) {
               if (!alive.contains(id)) {
                  eco.forget(id);
               }
            }
         }
      }
   }

   /** Called when a realm is disbanded or falls: the ruler, if he is here, gets what was in the treasury. */
   public static void onRealmRemoved(MinecraftServer server, Realm realm) {
      if (on()) {
         EconomyData eco = EconomyData.get(server);
         long n = eco.balance(realm.id());
         ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
         if (n > 0L && ruler != null) {
            Coins.give(ruler, n);
            ruler.sendSystemMessage(Msg.info("treasury.returned", Coins.fmt(n)));
         }

         eco.forget(realm.id());
      }
   }

   /** The tax rate's effect on happiness, merged into the decree effects of every settlement of the realm. */
   public static void addEffects(MinecraftServer server, String key, Map<ResourceLocation, Double> out) {
      if (on()) {
         Realm realm = RealmData.get(server).byMember(key);
         if (realm != null) {
            double h = EconomyData.get(server).tax(realm.id()).happiness;
            if (h != 0.0) {
               out.merge(ResearchConstants.HAPPINESS, h, Double::sum);
            }
         }
      }
   }

   /** The treasury page of the realm book. */
   public static CompoundTag describe(MinecraftServer server, Realm realm, boolean ruler) {
      CompoundTag t = new CompoundTag();
      EconomyData eco = EconomyData.get(server);
      RealmData realms = RealmData.get(server);
      Tax tax = eco.tax(realm.id());
      t.putLong("coins", eco.balance(realm.id()));
      t.putString("tax", tax.id());
      t.putInt("duty", eco.duty(realm.id()));
      t.putBoolean("ruler", ruler);
      t.putInt("unpaid", eco.unpaidDays(realm.id()));
      long income = 0L;

      for (String key : realm.members()) {
         income += taxOf(realms, key, tax);
      }

      int soldiers = ArmyData.get(server).soldiersOf(realm.members());
      t.putLong("income", income);
      t.putInt("soldiers", soldiers);
      t.putLong("pay", (long)soldiers * PrestolConfig.SOLDIER_PAY.get());
      ListTag rates = new ListTag();

      for (Tax x : Tax.values()) {
         CompoundTag r = new CompoundTag();
         r.putString("id", x.id());
         long sum = 0L;

         for (String key : realm.members()) {
            sum += taxOf(realms, key, x);
         }

         r.putLong("income", sum);
         r.putInt("happiness", (int)Math.round(x.happiness * 100.0));
         rates.add(r);
      }

      t.put("rates", rates);
      ListTag book = new ListTag();

      for (EconomyData.Entry e : eco.ledger(realm.id())) {
         CompoundTag x = new CompoundTag();
         x.putLong("day", e.day());
         x.putString("key", e.key());
         x.putLong("amount", e.amount());
         ListTag args = new ListTag();

         for (String a : e.args()) {
            args.add(StringTag.valueOf(a));
         }

         x.put("args", args);
         book.add(x);
      }

      t.put("ledger", book);
      return t;
   }
}
