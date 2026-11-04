package com.nick.prestol.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Keeps running sieges, siege camps, peace terms and tributes, the victors' pending choices and locked loot chests
 * across restarts.
 * The live state stays in {@link Sieges} and {@link Conquest}; this only writes it out with the world.
 */
public final class WarData extends SavedData {
   private static final String NAME = "prestol_war";
   private ListTag sieges = new ListTag();
   private ListTag pending = new ListTag();
   private ListTag loot = new ListTag();
   private ListTag camps = new ListTag();
   private CompoundTag treaties = new CompoundTag();
   private CompoundTag weariness = new CompoundTag();

   public static WarData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(WarData::load, WarData::new, NAME);
   }

   /** Puts the saved sieges, choices and loot back into play; call once when the server has started. */
   public static void restore(MinecraftServer server) {
      WarData data = get(server);
      Sieges.restore(data.sieges);
      Conquest.restore(data.pending, data.loot);
      Camps.restore(data.camps);
      Treaties.restore(data.treaties);
      Weariness.restore(data.weariness);
      data.sieges = new ListTag();
      data.treaties = new CompoundTag();
      data.weariness = new CompoundTag();
      data.camps = new ListTag();
      data.pending = new ListTag();
      data.loot = new ListTag();
   }

   @Override
   public boolean isDirty() {
      // the state changes every second while a siege runs, so it is written on every world save
      return true;
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      tag.put("sieges", Sieges.saveAll());
      tag.put("pending", Conquest.savePending());
      tag.put("loot", Conquest.saveLoot());
      tag.put("camps", Camps.saveAll());
      tag.put("treaties", Treaties.save());
      tag.put("weariness", Weariness.save());
      return tag;
   }

   private static WarData load(CompoundTag tag) {
      WarData d = new WarData();
      d.sieges = tag.getList("sieges", 10);
      d.pending = tag.getList("pending", 10);
      d.loot = tag.getList("loot", 10);
      d.camps = tag.getList("camps", 10);
      d.treaties = tag.getCompound("treaties");
      d.weariness = tag.getCompound("weariness");
      return d;
   }
}
