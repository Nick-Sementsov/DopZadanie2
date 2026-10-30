package com.nick.prestol.trade;

import net.minecraft.nbt.CompoundTag;

/**
 * A line of a settlement's market. A sell offer sells the item to foreign traders at {@code price} coins apiece,
 * but keeps {@code amount} of it in store; a buy offer buys it until the store holds {@code amount}. An automatic
 * line is one the settlement offers by itself (see {@link Markets}); the owner's own lines are never automatic.
 */
public record Offer(String item, boolean sell, double price, int amount, boolean auto) {
   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("item", this.item);
      t.putBoolean("sell", this.sell);
      t.putInt("price", (int)Math.round(this.price));
      t.putDouble("p", this.price);
      t.putInt("amount", this.amount);
      if (this.auto) {
         t.putBoolean("auto", true);
      }

      return t;
   }

   public static Offer load(CompoundTag t) {
      double price = t.contains("p") ? t.getDouble("p") : t.getInt("price");
      return new Offer(t.getString("item"), t.getBoolean("sell"), price, t.getInt("amount"), t.getBoolean("auto"));
   }
}
