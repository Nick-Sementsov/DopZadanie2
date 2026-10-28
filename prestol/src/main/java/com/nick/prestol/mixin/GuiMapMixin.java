package com.nick.prestol.mixin;

import com.nick.prestol.client.xaero.MapMenu;
import java.util.ArrayList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

@Mixin(
   targets = {"xaero.map.gui.GuiMap"},
   remap = false
)
public abstract class GuiMapMixin {
   @Shadow(
      remap = false
   )
   private MapTileSelection mapTileSelection;
   @Shadow(
      remap = false
   )
   private ResourceKey<Level> rightClickDim;
   @Shadow(
      remap = false
   )
   private int rightClickX;
   @Shadow(
      remap = false
   )
   private int rightClickZ;

   @Inject(
      method = {"getRightClickOptions"},
      at = {@At("RETURN")},
      remap = false,
      require = 0
   )
   private void prestol$options(CallbackInfoReturnable<ArrayList<RightClickOption>> info) {
      try {
         MapMenu.addOptions(
            (Screen)(Object)this, (ArrayList<RightClickOption>)info.getReturnValue(), this.mapTileSelection, this.rightClickDim, this.rightClickX, this.rightClickZ
         );
      } catch (LinkageError | RuntimeException var3) {
      }
   }
}
