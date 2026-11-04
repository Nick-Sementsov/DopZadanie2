package com.nick.prestol.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.nick.prestol.work.Crews;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {AbstractEntityAIBasic.class},
   remap = false
)
public abstract class CrewWalkMixin {
   private boolean prestol$crewAtSite() {
      if ((Object)this instanceof AbstractEntityAIStructure<?, ?> ai && Crews.atSite(ai.getWorker())) {
         return true;
      }

      return false;
   }

   @Inject(
      method = {"walkToBuilding()Z"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$stayAtSite(CallbackInfoReturnable<Boolean> info) {
      if (this.prestol$crewAtSite()) {
         info.setReturnValue(true);
      }
   }

   @Inject(
      method = {"walkToWorkPos"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$pickUpAtSite(BlockPos pos, CallbackInfoReturnable<Boolean> info) {
      AbstractEntityAIBasic<?, ?> self = (AbstractEntityAIBasic<?, ?>)(Object)this;
      if (pos != null
         && self.building != null
         && (pos.equals(self.building.getPosition()) || self.building.getContainers().contains(pos))
         && this.prestol$crewAtSite()) {
         info.setReturnValue(true);
      }
   }
}
