package com.nick.prestol.client.xaero;

import com.nick.prestol.client.ClientMap;
import com.nick.prestol.map.MapSnapshot;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

public class WorldHighlighter extends ChunkHighlighter {
   private final int[] colors = new int[5];

   public WorldHighlighter() {
      super(true);
   }

   private static String dim(ResourceKey<Level> key) {
      return key.location().toString();
   }

   public int calculateRegionHash(ResourceKey<Level> dim, int rx, int rz) {
      return ClientMap.regionHash(dim(dim), rx, rz);
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

   public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dim, int cx, int cz) {
      MapSnapshot.SettlementInfo s = ClientMap.at(dim(dim), cx, cz);
      return s == null ? null : ClientMap.describe(s);
   }

   public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dim, int cx, int cz) {
      return null;
   }

   public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dim, int x, int y, int z) {
      MapSnapshot.SettlementInfo s = ClientMap.at(dim(dim), x >> 4, z >> 4);
      if (s != null) {
         list.add(ClientMap.describe(s));
      }
   }
}
