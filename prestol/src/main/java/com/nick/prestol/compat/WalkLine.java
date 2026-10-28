package com.nick.prestol.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Whether a soldier can walk straight to a point of his path. Hundred Years War lets a soldier jump ahead along
 * a shared group path to any node within a few blocks, without looking at what stands between: a closed door,
 * a fence gate or the corner of a wall. The soldier then walks into it and never opens the door.
 */
public final class WalkLine {
   private static final double[] HEIGHTS = {1.1, 1.6};

   private WalkLine() {
   }

   /** The furthest index in {@code (start, found]} the entity can walk to in a straight line; {@code start} if none. */
   public static int furthestClear(Entity entity, List<BlockPos> nodes, int start, int found) {
      for (int i = Math.min(found, nodes.size() - 1); i > start; i--) {
         if (clear(entity, nodes.get(i))) {
            return i;
         }
      }

      return start;
   }

   public static boolean clear(Entity entity, BlockPos node) {
      Level level = entity.level();
      Vec3 from = entity.position();
      Vec3 to = new Vec3(node.getX() + 0.5, node.getY(), node.getZ() + 0.5);
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length < 0.6) {
         return true;
      } else {
         double side = Math.max(0.0, entity.getBbWidth() / 2.0 - 0.1);
         double px = -dz / length * side;
         double pz = dx / length * side;
         int steps = Mth.ceil(length / 0.25);

         for (int s = 1; s <= steps; s++) {
            double t = (double)s / steps;
            double x = from.x + dx * t;
            double z = from.z + dz * t;
            double y = from.y + (to.y - from.y) * t;

            for (double h : HEIGHTS) {
               for (int lane = -1; lane <= 1; lane++) {
                  if (solidAt(level, x + px * lane, y + h, z + pz * lane)) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }

   private static boolean solidAt(Level level, double x, double y, double z) {
      BlockPos pos = BlockPos.containing(x, y, z);

      for (int down = 0; down <= 1; down++) {
         BlockPos p = pos.below(down);
         BlockState state = level.getBlockState(p);
         if (!state.isAir()) {
            VoxelShape shape = state.getCollisionShape(level, p);
            if (!shape.isEmpty()) {
               for (AABB box : shape.toAabbs()) {
                  if (box.move(p).contains(x, y, z)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }
}
