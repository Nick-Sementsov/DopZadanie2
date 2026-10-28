package com.nick.prestol.mixin.hyw;

import com.nick.prestol.compat.CaptainSafety;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.compat.vehicles.captain.CaptainExt;

/** The same limits for bombardment points and dragged formation lines on the command map. */
@Mixin(value = CaptainExt.class, remap = false)
public abstract class CaptainExtMixin {
   @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$saneBombard(ServerPlayer player, CaptainExt.Order order, CallbackInfo info) {
      if (order.kind() == CaptainExt.BOMBARD && !CaptainSafety.near(player, order.x(), order.z())) {
         player.displayClientMessage(Component.translatable("prestol.army.order_too_far"), true);
         info.cancel();
      }
   }

   @Inject(method = "place", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$sanePlace(ServerPlayer player, CaptainExt.Place place, CallbackInfo info) {
      if (!CaptainSafety.near(player, place.sx(), place.sz()) || !CaptainSafety.near(player, place.ex(), place.ez())) {
         player.displayClientMessage(Component.translatable("prestol.army.order_too_far"), true);
         info.cancel();
      }
   }
}
