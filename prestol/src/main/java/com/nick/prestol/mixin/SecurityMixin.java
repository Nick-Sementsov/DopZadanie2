package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;
import com.nick.prestol.army.Army;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {CitizenHappinessHandler.class},
   remap = false
)
public abstract class SecurityMixin {
   @Inject(
      method = {"getGuardFactor"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private static void prestol$garrisons(IColony colony, CallbackInfoReturnable<Double> info) {
      if (Army.garrisons()) {
         int others = colony.getCitizenManager().getCitizens().size();
         info.setReturnValue(Math.min(2.0, (1.0 + (double)Army.strength(colony)) / ((1.0 + (double)others) * 2.0 / 3.0)));
      }
   }
}
