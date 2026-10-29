package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Sieges;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Provisions: every soldier of a player carries a food bar (0-100) that empties in {@code army.provisionDays}
 * game days. It is refilled with ordinary food from the colony (warehouse, town hall, his tower), from the
 * supply points of Hundred Years War, or from the pack of the commander he follows. A hungry soldier is weak,
 * and one who starves too long deserts; a recruit goes back home to his colony.
 */
public final class Provisions {
   static final String FOOD = "prestol_food";
   static final String HUNGRY_SINCE = "prestol_hungry";
   private static final double TOP_UP_BELOW = 60.0;
   private static final double PLAYER_REACH = 24.0;

   private Provisions() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.PROVISIONS.get() && Hyw.present();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static double get(Entity unit) {
      CompoundTag d = unit.getPersistentData();
      return d.contains(FOOD) ? d.getDouble(FOOD) : 100.0;
   }

   static void set(Entity unit, double value) {
      unit.getPersistentData().putDouble(FOOD, Math.max(0.0, Math.min(100.0, value)));
   }

   static void fill(Entity unit) {
      set(unit, 100.0);
      unit.getPersistentData().remove(HUNGRY_SINCE);
   }

   private static int days() {
      return Math.max(1, PrestolConfig.PROVISION_DAYS.get());
   }

   /** Food points (nutrition) one point of the bar is worth: a full bar is {@code days} daily rations. */
   private static double nutritionPerPoint() {
      return PrestolConfig.DAILY_RATION.get() * (double)days() / 100.0;
   }

   /** Runs every {@code interval} ticks over every loaded soldier. */
   static void tick(MinecraftServer server, ArmyData data, int interval, long now) {
      if (on()) {
         double burn = 100.0 * interval / (24000.0 * days());
         RealmData realms = RealmData.get(server);

         for (ServerLevel level : server.getAllLevels()) {
            List<Entity> soldiers = new ArrayList<>();

            for (Entity e : level.getAllEntities()) {
               if (Hyw.eater(e)) {
                  soldiers.add(e);
               }
            }

            for (Entity unit : soldiers) {
               Hyw.noSupply(unit);
               String tag = unit.getPersistentData().getString("prestol_army");
               // militia stands for a few minutes and mercenaries bring their own bread
               if (!tag.startsWith("m|") && !tag.startsWith("x|")) {
                  double food = get(unit) - burn;
                  if (food < TOP_UP_BELOW) {
                     food += eat(server, realms, level, unit, tag, 100.0 - food);
                  }

                  set(unit, food);
                  hunger(server, data, unit, food, now);
               }
            }
         }
      }
   }

   /** Finds food for {@code points} of the bar; returns how many points were eaten. */
   private static double eat(MinecraftServer server, RealmData realms, ServerLevel level, Entity unit, String tag, double points) {
      UUID owner = ((OwnableEntity)unit).getOwnerUUID();
      double perPoint = nutritionPerPoint();
      int want = (int)Math.ceil(points * perPoint);
      int eaten = 0;
      List<IItemHandler> stores = new ArrayList<>();
      if (tag.startsWith("g|")) {
         IColony colony = Settlements.byKey(Army.settlementOf(tag.substring(2)));
         IBuilding post = colony == null ? null : (IBuilding)colony.getServerBuildingManager().getBuilding(Army.posOf(tag.substring(2)));
         if (post != null) {
            stores.addAll(Supplies.stores(colony, post));
         }
      } else {
         IColony here = colonyAt(level, unit);
         if (here != null && friendly(server, realms, here, owner)) {
            stores.addAll(Supplies.stores(here, null));
         }
      }

      if (!stores.isEmpty()) {
         eaten += Supplies.takeFood(stores, want);
      }

      double got = eaten / perPoint;
      if (got < points) {
         got += Hyw.fromSupplyPoints(level, unit.blockPosition(), owner, points - got, days());
      }

      if (got < points) {
         ServerPlayer leader = server.getPlayerList().getPlayer(owner);
         if (leader != null && leader.level() == level && leader.distanceTo(unit) <= PLAYER_REACH && !leader.isCreative()) {
            int more = Supplies.takeFood(List.of(new PlayerMainInvWrapper(leader.getInventory())), (int)Math.ceil((points - got) * perPoint));
            got += more / perPoint;
         }
      }

      return Math.min(points, got);
   }

   private static void hunger(MinecraftServer server, ArmyData data, Entity unit, double food, long now) {
      CompoundTag d = unit.getPersistentData();
      if (unit instanceof LivingEntity living) {
         if (food < 25.0) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0, false, false));
         }

         if (food <= 0.0) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 0, false, false));
         }
      }

      if (food > 0.0) {
         d.remove(HUNGRY_SINCE);
      } else if (!d.contains(HUNGRY_SINCE)) {
         d.putLong(HUNGRY_SINCE, now);
         tellOwner(server, unit, Msg.bad("army.starving", unit.getDisplayName()));
      } else if (now - d.getLong(HUNGRY_SINCE) >= PrestolConfig.DESERT_DAYS.get() * 24000L && PrestolConfig.DESERT_DAYS.get() > 0) {
         tellOwner(server, unit, Msg.bad("army.deserted", unit.getDisplayName()));
         if (data.conscripts.containsKey(unit.getUUID()) || unit.getPersistentData().getString("prestol_army").startsWith("g|")) {
            Army.desert(server, data, unit);
         } else {
            unit.discard();
         }
      }
   }

   private static void tellOwner(MinecraftServer server, Entity unit, Component msg) {
      UUID owner = ((OwnableEntity)unit).getOwnerUUID();
      ServerPlayer p = owner == null ? null : server.getPlayerList().getPlayer(owner);
      if (p != null) {
         p.sendSystemMessage(msg);
      }
   }

   private static IColony colonyAt(ServerLevel level, Entity unit) {
      try {
         return IColonyManager.getInstance().getColonyByPosFromWorld(level, unit.blockPosition());
      } catch (RuntimeException var3) {
         return null;
      }
   }

   /** A colony feeds the soldiers of its owner, of its officers and of its realm. */
   private static boolean friendly(MinecraftServer server, RealmData realms, IColony colony, UUID owner) {
      if (owner.equals(Settlements.owner(colony))) {
         return true;
      } else {
         Realm realm = realms.byMember(Settlements.key(colony));
         Realm theirs = Sieges.realmOfPlayer(server, realms, owner);
         return realm != null && realm == theirs;
      }
   }

   /** Shows the soldier a player looks at over the hotbar: name, level and, to his own side, provisions and morale. */
   static void inspect(MinecraftServer server) {
      {
         RealmData realms = RealmData.get(server);

         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getViewVector(1.0F).scale(10.0));
            AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box, Hyw::eater, 100.0);
            if (hit != null) {
               Entity unit = hit.getEntity();
               UUID owner = ((OwnableEntity)unit).getOwnerUUID();
               boolean ours = p.getUUID().equals(owner);
               if (!ours) {
                  Realm mine = Sieges.realmOfPlayer(server, realms, p.getUUID());
                  ours = mine != null && mine == Sieges.realmOfPlayer(server, realms, owner);
               }

               MutableComponent line = Component.empty().append(unit.getDisplayName()).append(" · ").append(Msg.t("army.rank", Hyw.rank(unit)));
               if (ours && on()) {
                  int food = (int)Math.round(get(unit));
                  ChatFormatting color = food >= 50 ? ChatFormatting.GREEN : (food >= 25 ? ChatFormatting.GOLD : ChatFormatting.RED);
                  line.append(" · ").append(Msg.t("army.provisions", food).withStyle(color));
               }

               if (ours && Morale.on()) {
                  line.append(" · ").append(Morale.describe(unit));
               }

               p.displayClientMessage(line, true);
            }
         }
      }
   }
}
