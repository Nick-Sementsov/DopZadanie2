package com.nick.prestol.realm;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The courts of the realms: who holds which office, and who is outlawed where. */
public final class CourtData extends SavedData {
   private static final String NAME = "prestol_court";
   final Map<UUID, Map<Court.Office, UUID>> offices = new HashMap<>();
   final Map<UUID, Map<UUID, CourtData.Outlaw>> outlaws = new HashMap<>();
   /** The outlaws whose hostility was handed to Hundred Years War, to take it back on a pardon. */
   final Map<UUID, Set<UUID>> hywHostile = new HashMap<>();

   public static CourtData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(CourtData::load, CourtData::new, NAME);
   }

   Map<Court.Office, UUID> offices(UUID realm) {
      return this.offices.computeIfAbsent(realm, r -> new EnumMap<>(Court.Office.class));
   }

   Map<UUID, CourtData.Outlaw> outlaws(UUID realm) {
      return this.outlaws.computeIfAbsent(realm, r -> new LinkedHashMap<>());
   }

   public Set<UUID> outlawsOf(UUID realm) {
      Map<UUID, CourtData.Outlaw> m = this.outlaws.get(realm);
      return m == null ? Set.of() : m.keySet();
   }

   public Set<UUID> hywHostile(UUID realm) {
      return this.hywHostile.getOrDefault(realm, Set.of());
   }

   public void setHywHostile(UUID realm, Set<UUID> outlaws) {
      if (!outlaws.equals(this.hywHostile.getOrDefault(realm, Set.of()))) {
         if (outlaws.isEmpty()) {
            this.hywHostile.remove(realm);
         } else {
            this.hywHostile.put(realm, new HashSet<>(outlaws));
         }

         this.setDirty();
      }
   }

   void forget(UUID realm) {
      this.offices.remove(realm);
      this.outlaws.remove(realm);
      this.setDirty();
   }

   @Override
   public CompoundTag save(CompoundTag tag) {
      ListTag courts = new ListTag();
      Set<UUID> realms = new HashSet<>(this.offices.keySet());
      realms.addAll(this.outlaws.keySet());
      realms.addAll(this.hywHostile.keySet());

      for (UUID realm : realms) {
         CompoundTag t = new CompoundTag();
         t.putUUID("realm", realm);
         CompoundTag o = new CompoundTag();
         this.offices.getOrDefault(realm, Map.of()).forEach((office, who) -> o.putUUID(office.id(), who));
         t.put("offices", o);
         ListTag list = new ListTag();

         for (CourtData.Outlaw x : this.outlaws.getOrDefault(realm, Map.of()).values()) {
            list.add(x.save());
         }

         t.put("outlaws", list);
         ListTag hyw = new ListTag();

         for (UUID id : this.hywHostile.getOrDefault(realm, Set.of())) {
            hyw.add(NbtUtils.createUUID(id));
         }

         t.put("hyw", hyw);
         courts.add(t);
      }

      tag.put("courts", courts);
      return tag;
   }

   private static CourtData load(CompoundTag tag) {
      CourtData d = new CourtData();

      for (Tag x : tag.getList("courts", 10)) {
         CompoundTag t = (CompoundTag)x;
         UUID realm = t.getUUID("realm");
         CompoundTag o = t.getCompound("offices");

         for (Court.Office office : Court.Office.values()) {
            if (o.hasUUID(office.id())) {
               d.offices(realm).put(office, o.getUUID(office.id()));
            }
         }

         for (Tag y : t.getList("outlaws", 10)) {
            CourtData.Outlaw out = CourtData.Outlaw.load((CompoundTag)y);
            d.outlaws(realm).put(out.player, out);
         }

         Set<UUID> hyw = new HashSet<>();

         for (Tag y : t.getList("hyw", 11)) {
            hyw.add(NbtUtils.loadUUID(y));
         }

         if (!hyw.isEmpty()) {
            d.hywHostile.put(realm, hyw);
         }
      }

      return d;
   }

   /** A player outlawed in a realm: hostile in its settlements until {@code until}, with a price on his head. */
   static final class Outlaw {
      final UUID player;
      final String name;
      final String reason;
      long bounty;
      long until;

      Outlaw(UUID player, String name, String reason, long bounty, long until) {
         this.player = player;
         this.name = name;
         this.reason = reason;
         this.bounty = bounty;
         this.until = until;
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putUUID("player", this.player);
         t.putString("name", this.name);
         t.putString("reason", this.reason);
         t.putLong("bounty", this.bounty);
         t.putLong("until", this.until);
         return t;
      }

      static CourtData.Outlaw load(CompoundTag t) {
         return new CourtData.Outlaw(t.getUUID("player"), t.getString("name"), t.getString("reason"), t.getLong("bounty"), t.getLong("until"));
      }
   }
}
