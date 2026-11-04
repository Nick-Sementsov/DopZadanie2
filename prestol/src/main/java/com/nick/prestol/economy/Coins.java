package com.nick.prestol.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Money of the realms is gold: a nugget is one coin, an ingot nine, a block eighty-one. */
public final class Coins {
   public static final int INGOT = 9;
   public static final int BLOCK = 81;

   private Coins() {
   }

   public static int value(ItemStack stack) {
      Item item = stack.getItem();
      if (item == Items.GOLD_NUGGET) {
         return 1;
      } else if (item == Items.GOLD_INGOT) {
         return INGOT;
      } else {
         return item == Items.GOLD_BLOCK ? BLOCK : 0;
      }
   }

   /** All the gold a player carries, in coins. */
   public static long count(Inventory inv) {
      long n = 0L;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         n += (long)value(s) * s.getCount();
      }

      return n;
   }

   /** Takes all the gold a player carries and returns how many coins it was. */
   public static long takeAll(ServerPlayer player) {
      Inventory inv = player.getInventory();
      long n = 0L;

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack s = inv.getItem(i);
         int v = value(s);
         if (v > 0) {
            n += (long)v * s.getCount();
            inv.setItem(i, ItemStack.EMPTY);
         }
      }

      return n;
   }

   /** Gives coins as gold blocks, ingots and nuggets; what does not fit falls at the player's feet. */
   public static void give(ServerPlayer player, long coins) {
      long blocks = coins / BLOCK;
      long rest = coins % BLOCK;
      put(player, Items.GOLD_BLOCK, blocks);
      put(player, Items.GOLD_INGOT, rest / INGOT);
      put(player, Items.GOLD_NUGGET, rest % INGOT);
   }

   private static void put(ServerPlayer player, Item item, long count) {
      while (count > 0L) {
         int n = (int)Math.min(count, (long)item.getMaxStackSize());
         ItemStack stack = new ItemStack(item, n);
         if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
         }

         count -= n;
      }
   }

   /** 12345 -> "12 345". */
   public static String fmt(long coins) {
      String s = Long.toString(Math.abs(coins));
      StringBuilder b = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
         if (i > 0 && (s.length() - i) % 3 == 0) {
            b.append(' ');
         }

         b.append(s.charAt(i));
      }

      return (coins < 0L ? "−" : "") + b;
   }
}
