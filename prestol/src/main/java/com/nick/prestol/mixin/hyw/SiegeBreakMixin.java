package com.nick.prestol.mixin.hyw;

import com.nick.prestol.war.SiegeLaw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.utils.BlockBreakHandler;

/** Stones, bolts and cannonballs of siege engines break walls only where war allows it. */
@Mixin(value = BlockBreakHandler.class, remap = false)
public abstract class SiegeBreakMixin {
   @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true, require = 0)
   private void prestol$onlyAtWar(Projectile projectile, Level level, Vec3 pos, CallbackInfo ci) {
      if (!level.isClientSide() && !SiegeLaw.mayBreak(level, BlockPos.containing(pos), projectile.getOwner() == null ? null : SiegeLaw.ownerOf(projectile.getOwner()))) {
         ci.cancel();
      }
   }
}
