package com.nick.prestol.decree;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class DecreeData extends SavedData {
   private static final String NAME = "prestol_decrees";
   public final Map<String, Map<DecreeType, DecreeData.Active>> active = new HashMap<>();
   public final Map<String, Map<DecreeType, Long>> readyAt = new HashMap<>();
   public final Map<UUID, Set<LawType>> laws = new HashMap<>();
   public final Map<UUID, Long> lawLockUntil = new HashMap<>();

   public static DecreeData get(MinecraftServer server) {
      return (DecreeData)server.overworld().getDataStorage().computeIfAbsent(DecreeData::load, DecreeData::new, "prestol_decrees");
   }

   public DecreeData.Active active(String colony, DecreeType type) {
      Map<DecreeType, DecreeData.Active> m = this.active.get(colony);
      return m == null ? null : m.get(type);
   }

   public void putActive(String colony, DecreeType type, DecreeData.Active a) {
      this.active.computeIfAbsent(colony, k -> new EnumMap<>(DecreeType.class)).put(type, a);
      this.setDirty();
   }

   public void removeActive(String colony, DecreeType type) {
      Map<DecreeType, DecreeData.Active> m = this.active.get(colony);
      if (m != null) {
         m.remove(type);
         if (m.isEmpty()) {
            this.active.remove(colony);
         }

         this.setDirty();
      }
   }

   public long readyAt(String colony, DecreeType type) {
      Map<DecreeType, Long> m = this.readyAt.get(colony);
      Long v = m == null ? null : m.get(type);
      return v == null ? 0L : v;
   }

   public void putReadyAt(String colony, DecreeType type, long tick) {
      this.readyAt.computeIfAbsent(colony, k -> new EnumMap<>(DecreeType.class)).put(type, tick);
      this.setDirty();
   }

   public Set<LawType> lawsOf(UUID nation) {
      Set<LawType> s = this.laws.get(nation);
      return s == null ? Set.of() : s;
   }

   public void enact(UUID nation, LawType law) {
      this.laws.computeIfAbsent(nation, k -> new LinkedHashSet<>()).add(law);
      this.setDirty();
   }

   public void repeal(UUID nation, LawType law) {
      Set<LawType> s = this.laws.get(nation);
      if (s != null) {
         s.remove(law);
         if (s.isEmpty()) {
            this.laws.remove(nation);
         }

         this.setDirty();
      }
   }

   public long lawLockUntil(UUID nation) {
      Long v = this.lawLockUntil.get(nation);
      return v == null ? 0L : v;
   }

   public void setLawLock(UUID nation, long tick) {
      this.lawLockUntil.put(nation, tick);
      this.setDirty();
   }

   public boolean prune(long now, Predicate<UUID> nationExists) {
      boolean changed = false;
      Iterator<Map<DecreeType, Long>> it = this.readyAt.values().iterator();

      while (it.hasNext()) {
         Map<DecreeType, Long> byType = it.next();
         changed |= byType.values().removeIf(tick -> tick <= now);
         if (byType.isEmpty()) {
            it.remove();
            changed = true;
         }
      }

      changed |= this.lawLockUntil.entrySet().removeIf(e -> e.getValue() <= now || !nationExists.test(e.getKey()));
      if (changed) {
         this.setDirty();
      }

      return changed;
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag act = new ListTag();
      this.active.forEach((colony, byType) -> byType.forEach((type, a) -> {
            CompoundTag t = new CompoundTag();
            t.putString("colony", colony);
            t.putString("type", type.id);
            t.putLong("end", a.end());
            t.putDouble("scale", a.scale());
            act.add(t);
         }));
      tag.put("active", act);
      ListTag cd = new ListTag();
      this.readyAt.forEach((colony, byType) -> byType.forEach((type, tick) -> {
            CompoundTag t = new CompoundTag();
            t.putString("colony", colony);
            t.putString("type", type.id);
            t.putLong("ready", tick);
            cd.add(t);
         }));
      tag.put("cooldowns", cd);
      ListTag lawList = new ListTag();
      this.laws.forEach((nation, set) -> set.forEach(law -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("nation", nation);
            t.putString("law", law.id);
            lawList.add(t);
         }));
      tag.put("laws", lawList);
      ListTag locks = new ListTag();
      this.lawLockUntil.forEach((nation, tick) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("nation", nation);
         t.putLong("until", tick);
         locks.add(t);
      });
      tag.put("lawLocks", locks);
      return tag;
   }

   public static DecreeData load(CompoundTag tag) {
      DecreeData d = new DecreeData();

      for (Tag raw : tag.getList("active", 10)) {
         CompoundTag t = (CompoundTag)raw;
         DecreeType type = DecreeType.byId(t.getString("type"));
         if (type != null) {
            d.active
               .computeIfAbsent(t.getString("colony"), k -> new EnumMap<>(DecreeType.class))
               .put(type, new DecreeData.Active(t.getLong("end"), t.getDouble("scale")));
         }
      }

      for (Tag rawx : tag.getList("cooldowns", 10)) {
         CompoundTag t = (CompoundTag)rawx;
         DecreeType type = DecreeType.byId(t.getString("type"));
         if (type != null) {
            d.readyAt.computeIfAbsent(t.getString("colony"), k -> new EnumMap<>(DecreeType.class)).put(type, t.getLong("ready"));
         }
      }

      for (Tag rawxx : tag.getList("laws", 10)) {
         CompoundTag t = (CompoundTag)rawxx;
         LawType law = LawType.byId(t.getString("law"));
         if (law != null) {
            d.laws.computeIfAbsent(t.getUUID("nation"), k -> new LinkedHashSet<>()).add(law);
         }
      }

      for (Tag rawxxx : tag.getList("lawLocks", 10)) {
         CompoundTag t = (CompoundTag)rawxxx;
         d.lawLockUntil.put(t.getUUID("nation"), t.getLong("until"));
      }

      return d;
   }

   public static record Active(long end, double scale) {
   }
}
