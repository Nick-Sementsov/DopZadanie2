package com.nick.prestol.flag;

import com.nick.prestol.Prestol;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

public final class BannerImages {
   private static final int RES = 32;
   private static final Set<String> KNOWN = new HashSet<>();
   private static boolean broken;
   private static final String[] CREEPER = new String[]{"........", ".##..##.", ".##..##.", "...##...", "..####..", "..####..", "..#..#..", "........"};
   private static final String[] SKULL = new String[]{
      "..#####..",
      ".#######.",
      ".#.###.#.",
      ".#.###.#.",
      ".###.###.",
      "..#####..",
      "..#.#.#..",
      ".........",
      "#.......#",
      ".##...##.",
      "...###...",
      ".##...##.",
      "#.......#"
   };
   private static final String[] FLOWER = new String[]{
      "....#....", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "....#...."
   };
   private static final String[] THING = new String[]{".#######.", "#########", "##.###.##", "##.###.##", "##..#..##", "##.....##", ".##...##."};
   private static final String[] SNOUT = new String[]{"########", "#.####.#", "########", "##.##.##", "########", ".#....#."};

   private BannerImages() {
   }

   public static void reset() {
      KNOWN.clear();
      broken = false;
   }

   public static String idFor(MinecraftServer server, ListTag layers, boolean mast) {
      if (!broken && Flags.paintings() && layers != null && !layers.isEmpty()) {
         DyeColor base = DyeColor.byId(layers.getCompound(0).getInt("Color"));
         StringBuilder key = new StringBuilder().append(base.getId());

         for (int i = 1; i < layers.size(); i++) {
            key.append('|').append(layers.getCompound(i).getString("Pattern")).append(layers.getCompound(i).getInt("Color"));
         }

         String k = key.toString();
         String hex = String.format("%08x%04x", k.hashCode(), k.length() * 131 + new StringBuilder(k).reverse().toString().hashCode() & 65535);
         String id = "prestol:banner_" + (mast ? "flag_" : "std_") + hex;
         if (KNOWN.contains(id)) {
            return id;
         } else {
            try {
               ListTag patterns = new ListTag();

               for (int i = 1; i < layers.size(); i++) {
                  patterns.add(layers.getCompound(i).copy());
               }

               register(server, new ResourceLocation(id), base, patterns, mast ? 3 : 2, mast ? 2 : 1);
               KNOWN.add(id);
               return id;
            } catch (Throwable var10) {
               broken = true;
               Prestol.LOGGER.warn("Prestol: banners cannot be turned into images, plain banners stay in use", var10);
               return "";
            }
         }
      } else {
         return "";
      }
   }

   private static void register(MinecraftServer server, ResourceLocation id, DyeColor base, ListTag layers, int bw, int bh) throws Exception {
      Class<?> mgr = Class.forName("immersive_paintings.resources.ServerPaintingManager");
      Object store = mgr.getMethod("get").invoke(null);
      Map<?, ?> custom = (Map<?, ?>)store.getClass().getMethod("getCustomServerPaintings").invoke(store);
      if (!custom.containsKey(id)) {
         int w = bw * 32;
         int h = bh * 32;
         int[] px = draw(base, layers, w, h);
         Class<?> imageC = Class.forName("immersive_paintings.resources.ByteImage");
         Object image = imageC.getConstructor(int.class, int.class).newInstance(w, h);
         Method set = imageC.getMethod("setPixel", int.class, int.class, int.class, int.class, int.class, int.class);

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int c = px[y * w + x];
               set.invoke(image, x, y, c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, 255);
            }
         }

         Class<?> paintingC = Class.forName("immersive_paintings.resources.Painting");
         Constructor<?> ctor = paintingC.getConstructor(
            imageC, int.class, int.class, int.class, String.class, String.class, boolean.class, boolean.class, boolean.class, String.class
         );
         Object painting = ctor.newInstance(
            image, bw, bh, 32, "Banner " + id.getPath(), "Prestol", false, true, false, id.toString().replaceAll("[^a-zA-Z0-9]", "")
         );
         mgr.getMethod("registerPainting", ResourceLocation.class, paintingC).invoke(null, id, painting);
         Class<?> msgC = Class.forName("immersive_paintings.network.s2c.PaintingListMessage");
         Object msg = msgC.getConstructor(ResourceLocation.class, paintingC).newInstance(id, painting);
         Method send = Class.forName("immersive_paintings.cobalt.network.NetworkHandler")
            .getMethod("sendToPlayer", Class.forName("immersive_paintings.cobalt.network.Message"), ServerPlayer.class);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send.invoke(null, msg, p);
         }

         Prestol.LOGGER.info("Prestol: banner image {} registered ({} x {} blocks)", new Object[]{id, bw, bh});
      }
   }

   private static int[] draw(DyeColor base, ListTag layers, int w, int h) {
      float[][] rgb = new float[w * h][];
      float[] cloth = base.getTextureDiffuseColors();

      for (int i = 0; i < rgb.length; i++) {
         rgb[i] = new float[]{cloth[0], cloth[1], cloth[2]};
      }

      for (int l = 0; l < layers.size(); l++) {
         String code = layers.getCompound(l).getString("Pattern");
         float[] dye = DyeColor.byId(layers.getCompound(l).getInt("Color")).getTextureDiffuseColors();

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               float a = coverage(code, (float)x + 0.5F, (float)y + 0.5F, w, h);
               if (!(a <= 0.0F)) {
                  float[] p = rgb[y * w + x];

                  for (int c = 0; c < 3; c++) {
                     p[c] += (dye[c] - p[c]) * a;
                  }
               }
            }
         }
      }

      int[] out = new int[w * h];

      for (int y = 0; y < h; y++) {
         for (int xx = 0; xx < w; xx++) {
            float k = 1.0F + 0.055F * (float)Math.sin((Math.PI * 2) * (double)xx / (double)((float)w / 2.6F) + 0.7F) - 0.05F * (float)xx / (float)w;
            if (xx == 0 || y == 0 || xx == w - 1 || y == h - 1) {
               k *= 0.84F;
            }

            float[] p = rgb[y * w + xx];
            out[y * w + xx] = clamp(p[0] * 255.0F * k) << 16 | clamp(p[1] * 255.0F * k) << 8 | clamp(p[2] * 255.0F * k);
         }
      }

      return out;
   }

   private static int clamp(float v) {
      return Math.max(0, Math.min(255, Math.round(v)));
   }

   private static float line(float x, float y, float x0, float y0, float x1, float y1) {
      return (float)((double)Math.abs((y1 - y0) * x - (x1 - x0) * y + x1 * y0 - y1 * x0) / Math.hypot((double)(x1 - x0), (double)(y1 - y0)));
   }

   private static float bitmap(String[] rows, float x, float y, int w, int h) {
      int s = Math.max(1, (int)((float)h * 0.62F / (float)rows.length));
      float bx = (x - (float)(w - rows[0].length() * s) / 2.0F) / (float)s;
      float by = (y - (float)(h - rows.length * s) / 2.0F) / (float)s;
      if (!(bx < 0.0F) && !(by < 0.0F) && !(by >= (float)rows.length) && !(bx >= (float)rows[0].length())) {
         return rows[(int)by].charAt((int)bx) == '#' ? 1.0F : 0.0F;
      } else {
         return 0.0F;
      }
   }

   private static float coverage(String code, float x, float y, int w, int h) {
      float u = x / (float)w;
      float v = y / (float)h;
      float t = (float)h * 0.2F;
      if ("gra".equals(code)) {
         return 1.0F - v;
      } else if ("gru".equals(code)) {
         return v;
      } else {
         boolean on = switch (code) {
            case "b" -> true;
            case "bl" -> u < 0.5F && v >= 0.5F;
            case "br" -> u >= 0.5F && v >= 0.5F;
            case "tl" -> u < 0.5F && v < 0.5F;
            case "tr" -> u >= 0.5F && v < 0.5F;
            case "bs" -> v > 0.6666667F;
            case "ts" -> v < 0.33333334F;
            case "ls" -> u < 0.33333334F;
            case "rs" -> u > 0.6666667F;
            case "cs" -> Math.abs(x - (float)w / 2.0F) < t / 2.0F;
            case "ms" -> Math.abs(y - (float)h / 2.0F) < t / 2.0F;
            case "drs" -> line(x, y, 0.0F, 0.0F, (float)w, (float)h) < t / 2.0F;
            case "dls" -> line(x, y, 0.0F, (float)h, (float)w, 0.0F) < t / 2.0F;
            case "ss" -> (int)(u * 9.0F) % 2 == 1;
            case "cr" -> line(x, y, 0.0F, 0.0F, (float)w, (float)h) < t / 2.0F || line(x, y, 0.0F, (float)h, (float)w, 0.0F) < t / 2.0F;
            case "sc" -> Math.abs(x - (float)w / 2.0F) < t / 2.0F || Math.abs(y - (float)h / 2.0F) < t / 2.0F;
            case "bt" -> v - 0.5F >= Math.abs(u - 0.5F);
            case "tt" -> 0.5F - v >= Math.abs(u - 0.5F);
            case "bts" -> v > 1.0F - 0.2F * (1.0F - Math.abs(u * 3.0F % 1.0F * 2.0F - 1.0F));
            case "tts" -> v < 0.2F * (1.0F - Math.abs(u * 3.0F % 1.0F * 2.0F - 1.0F));
            case "ld" -> u + v < 1.0F;
            case "rd" -> u > v;
            case "lud" -> u < v;
            case "rud" -> u + v > 1.0F;
            case "vh" -> u < 0.5F;
            case "vhr" -> u >= 0.5F;
            case "hh" -> v < 0.5F;
            case "hhb" -> v >= 0.5F;
            case "mc" -> Math.hypot((double)(x - (float)w / 2.0F), (double)(y - (float)h / 2.0F)) < (double)((float)h * 0.28F);
            case "mr" -> Math.abs(x - (float)w / 2.0F) / ((float)h * 0.34F) + Math.abs(y - (float)h / 2.0F) / ((float)h * 0.34F) < 1.0F;
            case "bo" -> x < (float)h * 0.1F || y < (float)h * 0.1F || x > (float)w - (float)h * 0.1F || y > (float)h - (float)h * 0.1F;
            case "cbo" -> {
               float d = Math.min(Math.min(x, (float)w - x), Math.min(y, (float)h - y));
               float along = Math.min(y, (float)h - y) < Math.min(x, (float)w - x) ? x : y;
               yield d < (float)h * (0.07F + 0.05F * (float)Math.abs(Math.sin((double)along * Math.PI / (double)((float)h * 0.16F))));
            }
            case "bri" -> {
               float bh = (float)h / 8.0F;
               float bw = bh * 2.0F;
               int row = (int)(y / bh);
               float xx = x + (row % 2 == 0 ? 0.0F : bw / 2.0F);
               yield y % bh >= 1.0F && xx % bw >= 1.0F;
            }
            case "glb" -> {
               float r = (float)Math.hypot((double)(x - (float)w / 2.0F), (double)(y - (float)h / 2.0F)) / ((float)h * 0.36F);
               yield r < 1.0F && Math.abs(Math.sin((double)((y - (float)h / 2.0F) / ((float)h * 0.36F) * 2.6F) + Math.sin((double)(x * 0.35F)) * 0.6F)) > 0.45F;
            }
            case "cre" -> bitmap(CREEPER, x, y, w, h) > 0.0F;
            case "sku" -> bitmap(SKULL, x, y, w, h) > 0.0F;
            case "flo" -> bitmap(FLOWER, x, y, w, h) > 0.0F;
            case "moj" -> bitmap(THING, x, y, w, h) > 0.0F;
            case "pig" -> bitmap(SNOUT, x, y, w, h) > 0.0F;
            default -> false;
         };
         return on ? 1.0F : 0.0F;
      }
   }
}
