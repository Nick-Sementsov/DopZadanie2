package com.nick.prestol.land;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class LandData extends SavedData {
   private static final String NAME = "prestol_lands";
   private final Map<String, Set<Long>> drawn = new HashMap<>();

   public static LandData get(MinecraftServer server) {
      return (LandData)server.overworld().getDataStorage().computeIfAbsent(LandData::load, LandData::new, "prestol_lands");
   }

   public Set<Long> of(String settlement) {
      return this.drawn.computeIfAbsent(settlement, k -> new HashSet<>());
   }

   public Map<String, Set<Long>> all() {
      return this.drawn;
   }

   public void forget(String settlement) {
      if (this.drawn.remove(settlement) != null) {
         this.setDirty();
      }
   }

   public CompoundTag save(CompoundTag tag) {
      CompoundTag all = new CompoundTag();
      this.drawn.forEach((k, set) -> {
         if (!set.isEmpty()) {
            all.put(k, new LongArrayTag(set.stream().mapToLong(Long::longValue).toArray()));
         }
      });
      tag.put("drawn", all);
      return tag;
   }

   public static LandData load(CompoundTag tag) {
      LandData d = new LandData();
      CompoundTag all = tag.getCompound("drawn");

      for (String k : all.getAllKeys()) {
         Set<Long> set = new HashSet<>();

         for (long c : all.getLongArray(k)) {
            set.add(c);
         }

         d.drawn.put(k, set);
      }

      return d;
   }
}
