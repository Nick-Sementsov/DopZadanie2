package com.nick.prestol.map;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.Prestol;
import com.nick.prestol.land.Territory;
import com.nick.prestol.net.Net;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.war.Sieges;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.level.ChunkEvent.Load;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MapSync {
   private static final int SCAN_RADIUS = 6;
   private static final int FALLBACK_RADIUS = 3;
   private static byte[] last;
   private static boolean dirty = true;
   private static int lastTerritory = -1;
   private static final Set<UUID> synced = new HashSet<>();
   private static int ticks;

   private MapSync() {
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void tick(MinecraftServer server) {
      ticks++;

      try {
         if (ticks % 5 == 0) {
            scan(server);
         }

         Territory territory = Territory.get(server);
         if (!dirty && territory.version() == lastTerritory && ticks % 30 != 0) {
            sendMissing(server);
            return;
         }

         dirty = false;
         lastTerritory = territory.version();
         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
         build(server).write(buf);
         byte[] bytes = new byte[buf.readableBytes()];
         buf.readBytes(bytes);
         buf.release();
         if (last == null || !Arrays.equals(last, bytes)) {
            last = bytes;
            synced.clear();
         }

         sendMissing(server);
      } catch (RuntimeException var4) {
         Prestol.LOGGER.warn("Prestol: map sync failed", var4);
      }
   }

   private static void sendMissing(MinecraftServer server) {
      if (last != null) {
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!synced.contains(p.getUUID()) && Net.hasMod(p)) {
               Net.sendMap(p, last);
               synced.add(p.getUUID());
            }
         }
      }
   }

   private static void scan(MinecraftServer server) {
      Territory t = Territory.get(server);

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         ServerLevel level = p.serverLevel();
         ChunkPos c = p.chunkPosition();

         for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
               LevelChunk chunk = level.getChunkSource().getChunkNow(c.x + dx, c.z + dz);
               if (chunk != null) {
                  try {
                     t.record(level, chunk);
                  } catch (RuntimeException var10) {
                  }
               }
            }
         }
      }
   }

   public static List<MapSnapshot.Layer> banner(Realm realm) {
      List<MapSnapshot.Layer> out = new ArrayList<>();
      ListTag tag = Colors.blank(realm.banner()) ? null : realm.banner();

      for (int i = 0; tag != null && i < tag.size() && out.size() < 17; i++) {
         CompoundTag l = tag.getCompound(i);
         if (l.contains("Pattern")) {
            out.add(new MapSnapshot.Layer(l.getString("Pattern"), l.getInt("Color")));
         }
      }

      if (out.isEmpty() || !"b".equals(out.get(0).pattern())) {
         out.add(0, new MapSnapshot.Layer("b", Colors.nearestDye(Colors.of(realm))));
      }

      return out;
   }

   public static MapSnapshot build(MinecraftServer server) {
      RealmData data = RealmData.get(server);
      List<MapSnapshot.RealmInfo> realms = new ArrayList<>();

      for (Realm r : data.all()) {
         realms.add(new MapSnapshot.RealmInfo(r.id(), r.name(), Colors.of(r), Realms.rank(r).key(), r.flag(), banner(r)));
      }

      List<MapSnapshot.SettlementInfo> settlements = new ArrayList<>();
      Map<String, Set<Integer>> idsByDim = new HashMap<>();
      Map<String, List<IColony>> byDim = new HashMap<>();

      for (IColony c : Settlements.all()) {
         String key = Settlements.key(c);
         Realm realm = data.byMember(key);
         String dim = c.getDimension().location().toString();
         BlockPos p = c.getCenter();
         settlements.add(
            new MapSnapshot.SettlementInfo(
               key,
               dim,
               c.getID(),
               c.getName(),
               p.getX(),
               p.getY(),
               p.getZ(),
               Settlements.tierOf(c).ordinal(),
               realm != null && key.equals(realm.capital()),
               realm == null ? null : realm.id(),
               Sieges.mapState(key),
               Settlements.ownerName(c)
            )
         );
         idsByDim.computeIfAbsent(dim, k -> new HashSet<>()).add(c.getID());
         byDim.computeIfAbsent(dim, k -> new ArrayList<>()).add(c);
      }

      Map<String, Long2IntOpenHashMap> territory = new HashMap<>();
      Territory known = Territory.get(server);

      for (Entry<String, Set<Integer>> e : idsByDim.entrySet()) {
         Long2IntOpenHashMap out = new Long2IntOpenHashMap();
         Set<Integer> seen = new HashSet<>();
         Long2IntOpenHashMap mem = known.all().get(e.getKey());
         if (mem != null) {
            ObjectIterator var13 = mem.long2IntEntrySet().iterator();

            while (var13.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2IntMap.Entry c = (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry)var13.next();
               if (e.getValue().contains(c.getIntValue())) {
                  out.put(c.getLongKey(), c.getIntValue());
                  seen.add(c.getIntValue());
               }
            }
         }

         for (IColony c : byDim.getOrDefault(e.getKey(), List.of())) {
            if (!seen.contains(c.getID())) {
               ChunkPos cp = new ChunkPos(c.getCenter());

               for (int dx = -3; dx <= 3; dx++) {
                  for (int dz = -3; dz <= 3; dz++) {
                     out.putIfAbsent(ChunkPos.asLong(cp.x + dx, cp.z + dz), c.getID());
                  }
               }
            }
         }

         territory.put(e.getKey(), out);
      }

      return new MapSnapshot(realms, settlements, territory);
   }

   public static void forgetColony(MinecraftServer server, IColony colony) {
      Territory.get(server).forgetColony(colony.getDimension().location().toString(), colony.getID());
      markDirty();
   }

   public static final class Events {
      @SubscribeEvent
      public void onChunkLoad(Load event) {
         if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            try {
               Territory.get(level.getServer()).record(level, chunk);
            } catch (RuntimeException var5) {
            }
         }
      }

      @SubscribeEvent
      public void onLogin(PlayerLoggedInEvent event) {
         MapSync.synced.remove(event.getEntity().getUUID());
      }

      @SubscribeEvent
      public void onLogout(PlayerLoggedOutEvent event) {
         MapSync.synced.remove(event.getEntity().getUUID());
      }

      @SubscribeEvent
      public void onDimension(PlayerChangedDimensionEvent event) {
         MapSync.synced.remove(event.getEntity().getUUID());
      }
   }
}
