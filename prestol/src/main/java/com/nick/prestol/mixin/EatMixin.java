package com.nick.prestol.mixin;

import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.nick.prestol.work.WorkerFixes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {EntityAIEatTask.class},
   remap = false
)
public abstract class EatMixin {
   @Shadow(
      remap = false
   )
   @Final
   private EntityCitizen citizen;

   @Inject(
      method = {"searchRestaurant"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$foodWithoutRestaurant(CallbackInfoReturnable<EatingState> info) {
      EatingState next = WorkerFixes.foodWithoutRestaurant(this.citizen);
      if (next != null) {
         info.setReturnValue(next);
      }
   }
}
