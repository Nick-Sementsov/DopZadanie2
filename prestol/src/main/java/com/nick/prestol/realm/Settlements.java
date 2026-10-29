package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.nick.prestol.PrestolConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class Settlements {
   private Settlements() {
   }

   public static String key(IColony colony) {
      return colony.getDimension().location() + "#" + colony.getID();
   }

   public static IColony byKey(String key) {
      if (key == null) {
         return null;
      } else {
         int at = key.lastIndexOf(35);
         if (at <= 0) {
            return null;
         } else {
            try {
               ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.substring(0, at)));
               return IColonyManager.getInstance().getColonyByDimension(Integer.parseInt(key.substring(at + 1)), dim);
            } catch (RuntimeException var3) {
               return null;
            }
         }
      }
   }

   public static List<IColony> all() {
      try {
         List<IColony> list = IColonyManager.getInstance().getAllColonies();
         return (List<IColony>)(list == null ? Collections.emptyList() : new ArrayList<>(list));
      } catch (RuntimeException var1) {
         return Collections.emptyList();
      }
   }

   public static Settlements.Stats stats(IColony colony) {
      int citizens = 0;
      int hall = 0;
      int buildings = 0;

      try {
         citizens = colony.getCitizenManager().getCurrentCitizenCount();
      } catch (RuntimeException var7) {
      }

      try {
         IBuilding th = (IBuilding)colony.getServerBuildingManager().getTownHall();
         hall = th == null ? 0 : th.getBuildingLevel();
      } catch (RuntimeException var6) {
      }

      try {
         // a hut block that was only placed is a level-0 building; it must not make a hamlet a town
         buildings = (int)colony.getServerBuildingManager().getBuildings().values().stream().filter(b -> b.getBuildingLevel() > 0).count();
      } catch (RuntimeException var5) {
      }

      return new Settlements.Stats(citizens, hall, buildings);
   }

   public static boolean meets(Settlements.Stats s, Tier tier) {
      return s.citizens() >= tier.minCitizens() && s.townHall() >= tier.minTownHall() && s.buildings() >= tier.minBuildings();
   }

   public static Tier tierOf(Settlements.Stats s) {
      Tier best = Tier.HAMLET;

      for (Tier t : Tier.values()) {
         if (!meets(s, t)) {
            break;
         }

         best = t;
      }

      return best;
   }

   public static Tier tierOf(IColony colony) {
      return tierOf(stats(colony));
   }

   public static boolean canBeCapital(IColony colony) {
      Settlements.Stats s = stats(colony);
      return tierOf(s) == Tier.TOWN && s.citizens() >= (Integer)PrestolConfig.CAPITAL_CITIZENS.get();
   }

   public static double distance(IColony a, IColony b) {
      BlockPos p = a.getCenter();
      BlockPos q = b.getCenter();
      double dx = (double)(p.getX() - q.getX());
      double dz = (double)(p.getZ() - q.getZ());
      return Math.sqrt(dx * dx + dz * dz);
   }

   public static UUID owner(IColony colony) {
      try {
         return colony.getPermissions().getOwner();
      } catch (RuntimeException var2) {
         return null;
      }
   }

   public static String ownerName(IColony colony) {
      try {
         String name = colony.getPermissions().getOwnerName();
         return name == null ? "?" : name;
      } catch (RuntimeException var2) {
         return "?";
      }
   }

   public static boolean isOwner(ServerPlayer player, IColony colony) {
      return player.getUUID().equals(owner(colony));
   }

   public static boolean isManager(UUID player, IColony colony) {
      try {
         return colony.getPermissions().getRank(player).isColonyManager();
      } catch (RuntimeException var3) {
         return false;
      }
   }

   public static boolean isManager(ServerPlayer player, IColony colony) {
      return isManager(player.getUUID(), colony);
   }

   public static boolean isMember(ServerPlayer player, IColony colony) {
      try {
         return colony.getPermissions().isColonyMember(player);
      } catch (RuntimeException var3) {
         return false;
      }
   }

   public static List<IColony> ownedBy(UUID player) {
      List<IColony> out = new ArrayList<>();

      for (IColony c : all()) {
         if (player.equals(owner(c))) {
            out.add(c);
         }
      }

      return out;
   }

   public static double happiness(IColony colony) {
      try {
         return colony.getOverallHappiness();
      } catch (RuntimeException var2) {
         return 5.0;
      }
   }

   public static record Stats(int citizens, int townHall, int buildings) {
   }
}
