package com.nick.prestol.mixin.hyw;

import com.nick.prestol.army.Morale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;

/** A soldier whose spirit broke picks no fights: with no target his own AI takes him back to his post or his lord. */
@Mixin(value = BaseCombatEntity.class, remap = false)
public abstract class RoutTargetMixin {
   @Inject(method = "isValidTarget", at = @At("HEAD"), cancellable = true, require = 0)
   private void prestol$routedPicksNoFight(LivingEntity potentialTarget, CallbackInfoReturnable<Boolean> cir) {
      if (Morale.routed((Entity)(Object)this)) {
         cir.setReturnValue(false);
      }
   }
}
