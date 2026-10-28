package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.nick.prestol.PrestolConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class Supplies {
   private static final String[] POLEARMS = new String[]{
      "spear", "pike", "lance", "halberd", "glaive", "partisan", "ranseur", "guisarme", "bill", "corseque", "poleaxe", "voulge"
   };

   private Supplies() {
   }

   private static boolean polearm(ItemStack s) {
      if (s.getItem() instanceof TridentItem) {
         return true;
      } else {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
         String path = id == null ? "" : id.getPath();

         for (String p : POLEARMS) {
            if (path.contains(p)) {
               return true;
            }
         }

         return false;
      }
   }

   private static int worth(ItemStack s) {
      FoodProperties f = s.getItem().getFoodProperties(s, null);
      return f != null && f.getEffects().isEmpty() ? f.getNutrition() : 0;
   }

   public static List<Supplies.Need> needs(UnitType type) {
      List<Supplies.Need> out = new ArrayList<>(type.weapons);
      if ((Integer)PrestolConfig.ARMY_FOOD.get() > 0) {
         out.add(Supplies.Need.FOOD);
      }

      return out;
   }

   public static List<IItemHandler> stores(IColony colony, IBuilding post) {
      List<IItemHandler> out = new ArrayList<>();
      add(out, colony, post);

      for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
         if (b != post && b.getClass().getSimpleName().equals("BuildingWareHouse")) {
            add(out, colony, b);
         }
      }

      IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
      if (hall != null && hall != post) {
         add(out, colony, hall);
      }

      return out;
   }

   private static void add(List<IItemHandler> out, IColony colony, IBuilding b) {
      if (b != null && colony.getWorld() != null) {
         BlockEntity hut = colony.getWorld().getBlockEntity(b.getPosition());
         if (hut != null) {
            hut.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(out::add);
         }
      }
   }

   private static List<Supplies.Take> plan(List<IItemHandler> stores, List<Supplies.Need> needs, List<Supplies.Need> missing) {
      List<Supplies.Take> takes = new ArrayList<>();
      List<int[]> used = new ArrayList<>();

      for (IItemHandler h : stores) {
         used.add(new int[h.getSlots()]);
      }

      for (Supplies.Need need : needs) {
         int left = need.amount();

         for (int i = 0; i < stores.size() && left > 0; i++) {
            IItemHandler h = stores.get(i);

            for (int slot = 0; slot < h.getSlots() && left > 0; slot++) {
               ItemStack s = h.getStackInSlot(slot);
               int free = s.getCount() - used.get(i)[slot];
               if (free > 0 && need.matches(s)) {
                  int per = need == Supplies.Need.FOOD ? worth(s) : 1;
                  int n = Math.min(free, (left + per - 1) / per);
                  used.get(i)[slot] += n;
                  takes.add(new Supplies.Take(h, slot, n));
                  left -= n * per;
               }
            }
         }

         if (left > 0) {
            missing.add(need);
         }
      }

      return takes;
   }

   /** Eats up to {@code nutrition} food points out of the stores, in store order; returns what was eaten. */
   public static int takeFood(List<IItemHandler> stores, int nutrition) {
      int eaten = 0;

      for (IItemHandler h : stores) {
         for (int slot = 0; slot < h.getSlots() && eaten < nutrition; slot++) {
            ItemStack s = h.getStackInSlot(slot);
            int per = s.isEmpty() ? 0 : worth(s);
            if (per > 0) {
               int n = Math.min(s.getCount(), Math.max(1, (nutrition - eaten + per - 1) / per));
               ItemStack got = h.extractItem(slot, n, false);
               eaten += got.getCount() * per;
            }
         }

         if (eaten >= nutrition) {
            break;
         }
      }

      return eaten;
   }

   /** Food points lying in the stores. */
   public static int foodIn(List<IItemHandler> stores) {
      int n = 0;

      for (IItemHandler h : stores) {
         for (int slot = 0; slot < h.getSlots(); slot++) {
            ItemStack s = h.getStackInSlot(slot);
            if (!s.isEmpty()) {
               n += worth(s) * s.getCount();
            }
         }
      }

      return n;
   }

   public static List<Supplies.Need> missing(List<IItemHandler> stores, List<Supplies.Need> needs) {
      List<Supplies.Need> missing = new ArrayList<>();
      plan(stores, needs, missing);
      return missing;
   }

   public static boolean take(List<IItemHandler> stores, List<Supplies.Need> needs) {
      List<Supplies.Need> missing = new ArrayList<>();
      List<Supplies.Take> takes = plan(stores, needs, missing);
      if (!missing.isEmpty()) {
         return false;
      } else {
         for (Supplies.Take t : takes) {
            t.handler().extractItem(t.slot(), t.count(), false);
         }

         return true;
      }
   }

   public static enum Need {
      SWORD,
      POLEARM,
      SHIELD,
      BOW,
      CROSSBOW,
      ARROWS,
      FOOD;

      boolean matches(ItemStack s) {
         return switch (this) {
            case SWORD -> s.getItem() instanceof SwordItem && !Supplies.polearm(s);
            case POLEARM -> Supplies.polearm(s);
            case SHIELD -> s.getItem() instanceof ShieldItem;
            case BOW -> s.getItem() instanceof BowItem;
            case CROSSBOW -> s.getItem() instanceof CrossbowItem;
            case ARROWS -> s.is(ItemTags.ARROWS);
            case FOOD -> Supplies.worth(s) > 0;
         };
      }

      public int amount() {
         return switch (this) {
            case ARROWS -> PrestolConfig.ARMY_ARROWS.get();
            case FOOD -> PrestolConfig.ARMY_FOOD.get();
            default -> 1;
         };
      }

      public String langKey() {
         return "prestol.army.need." + this.name().toLowerCase(Locale.ROOT);
      }
   }

   private static record Take(IItemHandler handler, int slot, int count) {
   }
}
