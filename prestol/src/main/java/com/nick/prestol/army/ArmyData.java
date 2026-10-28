package com.nick.prestol.army;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class ArmyData extends SavedData {
   private static final String NAME = "prestol_army";
   final Map<String, ArmyData.Post> posts = new HashMap<>();
   final Map<UUID, ArmyData.Levy> militia = new HashMap<>();
   final Set<UUID> dismissed = new HashSet<>();
   final Set<String> guardsFreed = new HashSet<>();
   final Map<UUID, ArmyData.Hire> hired = new HashMap<>();
   /** Garrison soldiers raised from citizens: who they were, so they can come home when discharged. */
   final Map<UUID, ArmyData.Conscript> conscripts = new HashMap<>();

   public static ArmyData get(MinecraftServer server) {
      return (ArmyData)server.overworld().getDataStorage().computeIfAbsent(ArmyData::load, ArmyData::new, "prestol_army");
   }

   public ArmyData.Post post(String key, UnitType fresh) {
      ArmyData.Post p = this.posts.get(key);
      if (p == null) {
         p = new ArmyData.Post(fresh);
         this.posts.put(key, p);
         this.setDirty();
      }

      return p;
   }

   public ArmyData.Post peek(String key) {
      return this.posts.get(key);
   }

   public Map<String, ArmyData.Post> posts() {
      return this.posts;
   }

   public Map<UUID, ArmyData.Levy> militia() {
      return this.militia;
   }

   public CompoundTag save(CompoundTag tag) {
      CompoundTag all = new CompoundTag();
      this.posts.forEach((k, p) -> {
         CompoundTag t = new CompoundTag();
         t.putString("type", p.type.id());
         if (p.owner != null) {
            t.putUUID("owner", p.owner);
         }

         ListTag units = new ListTag();

         for (UUID u : p.units) {
            units.add(NbtUtils.createUUID(u));
         }

         t.put("units", units);
         t.putLong("nextAt", p.nextAt);
         t.putString("lack", p.lack);
         all.put(k, t);
      });
      tag.put("posts", all);
      ListTag levies = new ListTag();
      this.militia.forEach((u, l) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("unit", u);
         t.putString("settlement", l.settlement());
         t.putInt("citizen", l.citizen());
         levies.add(t);
      });
      tag.put("militia", levies);
      ListTag gone = new ListTag();

      for (UUID u : this.dismissed) {
         gone.add(NbtUtils.createUUID(u));
      }

      tag.put("dismissed", gone);
      ListTag freed = new ListTag();

      for (String s : this.guardsFreed) {
         freed.add(StringTag.valueOf(s));
      }

      tag.put("guardsFreed", freed);
      ListTag hires = new ListTag();
      this.hired.forEach((u, h) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("unit", u);
         t.putUUID("player", h.player());
         t.putLong("until", h.until());
         hires.add(t);
      });
      tag.put("hired", hires);
      ListTag drafted = new ListTag();
      this.conscripts.forEach((u, c) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("unit", u);
         t.putString("settlement", c.settlement());
         t.put("citizen", c.citizen());
         drafted.add(t);
      });
      tag.put("conscripts", drafted);
      return tag;
   }

   public static ArmyData load(CompoundTag tag) {
      ArmyData d = new ArmyData();
      CompoundTag all = tag.getCompound("posts");

      for (String k : all.getAllKeys()) {
         CompoundTag t = all.getCompound(k);
         UnitType type = UnitType.byId(t.getString("type"));
         ArmyData.Post p = new ArmyData.Post(type == null ? UnitType.WARRIOR : type);
         if (t.hasUUID("owner")) {
            p.owner = t.getUUID("owner");
         }

         for (Tag u : t.getList("units", 11)) {
            p.units.add(NbtUtils.loadUUID(u));
         }

         p.nextAt = t.getLong("nextAt");
         p.lack = t.getString("lack");
         d.posts.put(k, p);
      }

      for (Tag x : tag.getList("militia", 10)) {
         CompoundTag t = (CompoundTag)x;
         d.militia.put(t.getUUID("unit"), new ArmyData.Levy(t.getString("settlement"), t.getInt("citizen")));
      }

      for (Tag u : tag.getList("dismissed", 11)) {
         d.dismissed.add(NbtUtils.loadUUID(u));
      }

      for (Tag x : tag.getList("hired", 10)) {
         CompoundTag t = (CompoundTag)x;
         d.hired.put(t.getUUID("unit"), new ArmyData.Hire(t.getUUID("player"), t.getLong("until")));
      }

      for (Tag s : tag.getList("guardsFreed", 8)) {
         d.guardsFreed.add(s.getAsString());
      }

      for (Tag x : tag.getList("conscripts", 10)) {
         CompoundTag t = (CompoundTag)x;
         d.conscripts.put(t.getUUID("unit"), new ArmyData.Conscript(t.getString("settlement"), t.getCompound("citizen")));
      }

      return d;
   }

   public static record Conscript(String settlement, CompoundTag citizen) {
   }

   public static record Hire(UUID player, long until) {
   }

   public static record Levy(String settlement, int citizen) {
   }

   public static final class Post {
      public UnitType type;
      public UUID owner;
      public final Set<UUID> units = new LinkedHashSet<>();
      public long nextAt;
      public String lack = "";

      Post(UnitType type) {
         this.type = type;
      }
   }
}
