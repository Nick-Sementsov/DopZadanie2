package com.nick.prestol.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.modules.AbstractAssignedCitizenModule;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.nick.prestol.army.Army;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {AbstractAssignedCitizenModule.class},
   remap = false
)
public abstract class GuardHireMixin {
   @Inject(
      method = {"assignCitizen"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$noGuards(ICitizenData citizen, CallbackInfoReturnable<Boolean> info) {
      if (Army.garrisons() && ((Object)this instanceof GuardBuildingModule || (Object)this instanceof WorkerBuildingModule w && Army.isTraining(w))) {
         info.setReturnValue(false);
      }
   }
}
