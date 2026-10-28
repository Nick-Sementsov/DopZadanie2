package com.nick.prestol.mixin;

import com.google.gson.JsonElement;
import com.nick.prestol.compat.HywRules;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the recipes of Hundred Years War worker workstations before the recipe manager reads them. */
@Mixin(RecipeManager.class)
public abstract class RecipeFilterMixin {
   @Inject(
      method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
      at = @At("HEAD"),
      require = 0
   )
   private void prestol$noWorkerRecipes(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler, CallbackInfo info) {
      if (ModList.get() != null && ModList.get().isLoaded("hundred_years_war")) {
         HywRules.dropWorkerRecipes(recipes);
      }
   }
}
