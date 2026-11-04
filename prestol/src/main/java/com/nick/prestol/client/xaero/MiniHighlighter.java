package com.nick.prestol.client.xaero;

import com.nick.prestol.client.ClientMap;
import com.nick.prestol.map.MapSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

public class MiniHighlighter extends ChunkHighlighter {
   private final int[] colors = new int[5];

   public MiniHighlighter() {
      super(true);
   }

   private static String dim(ResourceKey<Level> key) {
      return key.location().toString();
   }

   public boolean regionHasHighlights(ResourceKey<Level> dim, int rx, int rz) {
      return ClientMap.regionHas(dim(dim), rx, rz);
   }

   public boolean chunkIsHighlit(ResourceKey<Level> dim, int cx, int cz) {
      return ClientMap.at(dim(dim), cx, cz) != null;
   }

   protected int[] getColors(ResourceKey<Level> dim, int cx, int cz) {
      return ClientMap.chunkColors(dim(dim), cx, cz, this.colors) ? this.colors : null;
   }

   public void addChunkHighlightTooltips(InfoDisplayCompiler out, ResourceKey<Level> dim, int cx, int cz, int width) {
      MapSnapshot.SettlementInfo s = ClientMap.at(dim(dim), cx, cz);
      if (s != null) {
         out.addLine(ClientMap.describe(s));
      }
   }
}
