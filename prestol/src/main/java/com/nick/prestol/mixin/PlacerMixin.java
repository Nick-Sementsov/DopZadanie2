package com.nick.prestol.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import com.minecolonies.core.entity.ai.workers.util.BuildingStructureHandler;
import com.nick.prestol.Prestol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {EntityAIStructureBuilder.class},
   remap = false
)
public abstract class PlacerMixin {
   @Inject(
      method = {"setStructurePlacer"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$orderGone(BuildingStructureHandler<?, ?> handler, CallbackInfo info) {
      AbstractEntityAIBasic<?, ?> self = (AbstractEntityAIBasic<?, ?>)(Object)this;
      if (self.building instanceof BuildingBuilder hut && hut.getWorkOrder() == null) {
         Prestol.LOGGER
            .warn("Prestol: the builder of {} lost his work order while his blueprint was loading; the blueprint is dropped", hut.getPosition().toShortString());
         info.cancel();
      }
   }
}
