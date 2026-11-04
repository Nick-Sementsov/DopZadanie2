package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.ColonyUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"com.minecolonies.core.entity.ai.workers.util.Tree"},
   remap = false
)
public abstract class TreeMixin {
   @Inject(
      method = {"checkIfInColony"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private static void prestol$freeLand(BlockPos pos, IColony colony, LevelReader world, boolean allowInsideBuilding, CallbackInfoReturnable<Boolean> info) {
      if (allowInsideBuilding && colony != null && world != null) {
         if (world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk loaded) {
            int owner = ColonyUtils.getOwningColony(loaded);
            if (owner == 0 || owner == colony.getID()) {
               info.setReturnValue(true);
            }
         }
      }
   }
}
