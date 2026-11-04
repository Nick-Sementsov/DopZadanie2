package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.nick.prestol.war.Conquest;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSleepHandler"},
   remap = false
)
public abstract class SleepMixin {
   @Shadow(
      remap = false
   )
   @Final
   private AbstractEntityCitizen citizen;

   @Inject(
      method = {"shouldGoSleep"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$noRest(CallbackInfoReturnable<Boolean> info) {
      try {
         IColony colony = this.citizen.getCitizenData() == null ? null : this.citizen.getCitizenData().getColony();
         if (Conquest.enslaved(colony)) {
            info.setReturnValue(false);
         }
      } catch (RuntimeException var3) {
      }
   }
}
