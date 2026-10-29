package com.nick.prestol.war;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Peace on terms. A ruler at war offers peace with a tribute (so many coins a day for so many days, paid by either
 * side) and the cession of a settlement (his own or the enemy's); the other ruler accepts those terms or answers with
 * his own. A tribute is paid every game day from treasury to treasury; two days unpaid in a row break the treaty and
 * the truce with it.
 */
public final class Treaties {
   public static final int MAX_DAYS = 30;
   public static final long MAX_PER_DAY = 10000L;
   /** Days unpaid in a row that break a treaty. */
   private static final int BREAK_AFTER = 2;
   private static final Map<String, Treaties.Terms> OFFERS = new HashMap<>();
   private static final List<Treaties.Tribute> TRIBUTES = new ArrayList<>();

   private Treaties() {
   }

   private static String pair(UUID from, UUID to) {
      return from + "|" + to;
   }

   /** {@code treaty <realm> <coins a day> <days> <us|them> <settlement|->}: offers peace on these terms. */
   public static Realms.Result offer(MinecraftServer server, ServerPlayer player, String[] w) {
      RealmData data = RealmData.get(server);
      Realm me = data.byRuler(player.getUUID());
      Realm them = w.length > 1 ? data.byId(uuid(w[1])) : null;
      if (me == null) {
         return Realms.Result.no(Msg.bad("err.not_ruler"));
      } else if (them == null || w.length < 6) {
         return Realms.Result.no(Msg.bad("err.unknown"));
      } else if (data.war(me.id(), them.id()) == null) {
         return Realms.Result.no(Msg.bad("war.none", them.name()));
      } else {
         long perDay = num(w[2]);
         int days = (int)num(w[3]);
         boolean theyPay = w[4].equals("them");
         String cede = w[5].equals("-") ? "" : w[5];
         if (perDay < 0L || perDay > MAX_PER_DAY || perDay > 0L && (days < 1 || days > MAX_DAYS)) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         } else if (perDay > 0L && !Treasury.on()) {
            return Realms.Result.no(Msg.bad("err.economy_off"));
         } else {
            boolean theyCede = !cede.isEmpty() && them.members().contains(cede);
            if (!cede.isEmpty()) {
               Component why = cessionRefused(data, theyCede ? them : me, cede);
               if (why != null) {
                  return Realms.Result.no(why);
               }
            }

            Treaties.Terms terms = new Treaties.Terms(perDay, perDay > 0L ? days : 0, theyPay, cede, theyCede);
            OFFERS.put(pair(me.id(), them.id()), terms);
            data.setOffer("peace", me.id(), them.id(), true);
            Realms.tell(server, them, Msg.info("war.treaty_offered", me.name(), describe(server, terms, false, me.name())));
            return Realms.Result.yes(Msg.good("ok.treaty_offered", them.name()));
         }
      }
   }

   /** Why the settlement {@code key} cannot be handed over by {@code from}, or null. */
   private static Component cessionRefused(RealmData data, Realm from, String key) {
      IColony c = Settlements.byKey(key);
      if (c == null || !from.members().contains(key)) {
         return Msg.bad("err.no_settlement");
      } else {
         return from.members().size() < 2 ? Msg.bad("war.cede_last", c.getName(), from.name()) : null;
      }
   }

   /** A plain peace offer carries no terms. */
   static void plain(UUID from, UUID to) {
      OFFERS.remove(pair(from, to));
   }

   /** Peace is made on the terms {@code offerer} proposed and {@code acceptor} accepted. */
   static void execute(MinecraftServer server, Realm offerer, Realm acceptor) {
      Treaties.Terms t = OFFERS.remove(pair(offerer.id(), acceptor.id()));
      OFFERS.remove(pair(acceptor.id(), offerer.id()));
      if (t != null && offerer != null && acceptor != null) {
         RealmData data = RealmData.get(server);
         if (t.perDay > 0L && t.days > 0) {
            Realm payer = t.theyPay ? acceptor : offerer;
            Realm payee = t.theyPay ? offerer : acceptor;
            TRIBUTES.add(new Treaties.Tribute(payer.id(), payee.id(), t.perDay, t.days));
            Realms.tellAll(server, Msg.war("war.tribute_set", payer.name(), payee.name(), Coins.fmt(t.perDay), t.days));
            Realms.chronicle(server, "tribute", List.of(payer.name(), payee.name()), payer, payee);
         }

         if (!t.cede.isEmpty()) {
            Realm from = t.theyCede ? acceptor : offerer;
            Realm to = t.theyCede ? offerer : acceptor;
            IColony c = Settlements.byKey(t.cede);
            if (c != null && cessionRefused(data, from, t.cede) == null) {
               Conquest.cede(server, c, to);
            }
         }
      }
   }

   /** A new game day: tributes are paid, run out or break. */
   public static void payDay(MinecraftServer server, long day) {
      if (!TRIBUTES.isEmpty()) {
         RealmData data = RealmData.get(server);
         EconomyData eco = EconomyData.get(server);
         Iterator<Treaties.Tribute> it = TRIBUTES.iterator();

         while (it.hasNext()) {
            Treaties.Tribute t = it.next();
            Realm payer = data.byId(t.payer);
            Realm payee = data.byId(t.payee);
            if (payer == null || payee == null || data.war(t.payer, t.payee) != null) {
               // a realm fell, or the two are at war again: the treaty is void
               it.remove();
            } else {
               long paid = Math.min(t.perDay, eco.balance(t.payer));
               if (paid > 0L) {
                  eco.take(t.payer, paid);
                  eco.give(t.payee, paid);
                  eco.log(t.payer, day, "tribute_paid", -paid, payee.name());
                  eco.log(t.payee, day, "tribute_got", paid, payer.name());
               }

               t.daysLeft--;
               if (paid < t.perDay) {
                  t.missed++;
                  Realms.tell(server, payer, Msg.bad("war.tribute_short", payee.name(), Coins.fmt(t.perDay - paid)));
                  Realms.tell(server, payee, Msg.bad("war.tribute_short_them", payer.name(), Coins.fmt(t.perDay - paid)));
               } else {
                  t.missed = 0;
               }

               if (t.missed >= BREAK_AFTER) {
                  it.remove();
                  data.setTruce(t.payer, t.payee, -1L);
                  Realms.tellAll(server, Msg.war("war.tribute_broken", payer.name(), payee.name()));
                  Realms.chronicle(server, "tribute_broken", List.of(payer.name(), payee.name()), payer, payee);
               } else if (t.daysLeft <= 0) {
                  it.remove();
                  Realms.tell(server, payer, Msg.info("war.tribute_done", payee.name()));
                  Realms.tell(server, payee, Msg.info("war.tribute_done", payer.name()));
               }
            }
         }
      }
   }

   /** The war between the two is over or void: offers between them are gone. */
   static void forgetOffers(UUID a, UUID b) {
      OFFERS.remove(pair(a, b));
      OFFERS.remove(pair(b, a));
   }

   // ---- book

   /** The terms as {@code reader} sees them: "we" are the reader's realm. */
   static MutableComponent describe(MinecraftServer server, Treaties.Terms t, boolean ours, String offerer) {
      // ours: the reader made the offer, so "they pay" means the other side pays the reader
      boolean readerPays = ours != t.theyPay;
      boolean readerCedes = ours != t.theyCede;
      MutableComponent out = Component.empty();
      if (t.perDay > 0L) {
         out.append(Msg.t(readerPays ? "treaty.we_pay" : "treaty.they_pay", Coins.fmt(t.perDay), t.days));
      }

      if (!t.cede.isEmpty()) {
         IColony c = Settlements.byKey(t.cede);
         if (t.perDay > 0L) {
            out.append("; ");
         }

         out.append(Msg.t(readerCedes ? "treaty.we_cede" : "treaty.they_cede", c == null ? "?" : c.getName()));
      }

      if (t.perDay <= 0L && t.cede.isEmpty()) {
         out.append(Msg.t("treaty.plain"));
      }

      return out;
   }

   /** What the war page shows about the peace between {@code mine} and {@code them}. */
   public static void describeFor(MinecraftServer server, Realm mine, Realm them, CompoundTag out) {
      Treaties.Terms theirs = OFFERS.get(pair(them.id(), mine.id()));
      if (theirs != null) {
         out.putString("theirTerms", Component.Serializer.toJson(describe(server, theirs, false, them.name())));
      }

      Treaties.Terms ours = OFFERS.get(pair(mine.id(), them.id()));
      if (ours != null) {
         out.putString("ourTerms", Component.Serializer.toJson(describe(server, ours, true, mine.name())));
      }

      ListTag cede = new ListTag();
      addMembers(them, false, cede);
      addMembers(mine, true, cede);
      out.put("cede", cede);
   }

   private static void addMembers(Realm r, boolean ours, ListTag out) {
      if (r.members().size() >= 2) {
         for (String key : r.members()) {
            IColony c = Settlements.byKey(key);
            if (c != null) {
               CompoundTag t = new CompoundTag();
               t.putString("key", key);
               t.putString("name", c.getName());
               t.putBoolean("ours", ours);
               out.add(t);
            }
         }
      }
   }

   /** The tributes a realm pays or receives, for its treasury page. */
   public static ListTag tributesOf(MinecraftServer server, Realm realm) {
      ListTag out = new ListTag();
      RealmData data = RealmData.get(server);

      for (Treaties.Tribute t : TRIBUTES) {
         boolean pays = t.payer.equals(realm.id());
         if (pays || t.payee.equals(realm.id())) {
            Realm other = data.byId(pays ? t.payee : t.payer);
            CompoundTag x = new CompoundTag();
            x.putBoolean("pays", pays);
            x.putString("realm", other == null ? "?" : other.name());
            x.putLong("perDay", t.perDay);
            x.putInt("days", t.daysLeft);
            out.add(x);
         }
      }

      return out;
   }

   // ---- persistence (with the sieges, in WarData)

   static CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      CompoundTag offers = new CompoundTag();
      OFFERS.forEach((k, t) -> offers.put(k, t.save()));
      tag.put("offers", offers);
      ListTag tributes = new ListTag();

      for (Treaties.Tribute t : TRIBUTES) {
         tributes.add(t.save());
      }

      tag.put("tributes", tributes);
      return tag;
   }

   static void restore(CompoundTag tag) {
      clear();
      CompoundTag offers = tag.getCompound("offers");

      for (String k : offers.getAllKeys()) {
         OFFERS.put(k, Treaties.Terms.load(offers.getCompound(k)));
      }

      for (Tag x : tag.getList("tributes", 10)) {
         Treaties.Tribute t = Treaties.Tribute.load((CompoundTag)x);
         if (t != null) {
            TRIBUTES.add(t);
         }
      }
   }

   public static void clear() {
      OFFERS.clear();
      TRIBUTES.clear();
   }

   private static UUID uuid(String s) {
      try {
         return UUID.fromString(s);
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   private static long num(String s) {
      try {
         return Long.parseLong(s);
      } catch (NumberFormatException var2) {
         return -1L;
      }
   }

   /** Terms from the offerer's side: {@code theyPay} and {@code theyCede} are about the realm the offer goes to. */
   record Terms(long perDay, int days, boolean theyPay, String cede, boolean theyCede) {
      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putLong("perDay", this.perDay);
         t.putInt("days", this.days);
         t.putBoolean("theyPay", this.theyPay);
         t.putString("cede", this.cede);
         t.putBoolean("theyCede", this.theyCede);
         return t;
      }

      static Treaties.Terms load(CompoundTag t) {
         return new Treaties.Terms(t.getLong("perDay"), t.getInt("days"), t.getBoolean("theyPay"), t.getString("cede"), t.getBoolean("theyCede"));
      }
   }

   static final class Tribute {
      final UUID payer;
      final UUID payee;
      final long perDay;
      int daysLeft;
      int missed;

      Tribute(UUID payer, UUID payee, long perDay, int days) {
         this.payer = payer;
         this.payee = payee;
         this.perDay = perDay;
         this.daysLeft = days;
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putUUID("payer", this.payer);
         t.putUUID("payee", this.payee);
         t.putLong("perDay", this.perDay);
         t.putInt("days", this.daysLeft);
         t.putInt("missed", this.missed);
         return t;
      }

      static Treaties.Tribute load(CompoundTag t) {
         if (t.hasUUID("payer") && t.hasUUID("payee")) {
            Treaties.Tribute x = new Treaties.Tribute(t.getUUID("payer"), t.getUUID("payee"), t.getLong("perDay"), t.getInt("days"));
            x.missed = t.getInt("missed");
            return x;
         } else {
            return null;
         }
      }
   }
}
