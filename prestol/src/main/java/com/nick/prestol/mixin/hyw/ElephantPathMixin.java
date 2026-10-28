package com.nick.prestol.mixin.hyw;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.compat.vehicles.elephant.WarElephantEntity;
import ydmsama.hundred_years_war.main.entity.navigation.LadderAwareNodeEvaluator;

/**
 * The war elephant is three blocks wide, so Hundred Years War plans its path with a 4x4 footprint that must
 * stand on flat ground at every step; any bump or dip under that square rules the step out, and on uneven
 * land the elephant finds no path at all. It plans with a 2x2 footprint instead: its 1.5 block step climbs
 * the rest.
 */
@Mixin(LadderAwareNodeEvaluator.class)
public abstract class ElephantPathMixin extends WalkNodeEvaluator {
   @Inject(method = "prepare", at = @At("TAIL"), require = 0)
   private void prestol$narrowElephant(PathNavigationRegion region, Mob mob, CallbackInfo info) {
      if (mob instanceof WarElephantEntity && this.entityWidth > 2) {
         this.entityWidth = 2;
         this.entityDepth = 2;
      }
   }
}
