package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.CitizenData;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {CitizenData.class},
   remap = false
)
public abstract class HomeMixin {
   @Inject(
      method = {"getHomePosition"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$sleepAtWork(CallbackInfoReturnable<BlockPos> info) {
      CitizenData self = (CitizenData)(Object)this;
      if (self.getHomeBuilding() == null && self.getColony() != null) {
         IColony colony = self.getColony();
         IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
         if (hall != null && hall.getPosition().equals(info.getReturnValue())) {
            IBuilding work = self.getWorkBuilding();
            if (work != null && work != hall && work.getBuildingLevel() > 0) {
               info.setReturnValue(work.getPosition());
            }
         }
      }
   }
}
