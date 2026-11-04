package com.nick.prestol.economy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Treasuries of the realms: coins, tax rate, duty on foreign traders and the account book. */
public final class EconomyData extends SavedData {
   private static final String NAME = "prestol_economy";
   private static final int LEDGER_SIZE = 40;
   final Map<UUID, Long> coins = new HashMap<>();
   final Map<UUID, Tax> tax = new HashMap<>();
   final Map<UUID, Integer> duty = new HashMap<>();
   final Map<UUID, Deque<EconomyData.Entry>> ledger = new HashMap<>();
   /** Game days in a row a realm could not pay its soldiers. */
   final Map<UUID, Integer> unpaid = new HashMap<>();
   long lastDay = -1L;

   public static EconomyData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(EconomyData::load, EconomyData::new, NAME);
   }

   public long balance(UUID realm) {
      return this.coins.getOrDefault(realm, 0L);
   }

   public void give(UUID realm, long n) {
      if (n != 0L) {
         this.coins.merge(realm, n, Long::sum);
         this.setDirty();
      }
   }

   /** Takes {@code n} coins if the treasury has them. */
   public boolean take(UUID realm, long n) {
      if (n <= 0L) {
         return true;
      } else if (this.balance(realm) < n) {
         return false;
      } else {
         this.give(realm, -n);
         return true;
      }
   }

   public Tax tax(UUID realm) {
      return this.tax.getOrDefault(realm, Tax.NORMAL);
   }

   public int duty(UUID realm) {
      return this.duty.getOrDefault(realm, 0);
   }

   public int unpaidDays(UUID realm) {
      return this.unpaid.getOrDefault(realm, 0);
   }

   public void log(UUID realm, long day, String key, long amount, String... args) {
      Deque<EconomyData.Entry> d = this.ledger.computeIfAbsent(realm, k -> new ArrayDeque<>());
      d.addFirst(new EconomyData.Entry(day, key, amount, List.of(args)));

      while (d.size() > LEDGER_SIZE) {
         d.removeLast();
      }

      this.setDirty();
   }

   public List<EconomyData.Entry> ledger(UUID realm) {
      return new ArrayList<>(this.ledger.getOrDefault(realm, new ArrayDeque<>()));
   }

   /** A realm that is gone takes its treasury with it. */
   public void forget(UUID realm) {
      this.coins.remove(realm);
      this.tax.remove(realm);
      this.duty.remove(realm);
      this.ledger.remove(realm);
      this.unpaid.remove(realm);
      this.setDirty();
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag realms = new ListTag();
      java.util.Set<UUID> ids = new java.util.HashSet<>(this.coins.keySet());
      ids.addAll(this.tax.keySet());
      ids.addAll(this.duty.keySet());
      ids.addAll(this.ledger.keySet());

      for (UUID id : ids) {
         CompoundTag t = new CompoundTag();
         t.putUUID("id", id);
         t.putLong("coins", this.balance(id));
         t.putString("tax", this.tax(id).id());
         t.putInt("duty", this.duty(id));
         t.putInt("unpaid", this.unpaidDays(id));
         ListTag book = new ListTag();

         for (EconomyData.Entry e : this.ledger.getOrDefault(id, new ArrayDeque<>())) {
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
         realms.add(t);
      }

      tag.put("realms", realms);
      tag.putLong("lastDay", this.lastDay);
      return tag;
   }

   public static EconomyData load(CompoundTag tag) {
      EconomyData d = new EconomyData();
      d.lastDay = tag.getLong("lastDay");

      for (Tag x : tag.getList("realms", 10)) {
         CompoundTag t = (CompoundTag)x;
         UUID id = t.getUUID("id");
         d.coins.put(id, t.getLong("coins"));
         Tax tax = Tax.byId(t.getString("tax"));
         if (tax != null) {
            d.tax.put(id, tax);
         }

         d.duty.put(id, t.getInt("duty"));
         if (t.getInt("unpaid") > 0) {
            d.unpaid.put(id, t.getInt("unpaid"));
         }

         Deque<EconomyData.Entry> book = new ArrayDeque<>();

         for (Tag y : t.getList("ledger", 10)) {
            CompoundTag e = (CompoundTag)y;
            List<String> args = new ArrayList<>();

            for (Tag a : e.getList("args", 8)) {
               args.add(a.getAsString());
            }

            book.addLast(new EconomyData.Entry(e.getLong("day"), e.getString("key"), e.getLong("amount"), args));
         }

         d.ledger.put(id, book);
      }

      return d;
   }

   public static record Entry(long day, String key, long amount, List<String> args) {
   }
}
