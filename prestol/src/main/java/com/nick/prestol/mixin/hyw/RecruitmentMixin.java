package com.nick.prestol.mixin.hyw;

import com.nick.prestol.compat.HywRules;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.recruitment.RecruitmentCategory;
import ydmsama.hundred_years_war.main.recruitment.data.RecruitmentConfig;

/** Hundred Years War recruits warriors only: the worker tab and its units are gone. */
@Mixin(value = ydmsama.hundred_years_war.main.recruitment.RecruitmentConfigManager.class, remap = false)
public abstract class RecruitmentMixin {
   @Inject(method = "getUnitTypesForCategory", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$noWorkers(RecruitmentConfig config, String categoryId, CallbackInfoReturnable<List<RecruitmentConfig.UnitTypeConfig>> info) {
      if (HywRules.noWorkers() && RecruitmentCategory.WORKER.getId().equals(categoryId)) {
         info.setReturnValue(new ArrayList<>());
      }
   }

   @Inject(method = "getAllCategories", at = @At("RETURN"), cancellable = true, require = 0)
   private static void prestol$noWorkerTab(CallbackInfoReturnable<List<RecruitmentCategory>> info) {
      if (HywRules.noWorkers() && info.getReturnValue() != null && info.getReturnValue().contains(RecruitmentCategory.WORKER)) {
         List<RecruitmentCategory> out = new ArrayList<>(info.getReturnValue());
         out.remove(RecruitmentCategory.WORKER);
         info.setReturnValue(out);
      }
   }
}
