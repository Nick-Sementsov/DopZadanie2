package com.nick.prestol.trade;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * What a trader does at a stop. In a settlement of his own realm he loads and unloads goods; in a foreign one he
 * buys from its market (paying at most {@code price} apiece) and sells to it (taking at least {@code price}).
 */
public record TradeOrder(TradeOrder.Kind kind, String item, int amount, double price) {
   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("kind", this.kind.id());
      t.putString("item", this.item);
      t.putInt("amount", this.amount);
      t.putInt("price", (int)Math.round(this.price));
      t.putDouble("p", this.price);
      return t;
   }

   public static TradeOrder load(CompoundTag t) {
      TradeOrder.Kind k = TradeOrder.Kind.byId(t.getString("kind"));
      double price = t.contains("p") ? t.getDouble("p") : t.getInt("price");
      return new TradeOrder(k == null ? TradeOrder.Kind.LOAD : k, t.getString("item"), t.getInt("amount"), price);
   }

   public static enum Kind {
      LOAD,
      UNLOAD,
      BUY,
      SELL;

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      /** Orders that need money and a foreign market. */
      public boolean market() {
         return this == BUY || this == SELL;
      }

      public static TradeOrder.Kind byId(String id) {
         for (TradeOrder.Kind k : values()) {
            if (k.id().equals(id)) {
               return k;
            }
         }

         return null;
      }
   }
}
