package com.nick.prestol.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import com.nick.prestol.compat.Troops;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A colony's guards fall upon the soldiers of a realm at war with their own (and upon bandits): MineColonies' guards
 * by themselves attack only monsters and hostile players.
 */
@Mixin(
   value = {AbstractEntityAIGuard.class},
   remap = false
)
public abstract class GuardTargetMixin {
   @Inject(
      method = {"isAttackableTarget"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private static void prestol$enemySoldiers(AbstractEntityCitizen user, LivingEntity entity, CallbackInfoReturnable<Boolean> info) {
      if (Troops.enemyOfGuard(user, entity)) {
         info.setReturnValue(true);
      }
   }
}
