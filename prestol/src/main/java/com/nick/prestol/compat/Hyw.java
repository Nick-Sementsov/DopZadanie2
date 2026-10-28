package com.nick.prestol.compat;

import com.nick.prestol.Prestol;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraftforge.fml.ModList;

public final class Hyw {
   public static final String MOD = "hundred_years_war";
   private static Boolean present;
   private static int failures;
   private static boolean broken;

   private Hyw() {
   }

   public static boolean present() {
      if (present == null) {
         present = ModList.get().isLoaded("hundred_years_war");
      }

      return present;
   }

   public static void sync(MinecraftServer server, RealmData data) {
      if (present() && !broken) {
         try {
            HywBridge.sync(server, data);
            failures = 0;
         } catch (Throwable var3) {
            if (++failures == 1) {
               Prestol.LOGGER.warn("Prestol: Hundred Years War sync failed, will retry", var3);
            }

            if (failures >= 5) {
               broken = true;
               Prestol.LOGGER.error("Prestol: Hundred Years War sync switched off after 5 failures");
            }
         }
      }
   }

   public static void onRealmRemoved(MinecraftServer server, Realm realm) {
      if (present() && !broken) {
         try {
            HywBridge.removeTeam(server, realm);
         } catch (Throwable var3) {
            Prestol.LOGGER.warn("Prestol: could not remove the Hundred Years War team of {}", realm.name(), var3);
         }
      }
   }

   public static Entity spawn(ServerLevel level, String kind, UUID owner, BlockPos at, BlockPos home, int rank, int tier) {
      if (!present()) {
         return null;
      } else {
         try {
            return HywBridge.spawn(level, kind, owner, at, home, rank, tier);
         } catch (Throwable var8) {
            Prestol.LOGGER.warn("Prestol: could not raise a Hundred Years War {}", kind, var8);
            return null;
         }
      }
   }

   public static boolean orders(Entity unit, BlockPos home, List<BlockPos> patrol, LivingEntity follow) {
      if (!present()) {
         return false;
      } else {
         try {
            HywBridge.orders(unit, home, patrol, follow);
            return true;
         } catch (Throwable var5) {
            Prestol.LOGGER.warn("Prestol: could not give orders to a Hundred Years War soldier", var5);
            return false;
         }
      }
   }

   public static int rank(Entity unit) {
      if (!present()) {
         return -1;
      } else {
         try {
            return HywBridge.rank(unit);
         } catch (Throwable var2) {
            return -1;
         }
      }
   }

   public static void rankUp(Entity unit) {
      if (present()) {
         try {
            HywBridge.rankUp(unit);
         } catch (Throwable var2) {
            Prestol.LOGGER.warn("Prestol: could not train a Hundred Years War soldier", var2);
         }
      }
   }

   public static boolean eater(Entity entity) {
      if (!present() || broken) {
         return false;
      } else {
         try {
            return HywBridge.eater(entity);
         } catch (LinkageError | RuntimeException var2) {
            return false;
         }
      }
   }

   public static Attribute rangedDamage() {
      if (!present() || broken) {
         return null;
      } else {
         try {
            return HywBridge.rangedDamage();
         } catch (LinkageError | RuntimeException var1) {
            return null;
         }
      }
   }

   public static void noSupply(Entity entity) {
      if (present() && !broken) {
         try {
            HywBridge.noSupply(entity);
         } catch (LinkageError | RuntimeException var2) {
         }
      }
   }

   public static double fromSupplyPoints(ServerLevel level, BlockPos pos, UUID owner, double points, int days) {
      if (!present() || broken) {
         return 0.0;
      } else {
         try {
            return HywBridge.fromSupplyPoints(level, pos, owner, points, days);
         } catch (LinkageError | RuntimeException var7) {
            return 0.0;
         }
      }
   }

   /** Workers, porters and other non-combat HYW units neither besiege nor defend. */
   public static boolean fighter(Entity entity) {
      if (!present() || broken) {
         return false;
      } else {
         try {
            return HywBridge.fighter(entity);
         } catch (LinkageError | RuntimeException var2) {
            return false;
         }
      }
   }

   public static UUID soldierOwner(Entity entity) {
      if (entity instanceof LivingEntity living && living.isAlive() && entity instanceof OwnableEntity own) {
         ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
         if (type != null && "hundred_years_war".equals(type.getNamespace()) && fighter(entity)) {
            return own.getOwnerUUID();
         }

         return null;
      }

      return null;
   }
}
