package com.nick.prestol.mixin;

import com.minecolonies.api.colony.ColonyState;
import com.minecolonies.api.colony.IColony;
import com.nick.prestol.work.OfflineWork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.minecolonies.core.colony.Colony"},
   remap = false
)
public abstract class ColonyMixin {
   @Inject(
      method = {"updateState"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$keepWorking(CallbackInfoReturnable<ColonyState> info) {
      IColony colony = (IColony)this;
      if (info.getReturnValue() != ColonyState.ACTIVE && OfflineWork.keepsAwake(colony)) {
         colony.markDirty();
         info.setReturnValue(ColonyState.ACTIVE);
      }
   }
}
