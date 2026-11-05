package com.nick.prestol.army;

import com.minecolonies.api.colony.buildings.IBuilding;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.shapes.VoxelShape;

final class Spots {
   private Spots() {
   }

   static List<BlockPos> outside(ServerLevel level, IBuilding b, int count) {
      BlockPos hut = b.getPosition();
      Tuple<BlockPos, BlockPos> c = b.getCorners();
      if (c != null && c.getA() != null && c.getB() != null) {
         int x1 = Math.min(((BlockPos)c.getA()).getX(), ((BlockPos)c.getB()).getX());
         int x2 = Math.max(((BlockPos)c.getA()).getX(), ((BlockPos)c.getB()).getX());
         int z1 = Math.min(((BlockPos)c.getA()).getZ(), ((BlockPos)c.getB()).getZ());
         int z2 = Math.max(((BlockPos)c.getA()).getZ(), ((BlockPos)c.getB()).getZ());
         List<BlockPos> found = new ArrayList<>();

         for (int d = 1; d <= 10 && found.size() < count; d++) {
            List<BlockPos> ring = new ArrayList<>();

            for (int x = x1 - d; x <= x2 + d; x++) {
               ring.add(new BlockPos(x, 0, z1 - d));
               ring.add(new BlockPos(x, 0, z2 + d));
            }

            for (int z = z1 - d + 1; z <= z2 + d - 1; z++) {
               ring.add(new BlockPos(x1 - d, 0, z));
               ring.add(new BlockPos(x2 + d, 0, z));
            }

            ring.sort(
               Comparator.comparingDouble(
                  px -> (double)(px.getX() - hut.getX()) * (double)(px.getX() - hut.getX())
                        + (double)(px.getZ() - hut.getZ()) * (double)(px.getZ() - hut.getZ())
               )
            );

            for (BlockPos p : ring) {
               if (found.size() < count) {
                  if (level.isLoaded(p)) {
                     BlockPos at = new BlockPos(p.getX(), level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
                     if (standable(level, at) && found.stream().noneMatch(f -> f.distSqr(at) < 4.0) && open(level, at)) {
                        found.add(at);
                     }
                  }
                  continue;
               }
            }
         }

         return found.isEmpty() ? around(level, hut, count) : found;
      } else {
         return around(level, hut, count);
      }
   }

   static List<BlockPos> densify(ServerLevel level, List<BlockPos> route, int step) {
      if (route.size() < 2) {
         return route;
      } else {
         List<BlockPos> out = new ArrayList<>();

         for (int i = 0; i < route.size(); i++) {
            BlockPos a = route.get(i);
            out.add(a);
            if (i + 1 >= route.size()) {
               break;
            }

            BlockPos b = route.get(i + 1);
            double dx = (double)(b.getX() - a.getX());
            double dz = (double)(b.getZ() - a.getZ());
            int legs = (int)Math.ceil(Math.sqrt(dx * dx + dz * dz) / (double)step);

            for (int k = 1; k < legs; k++) {
               int x = (int)Math.round((double)a.getX() + dx * (double)k / (double)legs);
               int z = (int)Math.round((double)a.getZ() + dz * (double)k / (double)legs);
               BlockPos ground = new BlockPos(x, level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
               if (!standable(level, ground)) {
                  List<BlockPos> near = around(level, ground, 1);
                  if (near.isEmpty()) {
                     continue;
                  }

                  ground = near.get(0);
               }

               out.add(ground);
            }
         }

         return out;
      }
   }

   static List<BlockPos> around(ServerLevel level, BlockPos center, int count) {
      List<BlockPos> found = new ArrayList<>();

      for (int r = 1; r <= 6 && found.size() < count; r++) {
         for (int dx = -r; dx <= r && found.size() < count; dx++) {
            for (int dz = -r; dz <= r && found.size() < count; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  for (int dy = -1; dy <= 2; dy++) {
                     BlockPos p = center.offset(dx, dy, dz);
                     if (standable(level, p)) {
                        found.add(p);
                        break;
                     }
                  }
               }
            }
         }
      }

      return found;
   }

   private static boolean standable(ServerLevel level, BlockPos p) {
      if (level.isLoaded(p) && free(level, p) && free(level, p.above())) {
         VoxelShape floor = level.getBlockState(p.below()).getCollisionShape(level, p.below());
         return !floor.isEmpty() && floor.max(Axis.Y) <= 1.0;
      } else {
         return false;
      }
   }

   private static boolean open(ServerLevel level, BlockPos start) {
      int need = 48;
      Set<Long> seen = new HashSet<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      seen.add(start.asLong());
      queue.add(start);

      while (!queue.isEmpty() && seen.size() < 48) {
         BlockPos p = queue.poll();

         for (Direction d : Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
               BlockPos n = p.relative(d).above(dy);
               if (Math.abs(n.getX() - start.getX()) <= 12
                  && Math.abs(n.getZ() - start.getZ()) <= 12
                  && !seen.contains(n.asLong())
                  && (dy != 1 || free(level, p.above(2)))
                  && standable(level, n)) {
                  seen.add(n.asLong());
                  queue.add(n);
                  break;
               }
            }
         }
      }

      return seen.size() >= 48;
   }

   private static boolean free(ServerLevel level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
   }
}
