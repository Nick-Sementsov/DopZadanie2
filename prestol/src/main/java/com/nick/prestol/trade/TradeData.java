package com.nick.prestol.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Traders of all realms and the markets of all settlements. */
public final class TradeData extends SavedData {
   private static final String NAME = "prestol_trade";
   public static final int MAX_OFFERS = 16;
   final Map<UUID, Trader> traders = new LinkedHashMap<>();
   final Map<String, List<Offer>> markets = new LinkedHashMap<>();

   public static TradeData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(TradeData::load, TradeData::new, NAME);
   }

   public List<Trader> of(UUID realm) {
      List<Trader> out = new ArrayList<>();

      for (Trader t : this.traders.values()) {
         if (t.realm.equals(realm)) {
            out.add(t);
         }
      }

      return out;
   }

   public List<Offer> market(String settlement) {
      return this.markets.getOrDefault(settlement, List.of());
   }

   List<Offer> marketForEdit(String settlement) {
      return this.markets.computeIfAbsent(settlement, k -> new ArrayList<>());
   }

   /** Saved every time: traders move all the time. */
   public boolean isDirty() {
      return true;
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag t = new ListTag();

      for (Trader tr : this.traders.values()) {
         t.add(tr.save());
      }

      tag.put("traders", t);
      CompoundTag m = new CompoundTag();
      this.markets.forEach((k, offers) -> {
         if (!offers.isEmpty()) {
            ListTag l = new ListTag();

            for (Offer o : offers) {
               l.add(o.save());
            }

            m.put(k, l);
         }
      });
      tag.put("markets", m);
      return tag;
   }

   public static TradeData load(CompoundTag tag) {
      TradeData d = new TradeData();

      for (Tag x : tag.getList("traders", 10)) {
         Trader tr = Trader.load((CompoundTag)x);
         d.traders.put(tr.id, tr);
      }

      CompoundTag m = tag.getCompound("markets");

      for (String k : m.getAllKeys()) {
         List<Offer> offers = new ArrayList<>();

         for (Tag x : m.getList(k, 10)) {
            offers.add(Offer.load((CompoundTag)x));
         }

         d.markets.put(k, offers);
      }

      return d;
   }
}
