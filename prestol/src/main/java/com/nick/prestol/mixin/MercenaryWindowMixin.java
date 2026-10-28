package com.nick.prestol.mixin;

import com.ldtteam.blockui.controls.Text;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.townhall.WindowTownHallMercenary;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {WindowTownHallMercenary.class},
   remap = false
)
public abstract class MercenaryWindowMixin {
   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")},
      remap = false,
      require = 0
   )
   private void prestol$story(IColonyView colony, CallbackInfo info) {
      Text text = (Text)((WindowTownHallMercenary)(Object)this).findPaneOfTypeByID("text", Text.class);
      if (text != null) {
         text.setText(Component.translatable("prestol.mercenary.story"));
      }
   }
}
