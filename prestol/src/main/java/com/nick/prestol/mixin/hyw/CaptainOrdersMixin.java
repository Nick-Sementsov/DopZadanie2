package com.nick.prestol.mixin.hyw;

import com.nick.prestol.compat.CaptainSafety;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.compat.vehicles.captain.CaptainLogic;

/**
 * Captain orders arrive from the client with any coordinates, and the server looks up the ground there,
 * which loads or even generates that chunk. Targets must be near the commander and in a loaded chunk,
 * and a formation name must be a plain short id.
 */
@Mixin(value = CaptainLogic.class, remap = false)
public abstract class CaptainOrdersMixin {
   @Inject(method = "order", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$sane(ServerPlayer player, int captain, int kind, double x, double z, String formation, CallbackInfo info) {
      boolean usesPoint = kind == 0 || kind == 1 || kind == 8 && (x != 0.0 || z != 0.0);
      if (usesPoint && !CaptainSafety.near(player, x, z)) {
         player.displayClientMessage(Component.translatable("prestol.army.order_too_far"), true);
         info.cancel();
      } else if (kind == 4 && formation != null && !CaptainSafety.formation(formation)) {
         info.cancel();
      }
   }
}
