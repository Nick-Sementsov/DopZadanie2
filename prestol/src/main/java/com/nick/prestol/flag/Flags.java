package com.nick.prestol.flag;

import com.nick.prestol.map.Colors;
import com.nick.prestol.realm.Realm;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class Flags {
   public static final String BETSY = "betsyross";
   public static final String PAINTINGS = "immersive_paintings";
   public static final ResourceLocation FLAG_STANDARD = new ResourceLocation("betsyross", "flag_standard");
   public static final ResourceLocation FLAG_BLOCK = new ResourceLocation("betsyross", "flag_block");

   private Flags() {
   }

   public static boolean betsy() {
      return ModList.get().isLoaded("betsyross") && ModList.get().isLoaded("immersive_paintings");
   }

   public static boolean paintings() {
      return ModList.get().isLoaded("immersive_paintings");
   }

   public static int[] imageSize(String id) {
      if (id != null && !id.isEmpty() && paintings() && ResourceLocation.isValidResourceLocation(id)) {
         try {
            Class<?> mgr = Class.forName("immersive_paintings.resources.ServerPaintingManager");
            Object painting = mgr.getMethod("getPainting", ResourceLocation.class).invoke(null, new ResourceLocation(id));
            Class<?> pc = Class.forName("immersive_paintings.resources.Painting");
            Object fallback = pc.getField("DEFAULT").get(null);
            return painting != null && painting != fallback ? new int[]{pc.getField("width").getInt(painting), pc.getField("height").getInt(painting)} : null;
         } catch (Throwable var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String imageAuthor(String id) {
      try {
         Class<?> mgr = Class.forName("immersive_paintings.resources.ServerPaintingManager");
         Object painting = mgr.getMethod("getPainting", ResourceLocation.class).invoke(null, new ResourceLocation(id));
         Field f = painting.getClass().getField("author");
         Object a = f.get(painting);
         return a == null ? "" : a.toString();
      } catch (Throwable var5) {
         return "";
      }
   }

   public static Map<ResourceLocation, Object> customImages() {
      try {
         Class<?> mgr = Class.forName("immersive_paintings.resources.ServerPaintingManager");
         Object store = mgr.getMethod("get").invoke(null);
         return (Map<ResourceLocation, Object>)store.getClass().getMethod("getCustomServerPaintings").invoke(store);
      } catch (Throwable var2) {
         return Map.of();
      }
   }

   public static ItemStack banner(ListTag layers) {
      DyeColor base = DyeColor.WHITE;
      ListTag patterns = new ListTag();

      for (int i = 0; layers != null && i < layers.size(); i++) {
         CompoundTag l = layers.getCompound(i);
         if (i == 0 && "b".equals(l.getString("Pattern"))) {
            base = DyeColor.byId(l.getInt("Color"));
         } else if (patterns.size() < 12) {
            patterns.add(l.copy());
         }
      }

      ItemStack st = new ItemStack(BannerBlock.byColor(base));
      if (!patterns.isEmpty()) {
         CompoundTag be = new CompoundTag();
         be.put("Patterns", patterns);
         BlockItem.setBlockEntityData(st, BlockEntityType.BANNER, be);
      }

      return st;
   }

   public static ListTag layers(Realm realm) {
      ListTag tag = Colors.blank(realm.banner()) ? null : realm.banner();
      if (tag != null && "b".equals(tag.getCompound(0).getString("Pattern"))) {
         return tag;
      } else {
         ListTag out = new ListTag();
         CompoundTag cloth = new CompoundTag();
         cloth.putString("Pattern", "b");
         cloth.putInt("Color", Colors.nearestDye(Colors.of(realm)));
         out.add(cloth);
         if (tag != null) {
            for (int i = 0; i < tag.size(); i++) {
               out.add(tag.getCompound(i).copy());
            }
         }

         return out;
      }
   }

   public static String imageFor(MinecraftServer server, Realm realm, boolean mast) {
      if (!realm.flag().isEmpty() && imageSize(realm.flag()) != null) {
         return realm.flag();
      } else {
         return betsy() ? BannerImages.idFor(server, layers(realm), mast) : "";
      }
   }

   public static ItemStack standard(MinecraftServer server, Realm realm) {
      if (betsy()) {
         String id = imageFor(server, realm, false);
         Item item = (Item)ForgeRegistries.ITEMS.getValue(FLAG_STANDARD);
         if (item != null && ForgeRegistries.ITEMS.containsKey(FLAG_STANDARD) && !id.isEmpty()) {
            ItemStack st = new ItemStack(item);
            st.getOrCreateTag().putString("FlagId", id);
            st.setHoverName(Component.translatable("prestol.flag.standard_name", new Object[]{realm.name()}));
            return st;
         }
      }

      ItemStack b = banner(layers(realm));
      b.setHoverName(Component.translatable("prestol.flag.standard_name", new Object[]{realm.name()}));
      return b;
   }
}
