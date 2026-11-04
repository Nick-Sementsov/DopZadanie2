package com.nick.prestol.realm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;

public final class Holding {
   public int tier = -1;
   public UUID lastOwner;
   public UUID formerOwner;
   public String formerOwnerName = "";
   public UUID formerRealm;
   public long capturedAt = -1L;
   public long immuneUntil = -1L;
   public boolean enslaved;
   public int unhappyDays;
   public long lastDay = -1L;
   public final List<BlockPos> mast = new ArrayList<>();
   public String mastFlag = "";
   public final Set<UUID> hostile = new LinkedHashSet<>();
   /** The colony rank id each player had before the war made him hostile; -1 = he was not on the list. */
   public final Map<UUID, Integer> hostileWas = new HashMap<>();

   public boolean isEmpty() {
      return this.tier < 0
         && this.lastOwner == null
         && this.formerOwner == null
         && !this.enslaved
         && this.mast.isEmpty()
         && this.hostile.isEmpty()
         && this.unhappyDays == 0;
   }

   CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putInt("tier", this.tier);
      if (this.lastOwner != null) {
         tag.putUUID("lastOwner", this.lastOwner);
      }

      if (this.formerOwner != null) {
         tag.putUUID("formerOwner", this.formerOwner);
         tag.putString("formerOwnerName", this.formerOwnerName);
      }

      if (this.formerRealm != null) {
         tag.putUUID("formerRealm", this.formerRealm);
      }

      tag.putLong("capturedAt", this.capturedAt);
      tag.putLong("immuneUntil", this.immuneUntil);
      tag.putBoolean("enslaved", this.enslaved);
      tag.putInt("unhappyDays", this.unhappyDays);
      tag.putLong("lastDay", this.lastDay);
      ListTag m = new ListTag();

      for (BlockPos p : this.mast) {
         m.add(LongTag.valueOf(p.asLong()));
      }

      tag.put("mast", m);
      tag.putString("mastFlag", this.mastFlag);
      ListTag h = new ListTag();

      for (UUID u : this.hostile) {
         h.add(StringTag.valueOf(u.toString()));
      }

      tag.put("hostile", h);
      CompoundTag was = new CompoundTag();
      this.hostileWas.forEach((u, r) -> was.putInt(u.toString(), r));
      tag.put("hostileWas", was);
      return tag;
   }

   static Holding load(CompoundTag tag) {
      Holding h = new Holding();
      h.tier = tag.getInt("tier");
      h.lastOwner = tag.hasUUID("lastOwner") ? tag.getUUID("lastOwner") : null;
      h.formerOwner = tag.hasUUID("formerOwner") ? tag.getUUID("formerOwner") : null;
      h.formerOwnerName = tag.getString("formerOwnerName");
      h.formerRealm = tag.hasUUID("formerRealm") ? tag.getUUID("formerRealm") : null;
      h.capturedAt = tag.getLong("capturedAt");
      h.immuneUntil = tag.getLong("immuneUntil");
      h.enslaved = tag.getBoolean("enslaved");
      h.unhappyDays = tag.getInt("unhappyDays");
      h.lastDay = tag.getLong("lastDay");
      ListTag m = tag.getList("mast", 4);

      for (int i = 0; i < m.size(); i++) {
         h.mast.add(BlockPos.of(((LongTag)m.get(i)).getAsLong()));
      }

      h.mastFlag = tag.getString("mastFlag");
      ListTag hs = tag.getList("hostile", 8);

      for (int i = 0; i < hs.size(); i++) {
         try {
            h.hostile.add(UUID.fromString(hs.getString(i)));
         } catch (IllegalArgumentException var6) {
         }
      }

      CompoundTag was = tag.getCompound("hostileWas");

      for (String k : was.getAllKeys()) {
         try {
            h.hostileWas.put(UUID.fromString(k), was.getInt(k));
         } catch (IllegalArgumentException var7) {
         }
      }

      return h;
   }
}
