package com.nick.prestol.trade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/** A settlement on a trader's route, what he does there, and the way points of the road that leads to it. */
public final class Stop {
   public static final int MAX_ORDERS = 12;
   public static final int MAX_WAYPOINTS = 32;
   final String settlement;
   final List<TradeOrder> orders = new ArrayList<>();
   final List<BlockPos> waypoints = new ArrayList<>();

   public Stop(String settlement) {
      this.settlement = settlement;
   }

   public String settlement() {
      return this.settlement;
   }

   public List<TradeOrder> orders() {
      return this.orders;
   }

   public List<BlockPos> waypoints() {
      return this.waypoints;
   }

   CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("settlement", this.settlement);
      ListTag o = new ListTag();

      for (TradeOrder order : this.orders) {
         o.add(order.save());
      }

      t.put("orders", o);
      long[] w = new long[this.waypoints.size()];

      for (int i = 0; i < w.length; i++) {
         w[i] = this.waypoints.get(i).asLong();
      }

      t.put("waypoints", new LongArrayTag(w));
      return t;
   }

   static Stop load(CompoundTag t) {
      Stop s = new Stop(t.getString("settlement"));

      for (Tag x : t.getList("orders", 10)) {
         s.orders.add(TradeOrder.load((CompoundTag)x));
      }

      for (long l : t.getLongArray("waypoints")) {
         s.waypoints.add(BlockPos.of(l));
      }

      return s;
   }
}
