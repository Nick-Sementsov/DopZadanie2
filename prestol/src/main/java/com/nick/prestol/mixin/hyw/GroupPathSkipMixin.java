package com.nick.prestol.mixin.hyw;

import com.nick.prestol.compat.WalkLine;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/**
 * A soldier on a shared group path may jump ahead only to a node he can walk to in a straight line: never past
 * a closed door, a fence gate or the corner of a wall (see {@link WalkLine}).
 */
@Mixin(targets = "ydmsama.hundred_years_war.main.entity.pathing.GroupPathingManager$GroupPathGroup", remap = false)
public abstract class GroupPathSkipMixin {
   @Inject(method = "findFurthestNearbyForwardNodeIndex", at = @At("RETURN"), cancellable = true, require = 0)
   private void prestol$noSkipThroughWalls(
      BaseCombatEntity entity, @Coerce Object path, int startIndex, int window, double range, CallbackInfoReturnable<Integer> cir
   ) {
      int found = cir.getReturnValue();
      if (found >= 0 && entity != null && path instanceof SharedPathAccess shared) {
         List<BlockPos> nodes = shared.prestol$nodes();
         int start = Math.max(0, Math.min(startIndex, nodes.size() - 1));
         if (found > start) {
            int clear = WalkLine.furthestClear(entity, nodes, start, found);
            if (clear != found) {
               cir.setReturnValue(clear);
            }
         }
      }
   }
}
