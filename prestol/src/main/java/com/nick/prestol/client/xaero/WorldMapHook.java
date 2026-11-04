package com.nick.prestol.client.xaero;

import com.nick.prestol.Prestol;
import com.nick.prestol.client.ClientMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

public final class WorldMapHook {
   private static final WorldHighlighter HIGHLIGHTER = new WorldHighlighter();
   private static boolean flagsAdded;
   private static boolean broken;
   private static int lastVersion = -1;
   private static Object lastProcessor;

   private WorldMapHook() {
   }

   public static void tick() {
      if (!broken) {
         try {
            if (!flagsAdded && WorldMap.mapElementRenderHandler != null) {
               WorldMap.mapElementRenderHandler.add(FlagRenderer.create());
               flagsAdded = true;
            }

            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null) {
               return;
            }

            MapProcessor processor = session.getMapProcessor();
            if (processor == null || processor.getHighlighterRegistry() == null) {
               return;
            }

            HighlighterRegistry registry = processor.getHighlighterRegistry();
            boolean refresh = processor != lastProcessor || ClientMap.version() != lastVersion;
            if (!registry.getHighlighters().contains(HIGHLIGHTER)) {
               inject(registry);
               refresh = true;
            }

            if (refresh) {
               lastProcessor = processor;
               lastVersion = ClientMap.version();
               MapWorld world = processor.getMapWorld();
               if (world != null) {
                  for (MapDimension d : world.getDimensionsList()) {
                     if (d != null && d.getHighlightHandler() != null) {
                        d.getHighlightHandler().clearCachedHashes();
                     }
                  }
               }
            }
         } catch (Throwable var7) {
            broken = true;
            Prestol.LOGGER.error("Prestol: Xaero's World Map integration switched off (incompatible version?)", var7);
         }
      }
   }

   private static void inject(HighlighterRegistry registry) throws ReflectiveOperationException {
      Field f = HighlighterRegistry.class.getDeclaredField("highlighters");
      f.setAccessible(true);
      List<Object> list = new ArrayList<>((List)f.get(registry));
      list.add(HIGHLIGHTER);
      f.set(registry, Collections.unmodifiableList(list));
   }
}
