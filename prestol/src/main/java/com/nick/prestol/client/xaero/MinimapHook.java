package com.nick.prestol.client.xaero;

import com.nick.prestol.Prestol;
import com.nick.prestol.client.ClientMap;
import com.nick.prestol.map.MapSnapshot;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.write.MinimapWriter;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

public final class MinimapHook {
   private static final ResourceLocation ORIGIN = new ResourceLocation("prestol", "settlements");
   private static final MiniHighlighter HIGHLIGHTER = new MiniHighlighter();
   private static boolean highlightBroken;
   private static boolean waypointsBroken;
   private static int lastHighlight = -1;
   private static Object lastWriter;
   private static int lastWaypoints = -1;
   private static MinimapWorldContainer lastContainer;

   private MinimapHook() {
   }

   public static void tick() {
      MinimapSession session;
      try {
         session = (MinimapSession)BuiltInHudModules.MINIMAP.getCurrentSession();
      } catch (Throwable var4) {
         highlightBroken = true;
         waypointsBroken = true;
         Prestol.LOGGER.error("Prestol: Xaero's Minimap integration switched off (incompatible version?)", var4);
         return;
      }

      if (session == null) {
         lastContainer = null;
      } else {
         if (!highlightBroken) {
            try {
               highlights(session);
            } catch (Throwable var3) {
               highlightBroken = true;
               Prestol.LOGGER.error("Prestol: minimap land colours switched off", var3);
            }
         }

         if (!waypointsBroken) {
            try {
               waypoints(session);
            } catch (Throwable var2) {
               waypointsBroken = true;
               Prestol.LOGGER.error("Prestol: minimap settlement waypoints switched off", var2);
            }
         }
      }
   }

   private static void highlights(MinimapSession session) throws ReflectiveOperationException {
      if (session.getProcessor() != null) {
         MinimapWriter writer = session.getProcessor().getMinimapWriter();
         if (writer != null) {
            Field rf = MinimapWriter.class.getDeclaredField("highlighterRegistry");
            rf.setAccessible(true);
            HighlighterRegistry registry = (HighlighterRegistry)rf.get(writer);
            if (registry != null) {
               boolean refresh = writer != lastWriter || ClientMap.version() != lastHighlight;
               if (!registry.getHighlighters().contains(HIGHLIGHTER)) {
                  Field lf = HighlighterRegistry.class.getDeclaredField("highlighters");
                  lf.setAccessible(true);
                  List<Object> list = new ArrayList<>((List)lf.get(registry));
                  list.add(HIGHLIGHTER);
                  lf.set(registry, Collections.unmodifiableList(list));
                  refresh = true;
               }

               if (refresh && writer.getDimensionHighlightHandler() != null) {
                  writer.getDimensionHighlightHandler().requestRefresh();
                  lastWriter = writer;
                  lastHighlight = ClientMap.version();
               }
            }
         }
      }
   }

   private static void waypoints(MinimapSession session) {
      MinimapWorld world = session.getWorldManager() == null ? null : session.getWorldManager().getCurrentWorld();
      MinimapWorldContainer container = world == null ? null : world.getContainer();
      if (container != lastContainer || ClientMap.version() != lastWaypoints) {
         if (lastContainer != null && lastContainer != container) {
            lastContainer.getThirdPartyWaypointManager().get(ORIGIN).clear();
         }

         lastContainer = container;
         lastWaypoints = ClientMap.version();
         if (container != null) {
            ThirdPartyWaypoints points = container.getThirdPartyWaypointManager().get(ORIGIN);
            points.clear();
            if (world.getDimId() != null) {
               String dim = world.getDimId().location().toString();

               for (MapSnapshot.SettlementInfo s : ClientMap.settlements()) {
                  if (s.dim().equals(dim) && s.realm() != null) {
                     String role = I18n.get(
                        s.capital() ? "prestol.role.capital" : "prestol.tier." + new String[]{"hamlet", "village", "town"}[Math.max(0, Math.min(2, s.tier()))],
                        new Object[0]
                     );
                     String initials = s.capital() ? "♛" : role.substring(0, 1);
                     points.add(s.key(), new Waypoint(s.x(), s.y() + 2, s.z(), role + " «" + s.name() + "»", initials, nearest(ClientMap.colorOf(s))));
                  }
               }
            }
         }
      }
   }

   private static int nearest(int rgb) {
      int best = 15;
      long bestD = Long.MAX_VALUE;

      for (ChatFormatting f : ChatFormatting.values()) {
         if (f.isColor() && f.getColor() != null) {
            int c = f.getColor();
            long r = (long)((c >> 16 & 0xFF) - (rgb >> 16 & 0xFF));
            long g = (long)((c >> 8 & 0xFF) - (rgb >> 8 & 0xFF));
            long b = (long)((c & 0xFF) - (rgb & 0xFF));
            long d = r * r + g * g + b * b;
            if (d < bestD) {
               bestD = d;
               best = f.getId();
            }
         }
      }

      return best;
   }
}
