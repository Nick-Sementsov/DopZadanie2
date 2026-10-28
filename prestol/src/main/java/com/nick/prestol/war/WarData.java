package com.nick.prestol.war;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Keeps running sieges, the victors' pending choices and locked loot chests across restarts.
 * The live state stays in {@link Sieges} and {@link Conquest}; this only writes it out with the world.
 */
public final class WarData extends SavedData {
   private static final String NAME = "prestol_war";
   private ListTag sieges = new ListTag();
   private ListTag pending = new ListTag();
   private ListTag loot = new ListTag();

   public static WarData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(WarData::load, WarData::new, NAME);
   }

   /** Puts the saved sieges, choices and loot back into play; call once when the server has started. */
   public static void restore(MinecraftServer server) {
      WarData data = get(server);
      Sieges.restore(data.sieges);
      Conquest.restore(data.pending, data.loot);
      data.sieges = new ListTag();
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
      return tag;
   }

   private static WarData load(CompoundTag tag) {
      WarData d = new WarData();
      d.sieges = tag.getList("sieges", 10);
      d.pending = tag.getList("pending", 10);
      d.loot = tag.getList("loot", 10);
      return d;
   }
}
