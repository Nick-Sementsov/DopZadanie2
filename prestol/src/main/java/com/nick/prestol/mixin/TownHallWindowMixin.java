package com.nick.prestol.mixin;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall.View;
import com.nick.prestol.client.PrestolClient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {WindowMainPage.class},
   remap = false
)
public abstract class TownHallWindowMixin {
   private static final String BUTTON = "prestol_book";

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")},
      remap = false,
      require = 0
   )
   private void prestol$bookButton(View townHall, CallbackInfo info) {
      WindowMainPage window = (WindowMainPage)(Object)this;
      Button anchor = (Button)window.findPaneOfTypeByID("mercenaries", Button.class);
      if (anchor != null && anchor.getParent() != null) {
         ButtonImage button = new ButtonImage();
         button.setID("prestol_book");
         button.setImage(new ResourceLocation("minecolonies", "textures/gui/builderhut/builder_button_medium_large.png"), false);
         button.setSize(anchor.getWidth(), anchor.getHeight());
         button.setPosition(anchor.getX(), anchor.getY() + anchor.getHeight() + 3);
         button.setTextRenderBox(anchor.getWidth(), anchor.getHeight());
         button.setText(Component.translatable("prestol.townhall.button"));
         button.setTextColor(-16777216);
         anchor.getParent().addChild(button);
         String key = townHall.getColony().getDimension().location() + "#" + townHall.getColony().getID();
         window.registerButton("prestol_book", () -> {
            window.close();
            PrestolClient.openBook("settlement " + key);
         });
      }
   }
}
