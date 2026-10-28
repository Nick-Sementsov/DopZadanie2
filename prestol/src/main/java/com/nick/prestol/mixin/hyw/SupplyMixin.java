package com.nick.prestol.mixin.hyw;

import com.nick.prestol.army.Provisions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.entity.utils.BaseCombatSupport;
import ydmsama.hundred_years_war.main.supply.SupplyManager;

/** With Prestol provisions on, soldiers are fed by the colony and the HYW supply chain stays idle. */
@Mixin(value = SupplyManager.class, remap = false)
public abstract class SupplyMixin {
   @Inject(method = "tryEstablishSupply", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$colonyFeedsThem(BaseCombatSupport entity, Level level, CallbackInfo info) {
      if (Provisions.on()) {
         info.cancel();
      }
   }

   @Inject(method = "hasActiveSupply", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$alwaysSupplied(BaseCombatSupport entity, CallbackInfoReturnable<Boolean> info) {
      if (Provisions.on()) {
         info.setReturnValue(true);
      }
   }
}
