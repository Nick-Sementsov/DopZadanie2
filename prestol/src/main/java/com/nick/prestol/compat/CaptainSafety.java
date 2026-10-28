package com.nick.prestol.compat;

import net.minecraft.server.level.ServerPlayer;

/** Checks shared by the captain order mixins. */
public final class CaptainSafety {
   public static final double MAX_DISTANCE = 512.0;

   private CaptainSafety() {
   }

   public static boolean near(ServerPlayer player, double x, double z) {
      if (!Double.isFinite(x) || !Double.isFinite(z)) {
         return false;
      } else {
         double dx = x - player.getX();
         double dz = z - player.getZ();
         return dx * dx + dz * dz <= MAX_DISTANCE * MAX_DISTANCE && player.level().hasChunk((int)Math.floor(x) >> 4, (int)Math.floor(z) >> 4);
      }
   }

   public static boolean formation(String id) {
      return id.length() <= 32 && id.chars().allMatch(c -> c >= 'a' && c <= 'z' || c == '_');
   }
}
