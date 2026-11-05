package com.nick.prestol.mixin;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.mods.gui.Waypoint;

@Mixin(
   targets = {"xaero.map.mods.SupportXaeroMinimap"},
   remap = false
)
public abstract class MapWaypointsMixin {
   @Inject(
      method = {"convertWaypoints"},
      at = {@At("RETURN")},
      remap = false,
      require = 0
   )
   private void prestol$noSettlementWaypoints(double division, CallbackInfoReturnable<ArrayList<Waypoint>> info) {
      ArrayList<Waypoint> list = (ArrayList<Waypoint>)info.getReturnValue();
      if (list != null) {
         list.removeIf(w -> w.getThirdPartyOrigin() != null && "prestol".equals(w.getThirdPartyOrigin().getNamespace()));
      }
   }
}
