package com.nick.prestol.land;

import com.minecolonies.api.util.ColonyUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

public final class Territory extends SavedData {
   private static final String NAME = "prestol_territory";
   private final Map<String, Long2IntOpenHashMap> claims = new HashMap<>();
   private int version;

   public static Territory get(MinecraftServer server) {
      return (Territory)server.overworld().getDataStorage().computeIfAbsent(Territory::load, Territory::new, "prestol_territory");
   }

   public int version() {
      return this.version;
   }

   public Map<String, Long2IntOpenHashMap> all() {
      return this.claims;
   }

   public int owner(String dim, long chunk) {
      Long2IntOpenHashMap m = this.claims.get(dim);
      return m == null ? 0 : m.getOrDefault(chunk, 0);
   }

   public void set(String dim, long chunk, int colony) {
      Long2IntOpenHashMap m = this.claims.computeIfAbsent(dim, k -> new Long2IntOpenHashMap());
      if (colony <= 0) {
         if (m.containsKey(chunk)) {
            m.remove(chunk);
            this.changed();
         }
      } else if (m.put(chunk, colony) != colony) {
         this.changed();
      }
   }

   public void forgetColony(String dim, int colony) {
      Long2IntOpenHashMap m = this.claims.get(dim);
      if (m != null && m.values().removeIf(v -> v == colony)) {
         this.changed();
      }
   }

   public void record(ServerLevel level, LevelChunk chunk) {
      this.set(level.dimension().location().toString(), chunk.getPos().toLong(), ColonyUtils.getOwningColony(chunk));
   }

   public static int ownerAt(ServerLevel level, int cx, int cz) {
      return level.hasChunk(cx, cz)
         ? ColonyUtils.getOwningColony(level.getChunk(cx, cz))
         : get(level.getServer()).owner(level.dimension().location().toString(), ChunkPos.asLong(cx, cz));
   }

   private void changed() {
      this.version++;
      this.setDirty();
   }

   public CompoundTag save(CompoundTag tag) {
      CompoundTag dims = new CompoundTag();
      this.claims.forEach((dim, map) -> {
         long[] chunks = new long[map.size()];
         long[] cols = new long[map.size()];
         int i = 0;

         for (ObjectIterator t = map.long2IntEntrySet().iterator(); t.hasNext(); i++) {
            Entry e = (Entry)t.next();
            chunks[i] = e.getLongKey();
            cols[i] = (long)e.getIntValue();
         }

         CompoundTag t = new CompoundTag();
         t.put("chunks", new LongArrayTag(chunks));
         t.put("colonies", new LongArrayTag(cols));
         dims.put(dim, t);
      });
      tag.put("dims", dims);
      return tag;
   }

   public static Territory load(CompoundTag tag) {
      Territory t = new Territory();
      CompoundTag dims = tag.getCompound("dims");

      for (String dim : dims.getAllKeys()) {
         CompoundTag d = dims.getCompound(dim);
         long[] chunks = d.getLongArray("chunks");
         long[] cols = d.getLongArray("colonies");
         Long2IntOpenHashMap m = new Long2IntOpenHashMap();

         for (int i = 0; i < Math.min(chunks.length, cols.length); i++) {
            m.put(chunks[i], (int)cols[i]);
         }

         t.claims.put(dim, m);
      }

      return t;
   }
}
