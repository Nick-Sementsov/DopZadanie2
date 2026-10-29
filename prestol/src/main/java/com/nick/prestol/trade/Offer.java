package com.nick.prestol.trade;

import net.minecraft.nbt.CompoundTag;

/**
 * A line of a settlement's market. A sell offer sells the item to foreign traders at {@code price} coins apiece,
 * but keeps {@code amount} of it in store; a buy offer buys it until the store holds {@code amount}.
 */
public record Offer(String item, boolean sell, int price, int amount) {
   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("item", this.item);
      t.putBoolean("sell", this.sell);
      t.putInt("price", this.price);
      t.putInt("amount", this.amount);
      return t;
   }

   public static Offer load(CompoundTag t) {
      return new Offer(t.getString("item"), t.getBoolean("sell"), t.getInt("price"), t.getInt("amount"));
   }
}
