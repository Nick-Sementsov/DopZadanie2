package com.nick.prestol.mixin.hyw;

import com.nick.prestol.PrestolConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ydmsama.hundred_years_war.extras.flags.ColonyFlags;

/** Prestol raises the flags over town halls; HYW's own masts would put a second one next to it. */
@Mixin(value = ColonyFlags.class, remap = false)
public abstract class ColonyFlagsMixin {
   @Inject(method = "poll", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prestol$prestolRaisesFlags(MinecraftServer server, CallbackInfo info) {
      try {
         if (PrestolConfig.FLAGS_OVER_HALLS.get()) {
            info.cancel();
         }
      } catch (IllegalStateException var3) {
      }
   }
}
