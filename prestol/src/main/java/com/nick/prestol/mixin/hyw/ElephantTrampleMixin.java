package com.nick.prestol.mixin.hyw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.compat.vehicles.elephant.WarElephantEntity;

/** A walking war elephant that runs into foliage pushes through it instead of standing still (mobGriefing permitting). */
@Mixin(WarElephantEntity.class)
public abstract class ElephantTrampleMixin {
   @Inject(method = "customServerAiStep", at = @At("TAIL"), require = 0)
   private void prestol$trample(CallbackInfo info) {
      Mob self = (Mob)(Object)this;
      Level level = self.level();
      if (self.horizontalCollision
         && self.tickCount % 10 == 0
         && !self.getNavigation().isDone()
         && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         Direction facing = self.getDirection();
         BlockPos front = self.blockPosition().relative(facing, 2);
         Direction side = facing.getClockWise();

         for (int up = 0; up <= 3; up++) {
            for (int across = -1; across <= 1; across++) {
               BlockPos p = front.above(up).relative(side, across);
               BlockState st = level.getBlockState(p);
               if (st.is(BlockTags.LEAVES) || st.is(BlockTags.REPLACEABLE_BY_TREES) || st.is(BlockTags.CROPS)) {
                  level.destroyBlock(p, true, self);
               }
            }
         }
      }
   }
}
