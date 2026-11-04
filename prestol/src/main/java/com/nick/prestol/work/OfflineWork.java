package com.nick.prestol.work;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

public final class OfflineWork {
   private static final int SANE_BOX = 6;
   private static final int HALL_MARGIN = 2;
   private static final Map<String, OfflineWork.Held> HELD = new ConcurrentHashMap<>();
   private static volatile boolean hookSeen;

   private OfflineWork() {
   }

   public static void registerChunkCallback() {
      ForgeChunkManager.setForcedChunkLoadingCallback("prestol", (level, helper) -> {
         for (BlockPos owner : new ArrayList<>(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(owner);
         }
      });
   }

   public static boolean keepsAwake(IColony colony) {
      if (!hookSeen) {
         hookSeen = true;
         Prestol.LOGGER.info("Prestol: colonies can be kept working without players (colony hook ok)");
      }

      return colony.getWorld() != null && !HELD.isEmpty() && HELD.containsKey(Settlements.key(colony));
   }

   public static boolean hookSeen() {
      return hookSeen;
   }

   public static int heldChunks(IColony colony) {
      OfflineWork.Held held = HELD.get(Settlements.key(colony));
      return held == null ? 0 : held.chunks().size();
   }

   public static void forgetAll() {
      HELD.clear();
   }

   public static void tick(MinecraftServer server) {
      Map<String, IColony> wanted = wanted(server);
      Iterator<Entry<String, OfflineWork.Held>> it = HELD.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, OfflineWork.Held> entry = it.next();
         if (!wanted.containsKey(entry.getKey())) {
            release(entry.getValue(), new ArrayList<>(entry.getValue().chunks()));
            it.remove();
         }
      }

      int max = (Integer)PrestolConfig.OFFLINE_CHUNKS.get();
      wanted.forEach((key, colony) -> hold(key, colony, max));
   }

   private static Map<String, IColony> wanted(MinecraftServer server) {
      Map<String, IColony> out = new LinkedHashMap<>();
      String mode = (String)PrestolConfig.OFFLINE.get();
      if (mode.equals("off")) {
         return out;
      } else {
         int sleepAfter = (Integer)PrestolConfig.OFFLINE_HOURS.get();
         List<IColony> candidates = new ArrayList<>();

         for (Realm nation : RealmData.get(server).all()) {
            for (IColony colony : Realms.members(nation)) {
               boolean capital = Settlements.key(colony).equals(nation.capital());
               if ((capital || mode.equals("all"))
                  && colony.getWorld() instanceof ServerLevel
                  && (sleepAfter == 0 || colony.getLastContactInHours() < sleepAfter)) {
                  candidates.add(colony);
               }
            }
         }

         candidates.sort(Comparator.comparingInt(IColony::getLastContactInHours).thenComparingInt(IColony::getID));
         int room = (Integer)PrestolConfig.OFFLINE_COLONIES.get();

         for (IColony colonyx : candidates) {
            if (out.size() >= room) {
               break;
            }

            out.put(Settlements.key(colonyx), colonyx);
         }

         return out;
      }
   }

   private static void hold(String key, IColony colony, int max) {
      ServerLevel level = (ServerLevel)colony.getWorld();
      BlockPos owner = colony.getCenter();
      OfflineWork.Held held = HELD.get(key);
      if (held != null && (held.level() != level || !held.owner().equals(owner))) {
         release(held, new ArrayList<>(held.chunks()));
         held = null;
      }

      if (held == null) {
         held = new OfflineWork.Held(level, owner, ConcurrentHashMap.newKeySet());
         HELD.put(key, held);
      }

      Set<Long> want = chunksOf(colony, max);

      for (long chunk : want) {
         if (held.chunks().add(chunk)) {
            ForgeChunkManager.forceChunk(level, "prestol", owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
         }
      }

      List<Long> extra = new ArrayList<>();

      for (long chunkx : held.chunks()) {
         if (!want.contains(chunkx)) {
            extra.add(chunkx);
         }
      }

      release(held, extra);
   }

   private static void release(OfflineWork.Held held, List<Long> chunks) {
      for (long chunk : chunks) {
         ForgeChunkManager.forceChunk(held.level(), "prestol", held.owner(), ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
         held.chunks().remove(chunk);
      }
   }

   private static Set<Long> chunksOf(IColony colony, int max) {
      List<int[]> boxes = new ArrayList<>();

      for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
         BlockPos pos = building.getPosition();
         int minX = pos.getX() >> 4;
         int maxX = minX;
         int minZ = pos.getZ() >> 4;
         int maxZ = minZ;
         Tuple<BlockPos, BlockPos> corners = building.getCorners();
         if (corners != null && corners.getA() != null && corners.getB() != null) {
            int ax = Math.min(((BlockPos)corners.getA()).getX(), ((BlockPos)corners.getB()).getX()) >> 4;
            int bx = Math.max(((BlockPos)corners.getA()).getX(), ((BlockPos)corners.getB()).getX()) >> 4;
            int az = Math.min(((BlockPos)corners.getA()).getZ(), ((BlockPos)corners.getB()).getZ()) >> 4;
            int bz = Math.max(((BlockPos)corners.getA()).getZ(), ((BlockPos)corners.getB()).getZ()) >> 4;
            if (ax <= minX && bx >= minX && az <= minZ && bz >= minZ && minX - ax <= 6 && bx - minX <= 6 && minZ - az <= 6 && bz - minZ <= 6) {
               minX = ax;
               maxX = bx;
               minZ = az;
               maxZ = bz;
            }
         }

         boxes.add(new int[]{minX, minZ, maxX, maxZ});
      }

      ChunkPos hall = new ChunkPos(colony.getCenter());
      return plan(hall.x, hall.z, boxes, max);
   }

   public static Set<Long> plan(int hallX, int hallZ, List<int[]> boxes, int max) {
      Set<Long> all = new HashSet<>();
      add(all, hallX - 2, hallZ - 2, hallX + 2, hallZ + 2);

      for (int[] box : boxes) {
         add(all, box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1);
      }

      if (all.size() <= max) {
         return all;
      } else {
         List<Long> sorted = new ArrayList<>(all);
         sorted.sort(
            Comparator.<Long>comparingInt(c -> Math.max(Math.abs(ChunkPos.getX(c) - hallX), Math.abs(ChunkPos.getZ(c) - hallZ)))
               .thenComparingLong(c -> c)
         );
         return new HashSet<>(sorted.subList(0, Math.max(0, max)));
      }
   }

   private static void add(Set<Long> to, int minX, int minZ, int maxX, int maxZ) {
      for (int x = minX; x <= maxX; x++) {
         for (int z = minZ; z <= maxZ; z++) {
            to.add(ChunkPos.asLong(x, z));
         }
      }
   }

   private static record Held(ServerLevel level, BlockPos owner, Set<Long> chunks) {
   }
}
