package com.nick.prestol.realm;

import com.nick.prestol.Prestol;
import com.nick.prestol.decree.DecreeData;
import com.nick.prestol.land.LandData;
import com.nick.prestol.land.Territory;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class DerzhavaImport {
   private DerzhavaImport() {
   }

   private static CompoundTag read(Path dir, String name) {
      File f = dir.resolve(name + ".dat").toFile();
      if (!f.isFile()) {
         return null;
      } else {
         try {
            return NbtIo.readCompressed(f).getCompound("data");
         } catch (Exception var4) {
            Prestol.LOGGER.warn("Prestol: could not read old {}", f, var4);
            return null;
         }
      }
   }

   public static void run(MinecraftServer server) {
      RealmData data = RealmData.get(server);
      if (!data.imported()) {
         Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
         CompoundTag nations = read(dir, "derzhava_nations");
         int realms = 0;
         if (nations != null && data.all().isEmpty()) {
            ListTag list = nations.getList("nations", 10);
            List<CompoundTag> raw = new ArrayList<>();

            for (int i = 0; i < list.size(); i++) {
               CompoundTag n = list.getCompound(i);
               raw.add(n);
               Realm r = new Realm(n.getUUID("id"), n.getString("name"), n.getUUID("ruler"), n.getString("capital"), n.getLong("founded"));
               ListTag members = n.getList("members", 8);

               for (int j = 0; j < members.size(); j++) {
                  r.members().add(members.getString(j));
               }

               r.setColor(n.contains("color") ? n.getInt("color") : -1);
               r.setBanner(n.getList("flag", 10));
               data.add(r);
               realms++;
            }

            Set<String> wars = new HashSet<>();

            for (CompoundTag n : raw) {
               UUID self = n.getUUID("id");
               CompoundTag stances = n.getCompound("stances");

               for (String other : stances.getAllKeys()) {
                  UUID them;
                  try {
                     them = UUID.fromString(other);
                  } catch (IllegalArgumentException var20) {
                     continue;
                  }

                  Realm a = data.byId(self);
                  Realm b = data.byId(them);
                  if (a != null && b != null) {
                     String stance = stances.getString(other).toUpperCase(Locale.ROOT);
                     if ("ALLY".equals(stance)) {
                        a.allies().add(them);
                     } else if ("ENEMY".equals(stance) && wars.add(RealmData.pair(self, them))) {
                        long now = Realms.now(server);
                        data.addWar(new RealmData.War(self, them, now, now));
                     }
                  }
               }
            }

            CompoundTag tiers = nations.getCompound("tiers");

            for (String k : tiers.getAllKeys()) {
               data.holding(k).tier = tiers.getInt(k);
            }

            CompoundTag owners = nations.getCompound("owners");

            for (String k : owners.getAllKeys()) {
               if (owners.hasUUID(k)) {
                  data.holding(k).lastOwner = owners.getUUID(k);
               }
            }
         }

         CompoundTag territory = read(dir, "derzhava_territory");
         if (territory != null) {
            Territory mine = Territory.get(server);
            Territory old = Territory.load(territory);
            old.all().forEach((dim, map) -> map.long2IntEntrySet().forEach(e -> {
                  if (mine.owner(dim, e.getLongKey()) == 0) {
                     mine.set(dim, e.getLongKey(), e.getIntValue());
                  }
               }));
         }

         CompoundTag lands = read(dir, "derzhava_lands");
         if (lands != null) {
            LandData mine = LandData.get(server);
            LandData old = LandData.load(lands);
            old.all().forEach((kx, set) -> mine.of(kx).addAll(set));
            mine.setDirty();
         }

         CompoundTag decrees = read(dir, "derzhava_decrees");
         if (decrees != null) {
            DecreeData mine = DecreeData.get(server);
            if (mine.active.isEmpty() && mine.laws.isEmpty()) {
               DecreeData old = DecreeData.load(decrees);
               mine.active.putAll(old.active);
               mine.readyAt.putAll(old.readyAt);
               mine.laws.putAll(old.laws);
               mine.lawLockUntil.putAll(old.lawLockUntil);
               mine.setDirty();
            }
         }

         data.setImported();
         if (nations != null || lands != null || decrees != null) {
            Prestol.LOGGER.info("Prestol: carried over the old Derzhava data ({} realms)", realms);
            if (realms > 0) {
               Realms.chronicleAll(server, "imported", String.valueOf(realms));
            }
         }
      }
   }
}
