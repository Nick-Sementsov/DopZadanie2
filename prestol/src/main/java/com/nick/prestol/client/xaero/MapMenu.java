package com.nick.prestol.client.xaero;

import com.nick.prestol.client.PrestolClient;
import com.nick.prestol.net.Net;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public final class MapMenu {
   private MapMenu() {
   }

   public static void addOptions(Screen map, ArrayList<RightClickOption> options, MapTileSelection selection, ResourceKey<Level> dim, int x, int z) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && map instanceof IRightClickableElement target) {
         boolean here = dim == null || dim == mc.level.dimension();
         if (here) {
            final String rect = selection != null
               ? selection.getLeft() + " " + selection.getTop() + " " + selection.getRight() + " " + selection.getBottom()
               : (x >> 4) + " " + (z >> 4) + " " + (x >> 4) + " " + (z >> 4);
            options.add(new RightClickOption("prestol.map.take", options.size(), target) {
               public void onAction(Screen screen) {
                  Net.order("land take " + rect);
               }
            });
            options.add(new RightClickOption("prestol.map.free", options.size(), target) {
               public void onAction(Screen screen) {
                  Net.order("land free " + rect);
               }
            });
         }

         options.add(new RightClickOption("prestol.map.book", options.size(), target) {
            public void onAction(Screen screen) {
               PrestolClient.openBook("lands");
            }
         });
      }
   }

   public static boolean openWorldMap(Screen parent) {
      Minecraft mc = Minecraft.getInstance();
      WorldMapSession session = WorldMapSession.getCurrentSession();
      if (session != null && session.getMapProcessor() != null && mc.player != null) {
         try {
            Screen map = (Screen)Class.forName("xaero.map.gui.GuiMap")
               .getConstructor(Screen.class, Screen.class, MapProcessor.class, Entity.class)
               .newInstance(parent, null, session.getMapProcessor(), mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player);
            mc.setScreen(map);
            return true;
         } catch (RuntimeException | ReflectiveOperationException var4) {
            return false;
         }
      } else {
         return false;
      }
   }
}
