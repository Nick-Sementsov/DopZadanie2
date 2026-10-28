package com.nick.prestol.map;

import com.nick.prestol.realm.Realm;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;

public final class Colors {
   public static final int INDEPENDENT = 10395294;

   private Colors() {
   }

   public static int of(Realm realm) {
      if (realm.color() >= 0) {
         return realm.color();
      } else {
         ListTag banner = realm.banner();
         return !blank(banner) && "b".equals(banner.getCompound(0).getString("Pattern"))
            ? dye(banner.getCompound(0).getInt("Color"))
            : hsv((float)(Math.abs(realm.id().hashCode()) % 360) / 60.0F, 0.65F, 0.9F);
      }
   }

   public static boolean blank(ListTag banner) {
      return banner != null && !banner.isEmpty()
         ? banner.size() == 1 && "b".equals(banner.getCompound(0).getString("Pattern")) && banner.getCompound(0).getInt("Color") == DyeColor.WHITE.getId()
         : true;
   }

   public static int dye(int id) {
      float[] c = DyeColor.byId(id).getTextureDiffuseColors();
      return (int)(c[0] * 255.0F) << 16 | (int)(c[1] * 255.0F) << 8 | (int)(c[2] * 255.0F);
   }

   public static int nearestDye(int rgb) {
      int best = 0;
      long bestD = Long.MAX_VALUE;

      for (DyeColor d : DyeColor.values()) {
         int c = dye(d.getId());
         long r = (long)((c >> 16 & 0xFF) - (rgb >> 16 & 0xFF));
         long g = (long)((c >> 8 & 0xFF) - (rgb >> 8 & 0xFF));
         long b = (long)((c & 0xFF) - (rgb & 0xFF));
         long dist = r * r + g * g + b * b;
         if (dist < bestD) {
            bestD = dist;
            best = d.getId();
         }
      }

      return best;
   }

   private static int hsv(float h, float s, float v) {
      int i = (int)Math.floor((double)h) % 6;
      float f = h - (float)Math.floor((double)h);
      float p = v * (1.0F - s);
      float q = v * (1.0F - f * s);
      float t = v * (1.0F - (1.0F - f) * s);
      float r;
      float g;
      float b;
      switch (i) {
         case 0:
            r = v;
            g = t;
            b = p;
            break;
         case 1:
            r = q;
            g = v;
            b = p;
            break;
         case 2:
            r = p;
            g = v;
            b = t;
            break;
         case 3:
            r = p;
            g = q;
            b = v;
            break;
         case 4:
            r = t;
            g = p;
            b = v;
            break;
         default:
            r = v;
            g = p;
            b = q;
      }

      return (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
   }
}
