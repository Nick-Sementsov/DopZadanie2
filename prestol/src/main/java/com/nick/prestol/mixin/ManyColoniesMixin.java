package com.nick.prestol.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.PrestolConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   targets = {"com.minecolonies.core.network.messages.server.GetColonyInfoMessage", "com.minecolonies.core.network.messages.server.CreateColonyMessage"},
   remap = false
)
public abstract class ManyColoniesMixin {
   @Redirect(
      method = {"onExecute"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"
      ),
      remap = false,
      require = 0
   )
   private IColony prestol$foundAnother(IColonyManager manager, Level level, Player player) {
      boolean several;
      try {
         several = (Boolean)PrestolConfig.SEVERAL_COLONIES.get();
      } catch (IllegalStateException var6) {
         several = false;
      }

      return several ? null : manager.getIColonyByOwner(level, player);
   }
}
