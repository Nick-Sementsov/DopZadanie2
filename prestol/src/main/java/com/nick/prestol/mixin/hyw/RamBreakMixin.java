package com.nick.prestol.mixin.hyw;

import com.nick.prestol.war.SiegeLaw;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.main.entity.entities.siege.BatteringRamEntity;

/** A battering ram breaks gates only where war allows it. */
@Mixin(value = BatteringRamEntity.class, remap = false)
public abstract class RamBreakMixin {
   @Inject(method = "destroyBlocks", at = @At("HEAD"), cancellable = true, require = 0)
   private void prestol$onlyAtWar(Collection<BlockPos> positions, CallbackInfo ci) {
      Entity ram = (Entity)(Object)this;
      if (!positions.isEmpty() && !ram.level().isClientSide() && !SiegeLaw.mayBreak(ram.level(), positions.iterator().next(), SiegeLaw.ownerOf(ram))) {
         ci.cancel();
      }
   }
}
