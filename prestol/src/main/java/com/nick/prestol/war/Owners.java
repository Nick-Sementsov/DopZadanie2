package com.nick.prestol.war;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Rank;
import com.nick.prestol.Prestol;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Owners {
   private Owners() {
   }

   public static boolean setOwner(MinecraftServer server, IColony colony, UUID id, String name) {
      IPermissions perms = colony.getPermissions();
      UUID old = perms.getOwner();
      boolean done = false;
      ServerPlayer online = server.getPlayerList().getPlayer(id);
      if (online != null) {
         done = perms.setOwner(online);
      } else {
         try {
            Class<?> c = perms.getClass();
            Field players = field(c, "players");
            Field ownerName = field(c, "ownerName");
            Field ownerUUID = field(c, "ownerUUID");
            Field ranks = field(c, "ranks");
            Field abandoned = field(c, "fullyAbandoned");
            Map<UUID, ColonyPlayer> map = (Map<UUID, ColonyPlayer>)players.get(perms);
            Map<Integer, Rank> rankMap = (Map<Integer, Rank>)ranks.get(perms);
            map.remove(old);
            ownerName.set(perms, name);
            ownerUUID.set(perms, id);
            map.put(id, new ColonyPlayer(id, name, rankMap.get(0)));
            abandoned.setBoolean(perms, false);
            colony.markDirty();
            done = true;
         } catch (RuntimeException | ReflectiveOperationException var16) {
            Prestol.LOGGER.error("Prestol: could not hand {} to {}", new Object[]{colony.getName(), name, var16});
         }
      }

      if (!done) {
         return false;
      } else {
         Rank neutral = perms.getRankNeutral();
         if (old != null && !old.equals(id)) {
            perms.addPlayer(old, nameOf(server, old, perms), neutral);
         }

         for (ColonyPlayer p : new ArrayList<>(perms.getPlayers().values())) {
            if (!p.getID().equals(id) && p.getRank().isColonyManager()) {
               p.setRank(neutral);
            }
         }

         colony.markDirty();
         return true;
      }
   }

   private static Field field(Class<?> c, String name) throws NoSuchFieldException {
      while (c != null) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
         } catch (NoSuchFieldException var3) {
            c = c.getSuperclass();
         }
      }

      throw new NoSuchFieldException(name);
   }

   public static String nameOf(MinecraftServer server, UUID id, IPermissions perms) {
      ServerPlayer p = server.getPlayerList().getPlayer(id);
      if (p != null) {
         return p.getGameProfile().getName();
      } else {
         ColonyPlayer cp = perms == null ? null : (ColonyPlayer)perms.getPlayers().get(id);
         return cp != null && cp.getName() != null ? cp.getName() : server.getProfileCache().get(id).map(g -> g.getName()).orElse(id.toString().substring(0, 8));
      }
   }
}
