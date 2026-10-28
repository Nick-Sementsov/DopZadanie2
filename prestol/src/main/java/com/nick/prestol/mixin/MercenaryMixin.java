package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.army.Mercenaries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"com.minecolonies.core.network.messages.server.colony.HireMercenaryMessage"},
   remap = false
)
public abstract class MercenaryMixin {
   @Inject(
      method = {"onExecute"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false,
      require = 0
   )
   private void prestol$hywMercenaries(Context ctx, boolean isLogicalServer, IColony colony, CallbackInfo info) {
      ServerPlayer player = ctx.getSender();
      if (isLogicalServer && player != null && colony != null && player.getServer() != null && Mercenaries.on()) {
         Mercenaries.hire(player.getServer(), player, colony);
         info.cancel();
      }
   }
}
