package com.nick.prestol.trade;

import com.nick.prestol.Prestol;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * What goods are worth, in coins (a gold nugget is one coin). Raw goods have a price of their own; everything made
 * from them is priced through its recipes — the cheapest way to make it plus the work — so the goods of any mod in
 * the pack get a fair price without anyone writing it down. Prices are fractions: a sheaf of wheat is worth less
 * than a nugget.
 */
public final class Prices {
   /** What the work of making a thing adds to the price of what it is made of. */
   private static final double WORK = 1.12;
   private static final double SMELT = 0.04;
   private static final Map<Item, Double> SEED = new HashMap<>();
   private static final Map<TagKey<Item>, Double> SEED_TAGS = new HashMap<>();
   private static Map<Item, Double> values = Map.of();
   private static RecipeManager computedFor;

   private Prices() {
   }

   static {
      // money is not a good
      seed(Items.GOLD_NUGGET, 1.0);
      seed(Items.GOLD_INGOT, 9.0);
      seed(Items.GOLD_BLOCK, 81.0);
      // metals and stones of value
      seed(Items.RAW_GOLD, 7.0);
      seed(Items.IRON_INGOT, 3.0);
      seed(Items.RAW_IRON, 2.4);
      seed(Items.IRON_NUGGET, 0.34);
      seed(Items.COPPER_INGOT, 0.7);
      seed(Items.RAW_COPPER, 0.55);
      seed(Items.COAL, 0.5);
      seed(Items.CHARCOAL, 0.45);
      seed(Items.DIAMOND, 32.0);
      seed(Items.EMERALD, 9.0);
      seed(Items.LAPIS_LAZULI, 0.8);
      seed(Items.REDSTONE, 0.5);
      seed(Items.QUARTZ, 0.9);
      seed(Items.AMETHYST_SHARD, 1.0);
      seed(Items.NETHERITE_SCRAP, 60.0);
      seed(Items.GLOWSTONE_DUST, 0.6);
      // earth and stone
      seed(Items.COBBLESTONE, 0.04);
      seed(Items.COBBLED_DEEPSLATE, 0.04);
      seed(Items.STONE, 0.07);
      seed(Items.DIRT, 0.02);
      seed(Items.SAND, 0.04);
      seed(Items.RED_SAND, 0.04);
      seed(Items.GRAVEL, 0.04);
      seed(Items.CLAY_BALL, 0.12);
      seed(Items.FLINT, 0.15);
      seed(Items.NETHERRACK, 0.02);
      seed(Items.ANDESITE, 0.05);
      seed(Items.DIORITE, 0.05);
      seed(Items.GRANITE, 0.05);
      seed(Items.TUFF, 0.05);
      seed(Items.CALCITE, 0.06);
      seed(Items.BLACKSTONE, 0.05);
      seed(Items.BASALT, 0.05);
      seed(Items.END_STONE, 0.1);
      seed(Items.OBSIDIAN, 1.5);
      seed(Items.ICE, 0.1);
      seed(Items.SNOWBALL, 0.02);
      seed(Items.MOSS_BLOCK, 0.1);
      seed(Items.MUD, 0.03);
      // the field and the garden
      seed(Items.WHEAT, 0.35);
      seed(Items.WHEAT_SEEDS, 0.03);
      seed(Items.CARROT, 0.3);
      seed(Items.POTATO, 0.3);
      seed(Items.BEETROOT, 0.3);
      seed(Items.BEETROOT_SEEDS, 0.03);
      seed(Items.MELON_SLICE, 0.08);
      seed(Items.MELON_SEEDS, 0.05);
      seed(Items.PUMPKIN, 0.45);
      seed(Items.PUMPKIN_SEEDS, 0.05);
      seed(Items.SUGAR_CANE, 0.15);
      seed(Items.COCOA_BEANS, 0.35);
      seed(Items.APPLE, 0.45);
      seed(Items.SWEET_BERRIES, 0.12);
      seed(Items.GLOW_BERRIES, 0.2);
      seed(Items.BAMBOO, 0.03);
      seed(Items.CACTUS, 0.08);
      seed(Items.KELP, 0.04);
      seed(Items.NETHER_WART, 0.3);
      seed(Items.BROWN_MUSHROOM, 0.15);
      seed(Items.RED_MUSHROOM, 0.15);
      seed(Items.VINE, 0.05);
      seed(Items.LILY_PAD, 0.1);
      seed(Items.CHORUS_FRUIT, 0.3);
      // the herd, the hunt and the sea
      seed(Items.BEEF, 0.7);
      seed(Items.PORKCHOP, 0.7);
      seed(Items.CHICKEN, 0.5);
      seed(Items.MUTTON, 0.6);
      seed(Items.RABBIT, 0.5);
      seed(Items.COD, 0.4);
      seed(Items.SALMON, 0.5);
      seed(Items.TROPICAL_FISH, 0.5);
      seed(Items.PUFFERFISH, 0.5);
      seed(Items.EGG, 0.15);
      seed(Items.MILK_BUCKET, 3.3);
      seed(Items.HONEY_BOTTLE, 0.8);
      seed(Items.HONEYCOMB, 0.8);
      seed(Items.LEATHER, 1.0);
      seed(Items.RABBIT_HIDE, 0.25);
      seed(Items.FEATHER, 0.15);
      seed(Items.STRING, 0.25);
      seed(Items.BONE, 0.25);
      seed(Items.INK_SAC, 0.25);
      seed(Items.GLOW_INK_SAC, 0.6);
      seed(Items.GUNPOWDER, 0.9);
      seed(Items.SLIME_BALL, 0.9);
      seed(Items.SPIDER_EYE, 0.4);
      seed(Items.ROTTEN_FLESH, 0.03);
      seed(Items.ENDER_PEARL, 4.0);
      seed(Items.BLAZE_ROD, 4.0);
      seed(Items.GHAST_TEAR, 6.0);
      seed(Items.PRISMARINE_SHARD, 0.5);
      seed(Items.PRISMARINE_CRYSTALS, 0.6);
      seed(Items.NAUTILUS_SHELL, 4.0);
      seed(Items.SCUTE, 3.0);
      seed(Items.PHANTOM_MEMBRANE, 2.0);
      seed(Items.SHULKER_SHELL, 12.0);
      seed(Items.NETHER_STAR, 200.0);
      seed(Items.TOTEM_OF_UNDYING, 60.0);
      seed(Items.SADDLE, 6.0);
      seed(Items.NAME_TAG, 6.0);
      // woods and wool, by kind
      seedTag(ItemTags.LOGS, 0.35);
      seedTag(ItemTags.SAPLINGS, 0.05);
      seedTag(ItemTags.LEAVES, 0.02);
      seedTag(ItemTags.SMALL_FLOWERS, 0.08);
      seedTag(ItemTags.TALL_FLOWERS, 0.12);
      seed(Items.WHITE_WOOL, 0.5);
   }

   private static void seed(Item item, double v) {
      SEED.put(item, v);
   }

   private static void seedTag(TagKey<Item> tag, double v) {
      SEED_TAGS.put(tag, v);
   }

   /** Money itself is not bought or sold. */
   public static boolean money(Item item) {
      return item == Items.GOLD_NUGGET || item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK;
   }

   /** What one {@code item} is worth in coins; 0 for goods nobody trades (money, unpriced oddities). */
   public static double of(Item item) {
      if (item == null || item == Items.AIR || money(item)) {
         return 0.0;
      } else {
         Double v = values.get(item);
         return v == null ? fallback(item) : v;
      }
   }

   public static double of(String id) {
      return of(Cargo.item(id));
   }

   /** A thing nobody prices and nothing makes: by what it is. */
   private static double fallback(Item item) {
      ItemStack s = new ItemStack(item);
      Rarity r = s.getRarity();
      if (r == Rarity.EPIC) {
         return 48.0;
      } else if (r == Rarity.RARE) {
         return 16.0;
      } else if (r == Rarity.UNCOMMON) {
         return 4.0;
      } else if (item.isEdible()) {
         // a crop of another mod: like ours, by how much it feeds
         var food = item.getFoodProperties(s, null);
         return food == null ? 0.3 : Math.max(0.1, food.getNutrition() * 0.12);
      } else {
         return 0.2;
      }
   }

   /** Prices are worked out again after every (re)load of the recipes. */
   public static void refresh(MinecraftServer server) {
      RecipeManager rm = server.getRecipeManager();
      if (rm != computedFor) {
         computedFor = rm;
         long start = System.nanoTime();
         values = compute(rm, server.registryAccess());
         Prestol.LOGGER.info("Prestol: priced {} goods from recipes in {} ms", values.size(), (System.nanoTime() - start) / 1000000L);
      }
   }

   private static Map<Item, Double> compute(RecipeManager rm, RegistryAccess access) {
      Map<Item, Double> v = new HashMap<>(SEED);

      for (Map.Entry<TagKey<Item>, Double> e : SEED_TAGS.entrySet()) {
         for (Item item : ForgeRegistries.ITEMS.tags().getTag(e.getKey())) {
            v.putIfAbsent(item, e.getValue());
         }
      }

      List<Recipe<?>> recipes = new java.util.ArrayList<>();
      recipes.addAll(rm.getAllRecipesFor(RecipeType.CRAFTING));
      recipes.addAll(rm.getAllRecipesFor(RecipeType.SMELTING));
      recipes.addAll(rm.getAllRecipesFor(RecipeType.STONECUTTING));
      // each pass prices what can be made from what already has a price; ten passes reach deep enough
      for (int pass = 0; pass < 10; pass++) {
         boolean changed = false;

         for (Recipe<?> r : recipes) {
            ItemStack out;
            try {
               out = r.getResultItem(access);
            } catch (RuntimeException e) {
               continue;
            }

            if (out.isEmpty() || SEED.containsKey(out.getItem()) || money(out.getItem())) {
               continue;
            }

            double cost = 0.0;
            boolean known = true;
            int parts = 0;

            for (Ingredient ing : r.getIngredients()) {
               if (ing.isEmpty()) {
                  continue;
               }

               double best = Double.MAX_VALUE;

               for (ItemStack opt : ing.getItems()) {
                  Double p = v.get(opt.getItem());
                  if (p != null && p < best) {
                     best = p;
                  }
               }

               if (best == Double.MAX_VALUE) {
                  known = false;
                  break;
               }

               cost += best;
               parts++;
            }

            if (known && parts > 0) {
               double price = cost / out.getCount() * WORK + (r instanceof AbstractCookingRecipe ? SMELT : 0.0);
               Double was = v.get(out.getItem());
               if (was == null || price < was - 1.0E-6) {
                  v.put(out.getItem(), price);
                  changed = true;
               }
            }
         }

         if (!changed) {
            break;
         }
      }

      return v;
   }

   /** A price as people say it: whole coins for dear goods, a decimal or two for cheap ones. */
   public static String fmt(double price) {
      if (price >= 100.0) {
         return Long.toString(Math.round(price));
      } else {
         return BigDecimal.valueOf(price).setScale(price >= 10.0 ? 1 : 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
      }
   }

   static ResourceLocation id(Item item) {
      return ForgeRegistries.ITEMS.getKey(item);
   }
}
