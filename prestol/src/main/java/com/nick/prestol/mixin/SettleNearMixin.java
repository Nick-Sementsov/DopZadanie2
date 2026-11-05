package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.realm.Settling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A town hall may be placed nearer to the settlements of its founder and of his realm than MineColonies allows (see
 * {@link Settling}); the founding window and the founding itself both ask.
 */
@Mixin(
   targets = {"com.minecolonies.core.network.messages.server.GetColonyInfoMessage", "com.minecolonies.core.network.messages.server.CreateColonyMessage"},
   remap = false
)
public abstract class SettleNearMixin {
   @Unique
   private static final ThreadLocal<ServerPlayer> prestol$founder = new ThreadLocal<>();

   @Inject(
      method = {"onExecute"},
      at = {@At("HEAD")},
      remap = false,
      require = 0
   )
   private void prestol$who(NetworkEvent.Context ctx, boolean server, CallbackInfo info) {
      prestol$founder.set(ctx.getSender());
   }

   @Redirect(
      method = {"onExecute"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/minecolonies/api/colony/IColonyManager;isFarEnoughFromColonies(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"
      ),
      remap = false,
      require = 0
   )
   private boolean prestol$nearOwn(IColonyManager manager, Level level, BlockPos pos) {
      return manager.isFarEnoughFromColonies(level, pos) || Settling.mayFoundNearOwn(prestol$founder.get(), level, pos);
   }
}
