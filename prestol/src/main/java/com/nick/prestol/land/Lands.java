package com.nick.prestol.land;

import com.minecolonies.api.colony.IChunkmanagerCapability;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyTagCapability;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.util.ChunkDataHelper;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class Lands {
   public static final int MAX_SIDE = 16;

   private Lands() {
   }

   public static List<IColony> colonies(MinecraftServer server, ServerPlayer player) {
      Realm realm = Realms.realmOf(server, player);
      return realm != null ? Realms.members(realm) : Settlements.ownedBy(player.getUUID());
   }

   public static int limit(List<IColony> colonies) {
      int total = 0;

      for (IColony c : colonies) {
         Settlements.Stats s = Settlements.stats(c);
         total += PrestolConfig.LAND_PER_SETTLEMENT.get()
            + PrestolConfig.LAND_PER_HALL_LEVEL.get() * s.townHall()
            + PrestolConfig.LAND_PER_BUILDING.get() * s.buildings();
      }

      return total;
   }

   public static int used(MinecraftServer server, List<IColony> colonies) {
      LandData data = LandData.get(server);
      int n = 0;

      for (IColony c : colonies) {
         n += data.of(Settlements.key(c)).size();
      }

      return n;
   }

   private static boolean mayChange(MinecraftServer server, ServerPlayer player, IColony colony) {
      Realm realm = RealmData.get(server).byMember(Settlements.key(colony));
      return Realms.isRuler(realm, player) || Settlements.isOwner(player, colony);
   }

   private static int chunksFromHall(IColony colony, int cx, int cz) {
      ChunkPos hall = new ChunkPos(colony.getCenter());
      return Math.max(Math.abs(cx - hall.x), Math.abs(cz - hall.z));
   }

   private static IChunkmanagerCapability manager(ServerLevel level) {
      return (IChunkmanagerCapability)level.getCapability(MineColonies.CHUNK_STORAGE_UPDATE_CAP, null).resolve().orElse(null);
   }

   public static Realms.Result rect(MinecraftServer server, ServerPlayer player, boolean take, int x1, int z1, int x2, int z2) {
      int left = Math.min(x1, x2);
      int right = Math.max(x1, x2);
      int top = Math.min(z1, z2);
      int bottom = Math.max(z1, z2);
      if (right - left < 16 && bottom - top < 16) {
         ServerLevel level = player.serverLevel();
         if (manager(level) == null) {
            return Realms.Result.no(Msg.bad("land.no_access"));
         } else {
            List<IColony> mine = colonies(server, player);
            if (mine.isEmpty()) {
               return Realms.Result.no(Msg.bad("land.no_settlement"));
            } else {
               int done = 0;
               int refused = 0;
               Realms.Result last = null;
               if (!take) {
                  for (int x = left; x <= right; x++) {
                     for (int z = top; z <= bottom; z++) {
                        Realms.Result r = giveUp(server, player, mine, x, z);
                        if (r != null) {
                           if (r.ok()) {
                              done++;
                           } else {
                              refused++;
                              last = r;
                           }
                        }
                     }
                  }

                  if (done == 0) {
                     return last != null ? last : Realms.Result.no(Msg.bad("land.none_drawn"));
                  } else {
                     return Realms.Result.yes(Msg.good("land.given", done, refused));
                  }
               } else {
                  Set<Long> tried = new HashSet<>();
                  boolean progress = true;
                  boolean stop = false;

                  while (progress && !stop) {
                     progress = false;

                     for (int x = left; x <= right && !stop; x++) {
                        for (int zx = top; zx <= bottom && !stop; zx++) {
                           long key = ChunkPos.asLong(x, zx);
                           if (!tried.contains(key)) {
                              int owner = Territory.ownerAt(level, x, zx);
                              if (owner > 0) {
                                 tried.add(key);
                                 if (!ownedBySide(level, mine, owner)) {
                                    refused++;
                                    last = Realms.Result.no(Msg.bad("land.foreign"));
                                 }
                              } else {
                                 IColony next = neighbour(level, mine, x, zx);
                                 if (next != null) {
                                    tried.add(key);
                                    if (!mayChange(server, player, next)) {
                                       refused++;
                                       last = Realms.Result.no(Msg.bad("land.not_yours", next.getName()));
                                    } else if (chunksFromHall(next, x, zx) > (Integer)PrestolConfig.LAND_REACH.get()) {
                                       refused++;
                                       last = Realms.Result.no(Msg.bad("land.too_far", next.getName(), PrestolConfig.LAND_REACH.get()));
                                    } else {
                                       int used = used(server, mine);
                                       int limit = limit(mine);
                                       if (used >= limit) {
                                          last = Realms.Result.no(Msg.bad("land.no_allowance", used, limit));
                                          stop = true;
                                          break;
                                       }

                                       claim(level, next, x, zx, true);
                                       LandData.get(server).of(Settlements.key(next)).add(key);
                                       LandData.get(server).setDirty();
                                       done++;
                                       progress = true;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (done == 0) {
                     return last != null ? last : Realms.Result.no(Msg.bad("land.not_touching"));
                  } else {
                     Realms.changed(server);
                     return Realms.Result.yes(Msg.good("land.taken", done, used(server, mine), limit(mine)));
                  }
               }
            }
         }
      } else {
         return Realms.Result.no(Msg.bad("land.too_big", 16));
      }
   }

   private static boolean ownedBySide(ServerLevel level, List<IColony> mine, int owner) {
      for (IColony c : mine) {
         if (c.getID() == owner && c.getDimension().equals(level.dimension())) {
            return true;
         }
      }

      return false;
   }

   private static IColony neighbour(ServerLevel level, List<IColony> mine, int cx, int cz) {
      int[][] around = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

      for (int[] d : around) {
         int owner = Territory.ownerAt(level, cx + d[0], cz + d[1]);

         for (IColony c : mine) {
            if (c.getID() == owner && c.getDimension().equals(level.dimension())) {
               return c;
            }
         }
      }

      return null;
   }

   private static Realms.Result giveUp(MinecraftServer server, ServerPlayer player, List<IColony> mine, int cx, int cz) {
      ServerLevel level = player.serverLevel();
      long key = ChunkPos.asLong(cx, cz);
      LandData data = LandData.get(server);

      for (IColony c : mine) {
         Set<Long> set = data.of(Settlements.key(c));
         if (set.contains(key)) {
            if (!mayChange(server, player, c)) {
               return Realms.Result.no(Msg.bad("land.not_yours", c.getName()));
            }

            IColonyTagCapability cap = level.hasChunk(cx, cz)
               ? (IColonyTagCapability)level.getChunk(cx, cz).getCapability(IColony.CLOSE_COLONY_CAP, null).resolve().orElse(null)
               : null;
            if (cap != null && !cap.getAllClaimingBuildings().getOrDefault(c.getID(), Set.of()).isEmpty()) {
               return Realms.Result.no(Msg.bad("land.under_building"));
            }

            claim(level, c, cx, cz, false);
            set.remove(key);
            data.setDirty();
            Realms.changed(server);
            return Realms.Result.yes(Msg.good("land.given", 1, 0));
         }
      }

      return null;
   }

   public static void claim(ServerLevel level, IColony colony, int cx, int cz, boolean add) {
      IChunkmanagerCapability manager = manager(level);
      if (manager != null) {
         ChunkDataHelper.tryClaim(level, new BlockPos(cx * 16, 0, cz * 16), add, colony.getID(), manager, !add || level.hasChunk(cx, cz));
         Territory.get(level.getServer()).set(level.dimension().location().toString(), ChunkPos.asLong(cx, cz), add ? colony.getID() : 0);
      }
   }
}
