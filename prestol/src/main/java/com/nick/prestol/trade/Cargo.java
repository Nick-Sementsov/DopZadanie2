package com.nick.prestol.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

/** The goods a trader carries: a fixed number of slots, like the chests on his mules or in his boat. */
public final class Cargo {
   final List<ItemStack> slots = new ArrayList<>();
   int size;

   public Cargo(int size) {
      this.size = size;
   }

   public static Item item(String id) {
      ResourceLocation rl = ResourceLocation.tryParse(id);
      Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
      return item == null ? Items.AIR : item;
   }

   public static String id(Item item) {
      return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
   }

   public int size() {
      return this.size;
   }

   public int used() {
      return this.slots.size();
   }

   public List<ItemStack> stacks() {
      return this.slots;
   }

   public int count(Item item) {
      int n = 0;

      for (ItemStack s : this.slots) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }

      return n;
   }

   /** How many more of {@code item} fit. */
   public int room(Item item) {
      int max = item.getMaxStackSize();
      int n = (this.size - this.slots.size()) * max;

      for (ItemStack s : this.slots) {
         if (s.is(item) && !s.hasTag()) {
            n += max - s.getCount();
         }
      }

      return n;
   }

   /** Adds up to {@code count} plain items; returns how many went in. */
   public int add(Item item, int count) {
      int left = count;

      for (ItemStack s : this.slots) {
         if (left <= 0) {
            break;
         }

         if (s.is(item) && !s.hasTag() && s.getCount() < s.getMaxStackSize()) {
            int n = Math.min(left, s.getMaxStackSize() - s.getCount());
            s.grow(n);
            left -= n;
         }
      }

      while (left > 0 && this.slots.size() < this.size) {
         int n = Math.min(left, item.getMaxStackSize());
         this.slots.add(new ItemStack(item, n));
         left -= n;
      }

      return count - left;
   }

   /** Takes up to {@code count}; returns the stacks taken. */
   public List<ItemStack> take(Item item, int count) {
      List<ItemStack> out = new ArrayList<>();
      int left = count;

      for (int i = this.slots.size() - 1; i >= 0 && left > 0; i--) {
         ItemStack s = this.slots.get(i);
         if (s.is(item)) {
            ItemStack part = s.split(Math.min(left, s.getCount()));
            left -= part.getCount();
            out.add(part);
            if (s.isEmpty()) {
               this.slots.remove(i);
            }
         }
      }

      return out;
   }

   /** Item counts by id, in the order first carried. */
   public Map<String, Integer> summary() {
      Map<String, Integer> out = new LinkedHashMap<>();

      for (ItemStack s : this.slots) {
         out.merge(id(s.getItem()), s.getCount(), Integer::sum);
      }

      return out;
   }

   ListTag save() {
      ListTag l = new ListTag();

      for (ItemStack s : this.slots) {
         l.add(s.save(new CompoundTag()));
      }

      return l;
   }

   void load(ListTag l) {
      this.slots.clear();

      for (Tag t : l) {
         ItemStack s = ItemStack.of((CompoundTag)t);
         if (!s.isEmpty()) {
            this.slots.add(s);
         }
      }
   }

   // ---- settlement stores ----

   public static int stock(List<IItemHandler> stores, Item item) {
      int n = 0;

      for (IItemHandler h : stores) {
         for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.is(item)) {
               n += s.getCount();
            }
         }
      }

      return n;
   }

   /** Takes up to {@code count} of the item out of the stores. */
   public static List<ItemStack> extract(List<IItemHandler> stores, Item item, int count) {
      List<ItemStack> out = new ArrayList<>();
      int left = count;

      for (IItemHandler h : stores) {
         for (int i = 0; i < h.getSlots() && left > 0; i++) {
            if (h.getStackInSlot(i).is(item)) {
               ItemStack got = h.extractItem(i, left, false);
               if (!got.isEmpty()) {
                  left -= got.getCount();
                  out.add(got);
               }
            }
         }
      }

      return out;
   }

   /** Puts the stack into the stores; returns what did not fit. */
   public static ItemStack insert(List<IItemHandler> stores, ItemStack stack) {
      ItemStack left = stack.copy();

      for (IItemHandler h : stores) {
         for (int i = 0; i < h.getSlots() && !left.isEmpty(); i++) {
            left = h.insertItem(i, left, false);
         }
      }

      return left;
   }

   /** How many of the item the stores could still take. */
   public static int space(List<IItemHandler> stores, Item item) {
      int n = 0;
      ItemStack probe = new ItemStack(item, item.getMaxStackSize());

      for (IItemHandler h : stores) {
         for (int i = 0; i < h.getSlots(); i++) {
            n += probe.getCount() - h.insertItem(i, probe.copy(), true).getCount();
         }
      }

      return n;
   }
}
