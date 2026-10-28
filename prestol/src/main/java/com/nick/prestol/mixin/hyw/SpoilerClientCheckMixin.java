package com.nick.prestol.mixin.hyw;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ydmsama.hundred_years_war.main.spoiler.SpoilerProtectionRegistry;

/**
 * Hundred Years War tells the client from the server by trying to load the client's Minecraft class; on a
 * dedicated server every try leaves an error in the log. The loader already knows which side it runs on.
 */
@Mixin(value = SpoilerProtectionRegistry.class, remap = false)
public abstract class SpoilerClientCheckMixin {
   @Inject(method = "shouldHideFromClientState", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$serverHidesNothing(ResourceLocation itemId, CallbackInfoReturnable<Boolean> cir) {
      if (!FMLEnvironment.dist.isClient()) {
         cir.setReturnValue(false);
      }
   }
}
