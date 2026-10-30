package com.nick.prestol.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nick.prestol.flag.BannerImages;
import com.nick.prestol.map.MapSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class FlagArt {
   private static final Map<String, Optional<ResourceLocation>> PATTERNS = new HashMap<>();
   private static final Map<String, Optional<ResourceLocation>> CLOTHS = new HashMap<>();
   private static final int CLOTH_W = 96;
   private static final int CLOTH_H = 64;
   private static Boolean paintings;
   private static Boolean betsy;

   private FlagArt() {
   }

   public static boolean paintings() {
      if (paintings == null) {
         paintings = ModList.get().isLoaded("immersive_paintings");
      }

      return paintings;
   }

   /** Whether realm colours fly as Betsy Ross flags in the world (and so are drawn as flags here, not banners). */
   public static boolean betsy() {
      if (betsy == null) {
         betsy = ModList.get().isLoaded("betsyross") && paintings();
      }

      return betsy;
   }

   /** The Betsy Ross flag standard as an icon, or a banner without it. */
   public static Item flagItem() {
      ResourceLocation id = new ResourceLocation("betsyross", "flag_standard");
      Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
      return item != null && item != Items.AIR ? item : Items.RED_BANNER;
   }

   public static int draw(GuiGraphics g, String image, List<MapSnapshot.Layer> banner, int x, int y, int w, int h, boolean big) {
      return place(g, image, banner, x, y, w, h, big, false)[0];
   }

   public static int[] drawOnPole(GuiGraphics g, String image, List<MapSnapshot.Layer> banner, int x, int y, int w, int h) {
      return place(g, image, banner, x, y, w, h, false, true);
   }

   private static int[] place(GuiGraphics g, String image, List<MapSnapshot.Layer> banner, int x, int y, int w, int h, boolean big, boolean onPole) {
      if (image != null && !image.isEmpty() && paintings()) {
         try {
            PaintingArt.Pic pic = PaintingArt.texture(image, big);
            if (pic != null) {
               float ratio = (float)pic.blocksW() / (float)Math.max(1, pic.blocksH());
               int dw = w;
               int dh = Math.round((float)w / ratio);
               if (dh > h) {
                  dh = h;
                  dw = Math.round((float)h * ratio);
               }

               int dx = onPole ? x : x + (w - dw) / 2;
               int dy = onPole ? y : y + (h - dh) / 2;
               RenderSystem.enableBlend();
               g.blit(pic.texture(), dx, dy, dw, dh, 0.0F, 0.0F, pic.pixelsW(), pic.pixelsH(), pic.pixelsW(), pic.pixelsH());
               return new int[]{dw, dh};
            }
         } catch (LinkageError | RuntimeException var15) {
         }
      }

      if (betsy() && banner != null && !banner.isEmpty()) {
         Optional<ResourceLocation> cloth = cloth(banner);
         if (cloth.isPresent()) {
            ResourceLocation tex = cloth.get();
            int dw = w;
            int dh = Math.round((float)w * CLOTH_H / CLOTH_W);
            if (dh > h) {
               dh = h;
               dw = Math.round((float)h * CLOTH_W / CLOTH_H);
            }

            int dx = onPole ? x : x + (w - dw) / 2;
            int dy = onPole ? y : y + (h - dh) / 2;
            g.blit(tex, dx, dy, dw, dh, 0.0F, 0.0F, CLOTH_W, CLOTH_H, CLOTH_W, CLOTH_H);
            return new int[]{dw, dh};
         }
      }

      int bw = Math.min(w, h / 2);
      int bh = Math.min(h, bw * 2);
      int dx = onPole ? x : x + (w - bw) / 2;
      banner(g, banner, dx, y, bw, bh);
      return new int[]{bw, bh};
   }

   /** The realm's colours as the cloth of its Betsy Ross flag, painted the same way the server paints the flag. */
   private static Optional<ResourceLocation> cloth(List<MapSnapshot.Layer> layers) {
      StringBuilder key = new StringBuilder();

      for (MapSnapshot.Layer l : layers) {
         key.append(l.pattern()).append(l.color()).append('|');
      }

      return CLOTHS.computeIfAbsent(key.toString(), k -> {
         try {
            DyeColor base = DyeColor.WHITE;
            ListTag patterns = new ListTag();

            for (int i = 0; i < layers.size(); i++) {
               MapSnapshot.Layer l = layers.get(i);
               if (i == 0 && "b".equals(l.pattern())) {
                  base = DyeColor.byId(l.color());
               } else {
                  CompoundTag t = new CompoundTag();
                  t.putString("Pattern", l.pattern());
                  t.putInt("Color", l.color());
                  patterns.add(t);
               }
            }

            int[] px = BannerImages.render(base, patterns, CLOTH_W, CLOTH_H);
            NativeImage img = new NativeImage(CLOTH_W, CLOTH_H, false);

            for (int y = 0; y < CLOTH_H; y++) {
               for (int x = 0; x < CLOTH_W; x++) {
                  int c = px[y * CLOTH_W + x];
                  // NativeImage pixels are ABGR
                  img.setPixelRGBA(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | c >> 16 & 0xFF);
               }
            }

            return Optional.of(Minecraft.getInstance().getTextureManager().register("prestol_cloth", new DynamicTexture(img)));
         } catch (RuntimeException e) {
            return Optional.empty();
         }
      });
   }

   private static Optional<ResourceLocation> pattern(String code) {
      return PATTERNS.computeIfAbsent(code, c -> {
         try {
            for (Entry<ResourceKey<BannerPattern>, BannerPattern> e : BuiltInRegistries.BANNER_PATTERN.entrySet()) {
               if (e.getValue().getHashname().equals(c)) {
                  ResourceLocation tex = BannerPattern.location(e.getKey(), true);
                  return Optional.of(new ResourceLocation(tex.getNamespace(), "textures/" + tex.getPath() + ".png"));
               }
            }
         } catch (RuntimeException var4) {
         }

         return Optional.empty();
      });
   }

   public static void banner(GuiGraphics g, List<MapSnapshot.Layer> layers, int x, int y, int w, int h) {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();

      for (MapSnapshot.Layer l : layers == null ? List.<MapSnapshot.Layer>of() : layers) {
         Optional<ResourceLocation> tex = pattern(l.pattern());
         if (!tex.isEmpty()) {
            float[] c = DyeColor.byId(l.color()).getTextureDiffuseColors();
            RenderSystem.setShaderColor(c[0], c[1], c[2], 1.0F);
            g.blit(tex.get(), x, y, w, h, 1.0F, 1.0F, 20, 40, 64, 64);
         }
      }

      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public static List<String[]> choices() {
      List<String[]> out = new ArrayList<>();
      if (!paintings()) {
         return out;
      } else {
         try {
            for (PaintingArt.Choice c : PaintingArt.choices()) {
               out.add(new String[]{c.id(), c.name(), c.author(), c.w() + "x" + c.h()});
            }
         } catch (LinkageError | RuntimeException var3) {
         }

         return out;
      }
   }
}
