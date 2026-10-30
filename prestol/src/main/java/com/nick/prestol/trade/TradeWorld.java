package com.nick.prestol.trade;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Court;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.land.Territory;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Traders on the move. Every trader travels his route in the books at his speed; when a player comes near, the
 * merchant appears there for real with his pack mules (their chests show the cargo) or in his boat, walks or sails
 * the same road, and disappears again when the players leave.
 */
public final class TradeWorld {
   static final String TAG = "prestol_trader";
   private static final double SPAWN_RANGE = 80.0;
   private static final double DESPAWN_RANGE = 112.0;
   private static final double SAMPLE_STEP = 48.0;

   private TradeWorld() {
   }

   private static ServerLevel level(MinecraftServer server, String dim) {
      ResourceLocation rl = ResourceLocation.tryParse(dim);
      return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
   }

   private static double speed(Trader t) {
      return t.kind == Trader.Kind.SHIP ? PrestolConfig.SHIP_SPEED.get() : PrestolConfig.CARAVAN_SPEED.get();
   }

   public static void tick(MinecraftServer server) {
      Prices.refresh(server);
      TradeData data = TradeData.get(server);
      if (!Trading.on()) {
         for (Trader t : data.traders.values()) {
            if (t.physical()) {
               despawn(server, t, true);
            }
         }
      } else {
         long now = Realms.now(server);
         RealmData realms = RealmData.get(server);
         boolean second = server.getTickCount() % 20 == 7;

         for (Trader t : new ArrayList<>(data.traders.values())) {
            if (t.stranger ? !Strangers.on() : realms.byId(t.realm) == null) {
               despawn(server, t, false);
               data.traders.remove(t.id);
            } else {
               if (t.physical()) {
                  moveReal(server, data, t, now);
               }

               if (second && data.traders.containsKey(t.id)) {
                  presence(server, t);
                  if (!t.physical()) {
                     step(server, data, t, now, null);
                  }

                  AutoTrade.idle(server, data, t, now);
               }
            }
         }

         if (second) {
            Strangers.tick(server, data, now);
         }
      }
   }

   // ------------------------------------------------------------------ the road

   /** One step of a trader's or a stranger's way (see {@link #advance}). */
   private static void step(MinecraftServer server, TradeData data, Trader t, long now, Vec3 moved) {
      if (t.stranger) {
         Strangers.advance(server, data, t, now, 1.0, moved);
      } else {
         advance(server, data, t, now, 1.0, moved);
      }
   }

   /** Where the merchant is heading just now, or null while he stands. */
   private static Vec3 heading(Trader t, long now) {
      if (t.stranger) {
         return Strangers.moving(t, now) ? Strangers.goal(t) : null;
      } else if (t.running && t.route.size() >= 2 && now >= t.dwellUntil) {
         int target = t.target < t.route.size() ? t.target : 0;
         IColony c = Settlements.byKey(t.route.get(target).settlement);
         return c == null ? null : goal(t, t.route.get(target), c);
      } else {
         return null;
      }
   }

   private static Vec3 goal(Trader t, Stop stop, IColony colony) {
      return t.wp < stop.waypoints.size() ? Vec3.atBottomCenterOf(stop.waypoints.get(t.wp)) : Vec3.atBottomCenterOf(Trading.anchor(colony, t.kind));
   }

   /**
    * One step along the route: {@code moved} is the merchant's real position when he is in the world (then the
    * step only checks for arrival), or null to move him in the books by {@code seconds} of travel.
    */
   private static void advance(MinecraftServer server, TradeData data, Trader t, long now, double seconds, Vec3 moved) {
      if (t.running && t.route.size() >= 2) {
         if (t.target < 0 || t.target >= t.route.size()) {
            t.target = 0;
            t.wp = 0;
         }

         if (now < t.dwellUntil) {
            return;
         }

         if (t.leaving) {
            depart(server, t);
         }

         Stop stop = t.route.get(t.target);
         IColony colony = Settlements.byKey(stop.settlement);
         if (colony == null || !Trading.dim(colony).equals(t.dim)) {
            t.note(Trading.json(Msg.bad("trade.stop_lost", stop.settlement)));
            next(t);
            return;
         }

         Vec3 goal = goal(t, stop, colony);
         boolean last = t.wp >= stop.waypoints.size();
         double reach;
         if (moved != null) {
            t.pos = moved;
            reach = last ? (t.kind == Trader.Kind.SHIP ? 20.0 : 8.0) : 4.0;
         } else {
            reach = 0.5;
            double dx = goal.x - t.pos.x;
            double dz = goal.z - t.pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            double step = speed(t) * seconds;
            if (d <= step) {
               t.pos = goal;
               t.legDone += d;
            } else {
               t.pos = new Vec3(t.pos.x + dx / d * step, t.pos.y + (goal.y - t.pos.y) * step / d, t.pos.z + dz / d * step);
               t.legDone += step;
            }

            if (t.robPending && t.legDone >= t.legLength / 2.0) {
               t.robPending = false;
               rob(server, t);
            }
         }

         double hx = goal.x - t.pos.x;
         double hz = goal.z - t.pos.z;
         if (hx * hx + hz * hz <= reach * reach) {
            if (!last) {
               t.wp++;
            } else {
               arrive(server, data, t, stop, colony, now);
            }
         } else {
            t.status("travel", colony.getName(), (int)remaining(t, stop, colony));
         }
      }
   }

   private static double remaining(Trader t, Stop stop, IColony colony) {
      double d = 0.0;
      Vec3 p = t.pos;

      for (int i = t.wp; i < stop.waypoints.size(); i++) {
         Vec3 w = Vec3.atBottomCenterOf(stop.waypoints.get(i));
         d += Math.sqrt(p.distanceToSqr(w));
         p = w;
      }

      return d + Math.sqrt(p.distanceToSqr(Vec3.atBottomCenterOf(Trading.anchor(colony, t.kind))));
   }

   private static void next(Trader t) {
      t.target = (t.target + 1) % Math.max(1, t.route.size());
      t.wp = 0;
      t.leaving = true;
   }

   private static void arrive(MinecraftServer server, TradeData data, Trader t, Stop stop, IColony colony, long now) {
      boolean home = t.auto && stop.settlement.equals(t.home);
      if (home) {
         AutoTrade.atHome(server, t, colony, now);
      } else {
         Trading.arrive(server, data, t, stop, colony);
         if (t.auto) {
            // done: these goods are no longer on their way here
            stop.orders.clear();
         }
      }

      if (t.physical()) {
         pushCargo(server, t);
      }

      t.dwellUntil = now + PrestolConfig.DWELL_SECONDS.get() * 20L;
      if (!home) {
         t.status("trading", colony.getName());
      }

      next(t);
   }

   /** Leaving a stop: pays the escort and finds out whether the road ahead runs through unsafe land. */
   private static void depart(MinecraftServer server, Trader t) {
      t.leaving = false;
      t.legDone = 0.0;
      t.escorted = false;
      Stop stop = t.route.get(t.target);
      IColony to = Settlements.byKey(stop.settlement);
      IColony from = Settlements.byKey(t.route.get((t.target - 1 + t.route.size()) % t.route.size()).settlement);
      t.legLength = to == null ? 0.0 : remaining(t, stop, to);
      if (t.guards > 0) {
         EconomyData eco = EconomyData.get(server);
         long cost = (long)t.guards * PrestolConfig.ESCORT_COST.get();
         if (eco.take(t.realm, cost)) {
            t.escorted = true;
            t.spent += cost;
            t.tripSpent += cost;
            if (cost > 0L) {
               eco.log(t.realm, Realms.day(Realms.now(server)), "escort", -cost, t.name);
            }
         } else {
            t.note(Trading.json(Msg.bad("trade.no_escort", Coins.fmt(cost))));
         }
      }

      t.risky = to != null && from != null && risky(server, t, stop, to);
      // bandits: as usual, gone from patrolled roads, thrice as bold where the ruler let them be
      int roads = t.stranger ? 1 : com.nick.prestol.events.RealmEvents.roads(server, t.realm);
      t.robPending = t.risky && !t.escorted && server.overworld().random.nextInt(100) < PrestolConfig.ROBBERY_CHANCE.get() * roads;
   }

   /** Whether the road crosses land that is neither the trader's realm's nor an ally's. */
   private static boolean risky(MinecraftServer server, Trader t, Stop stop, IColony to) {
      ServerLevel level = level(server, t.dim);
      if (level == null) {
         return false;
      } else {
         RealmData realms = RealmData.get(server);
         Realm mine = realms.byId(t.realm);
         List<Vec3> line = new ArrayList<>();
         line.add(t.pos);

         for (BlockPos w : stop.waypoints) {
            line.add(Vec3.atBottomCenterOf(w));
         }

         line.add(Vec3.atBottomCenterOf(Trading.anchor(to, t.kind)));

         for (int i = 0; i + 1 < line.size(); i++) {
            Vec3 a = line.get(i);
            Vec3 b = line.get(i + 1);
            double len = Math.sqrt(a.distanceToSqr(b));
            int n = Math.max(1, (int)(len / SAMPLE_STEP));

            for (int k = 0; k <= n; k++) {
               Vec3 p = a.lerp(b, (double)k / n);
               int owner = Territory.ownerAt(level, Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4);
               IColony c = owner <= 0 ? null : IColonyManager.getInstance().getColonyByWorld(owner, level);
               Realm r = c == null ? null : realms.byMember(Settlements.key(c));
               if (r == null || r != mine && !Trading.allied(mine, r)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   /** Bandits on the road: a trader without escort loses part of his goods. */
   private static void rob(MinecraftServer server, Trader t) {
      List<ItemStack> slots = t.cargo.slots;
      if (!slots.isEmpty()) {
         int lose = Math.max(1, slots.size() * (30 + server.overworld().random.nextInt(41)) / 100);

         for (int i = 0; i < lose && !slots.isEmpty(); i++) {
            slots.remove(server.overworld().random.nextInt(slots.size()));
         }

         MutableComponent msg = Msg.bad("trade.robbed_road", t.name, lose);
         t.note(Trading.json(msg));
         Realm r = RealmData.get(server).byId(t.realm);
         if (r != null) {
            Realms.tell(server, r, msg);
         }
      }
   }

   // ------------------------------------------------------------------ in the world

   private static boolean nearPlayer(ServerLevel level, Vec3 pos, double range) {
      for (ServerPlayer p : level.players()) {
         if (!p.isSpectator() && p.position().distanceToSqr(pos) <= range * range) {
            return true;
         }
      }

      return false;
   }

   private static void presence(MinecraftServer server, Trader t) {
      ServerLevel level = level(server, t.dim);
      if (level != null) {
         BlockPos at = BlockPos.containing(t.pos);
         boolean ticking = level.isPositionEntityTicking(at);
         if (t.physical()) {
            Entity m = level.getEntity(t.merchant);
            if (m != null && m.isAlive() && t.kind == Trader.Kind.SHIP && !t.beasts.isEmpty() && level.getEntity(t.beasts.get(0)) == null) {
               // the boat was broken: its chest spilled into the water
               t.cargo.slots.clear();
               lost(server, t, Msg.bad("trade.boat_broken", t.name));
            }

            if (m == null || !m.isAlive() || !ticking || !nearPlayer(level, t.pos, DESPAWN_RANGE)) {
               Prestol.LOGGER.debug(
                  "Prestol: trader {} leaves the world (merchant {}, alive {}, ticking {}, near {})", t.name, m != null, m != null && m.isAlive(), ticking, nearPlayer(level, t.pos, DESPAWN_RANGE)
               );
               despawn(server, t, true);
            }
         } else if (ticking && server.overworld().getGameTime() >= t.hiddenUntil && nearPlayer(level, t.pos, SPAWN_RANGE)) {
            spawn(server, level, t);
         }
      }
   }

   /**
    * Where to stand at {@code p}: the nearest spot at about the height {@code p} has, with solid ground below and
    * two free blocks above, so a merchant never lands on a roof, a flag mast or a tree. On water: the surface.
    */
   private static Vec3 surface(ServerLevel level, Vec3 p, boolean water) {
      if (water) {
         BlockPos top = level.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(p));
         return new Vec3(p.x, (double)top.getY(), p.z);
      } else {
         BlockPos base = BlockPos.containing(p);

         // the level first, near before far; then a step up or down, and so on
         for (int k = 0; k <= 16; k++) {
            int dy = (k + 1) / 2 * (k % 2 == 0 ? -1 : 1);

            for (int r = 0; r <= 6; r++) {
               for (int dx = -r; dx <= r; dx++) {
                  for (int dz = -r; dz <= r; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        BlockPos at = base.offset(dx, dy, dz);
                        if (standable(level, at)) {
                           return new Vec3(at.getX() + 0.5, (double)at.getY(), at.getZ() + 0.5);
                        }
                     }
                  }
               }
            }
         }

         BlockPos top = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, base);
         return new Vec3(p.x, (double)top.getY(), p.z);
      }
   }

   /** The nearest open water to {@code p} within {@code range} blocks, at its surface; null if there is none. */
   private static Vec3 water(ServerLevel level, Vec3 p, int range) {
      BlockPos base = BlockPos.containing(p);

      for (int r = 0; r <= range; r++) {
         for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  BlockPos top = level.getHeightmapPos(Types.MOTION_BLOCKING, base.offset(dx, 0, dz)).below();
                  if (level.getFluidState(top).isSource() && level.getBlockState(top.above()).isAir()) {
                     return new Vec3(top.getX() + 0.5, (double)top.getY() + 0.9, top.getZ() + 0.5);
                  }
               }
            }
         }
      }

      return null;
   }

   private static boolean standable(ServerLevel level, BlockPos at) {
      return level.getBlockState(at).getCollisionShape(level, at).isEmpty()
         && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
         && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
         && level.getFluidState(at).isEmpty();
   }

   private static float facing(Trader t, Vec3 from) {
      Vec3 to = t.pos;
      if (t.stranger) {
         Vec3 g = Strangers.goal(t);
         to = g == null ? to : g;
      } else if (t.route.size() >= 2 && t.target < t.route.size()) {
         IColony c = Settlements.byKey(t.route.get(t.target).settlement);
         if (c != null) {
            to = goal(t, t.route.get(t.target), c);
         }
      }

      return (float)(Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
   }

   static void spawn(MinecraftServer server, ServerLevel level, Trader t) {
      MerchantEntity m = TradeEntities.MERCHANT.get().create(level);
      if (m != null) {
         boolean ship = t.kind == Trader.Kind.SHIP;
         Vec3 at = ship ? water(level, t.pos, 24) : surface(level, t.pos, false);
         if (at == null) {
            // a ship over land in the books (between two way points): it shows itself again on the water
            m.discard();
            return;
         }

         float yaw = facing(t, at);
         Realm realm = RealmData.get(server).byId(t.realm);
         m.trader = t.id;
         m.moveTo(at.x, at.y, at.z, yaw, 0.0F);
         m.setCustomName(
            t.stranger
               ? Msg.t("trade.stranger_name", Msg.t("trade.kind." + t.kind.id()), t.name)
               : Msg.t("trade.merchant_name", Msg.t("trade.kind." + t.kind.id()), t.name, realm == null ? "" : realm.name())
         );
         m.setCustomNameVisible(true);
         m.addTag(TAG);
         m.setVariant(Math.floorMod(t.id.hashCode(), MerchantEntity.VARIANTS));
         t.merchant = m.getUUID();
         t.beasts.clear();
         level.addFreshEntity(m);
         if (ship) {
            ChestBoat boat = EntityType.CHEST_BOAT.create(level);
            if (boat != null) {
               boat.setVariant(Boat.Type.DARK_OAK);
               boat.moveTo(at.x, at.y, at.z, yaw, 0.0F);
               boat.addTag(TAG);
               t.beasts.add(boat.getUUID());
               level.addFreshEntity(boat);
               m.startRiding(boat, true);
            }
         } else {
            Vec3 back = Vec3.directionFromRotation(0.0F, yaw).scale(-1.6);

            for (int i = 0; i < Math.max(1, t.animals); i++) {
               Mule mule = EntityType.MULE.create(level);
               if (mule != null) {
                  Vec3 p = surface(level, at.add(back.scale(i + 1)), false);
                  mule.moveTo(p.x, p.y, p.z, yaw, 0.0F);
                  mule.setChest(true);
                  mule.setPersistenceRequired();
                  mule.addTag(TAG);
                  t.beasts.add(mule.getUUID());
                  level.addFreshEntity(mule);
                  mule.setLeashedTo(m, true);
               }
            }

            if (t.escorted && realm != null) {
               for (int i = 0; i < t.guards; i++) {
                  Vec3 p = surface(level, at.add(back.scale(-1.0 - i)).add(i % 2 == 0 ? 1.2 : -1.2, 0.0, 0.0), false);
                  Entity guard = Hyw.spawn(level, "spear_man", realm.ruler(), BlockPos.containing(p), BlockPos.containing(p), 2, 1);
                  if (guard != null) {
                     guard.addTag(TAG);
                     guard.getPersistentData().putString("prestol_army", "x|escort");
                     Hyw.orders(guard, BlockPos.containing(p), List.of(), m);
                     t.beasts.add(guard.getUUID());
                  }
               }
            }
         }

         pushCargo(server, t);
         t.lastPhysical = at;
         t.stuck = 0;
      }
   }

   /**
    * Removes the merchant, his mules, boat and guards. The trader's record is the truth about the cargo; the
    * chests in the world only show it, and what is lost there (a mule killed, the boat broken) is taken off
    * the record when it happens.
    */
   public static void despawn(MinecraftServer server, Trader t, boolean unused) {
      ServerLevel level = level(server, t.dim);
      if (level != null && t.physical()) {
         Entity m = level.getEntity(t.merchant);
         if (m != null) {
            t.pos = m.getVehicle() != null ? m.getVehicle().position() : m.position();
            m.stopRiding();
            m.discard();
         }

         for (UUID id : t.beasts) {
            Entity e = level.getEntity(id);
            if (e != null) {
               clearChest(e);
               e.ejectPassengers();
               e.discard();
            }
         }
      }

      t.merchant = null;
      t.beasts.clear();
   }

   private static void clearChest(Entity e) {
      if (e instanceof AbstractChestedHorse mule) {
         for (int k = 0; k < Trader.SLOTS_PER_ANIMAL; k++) {
            mule.getSlot(500 + k).set(ItemStack.EMPTY);
         }
      } else if (e instanceof ChestBoat boat) {
         boat.clearContent();
      }
   }

   public static void despawnAll(MinecraftServer server) {
      for (Trader t : TradeData.get(server).traders.values()) {
         despawn(server, t, true);
      }
   }

   /** Rebuilds the caravan in the world after its mules changed. */
   static void refresh(MinecraftServer server, Trader t) {
      if (t.physical()) {
         despawn(server, t, true);
      }
   }

   /** Puts the cargo into the mules' chests or the boat's chest. */
   static void pushCargo(MinecraftServer server, Trader t) {
      ServerLevel level = level(server, t.dim);
      if (level != null && t.physical()) {
         List<ItemStack> left = new ArrayList<>(t.cargo.slots);

         for (UUID id : t.beasts) {
            Entity e = level.getEntity(id);
            if (e instanceof AbstractChestedHorse mule) {
               for (int k = 0; k < Trader.SLOTS_PER_ANIMAL; k++) {
                  mule.getSlot(500 + k).set(left.isEmpty() ? ItemStack.EMPTY : left.remove(0).copy());
               }
            } else if (e instanceof ChestBoat boat) {
               for (int k = 0; k < boat.getContainerSize(); k++) {
                  boat.setItem(k, left.isEmpty() ? ItemStack.EMPTY : left.remove(0).copy());
               }
            }
         }
      }
   }

   /** Every tick while the merchant is in the world: he walks (or sails) toward the next point of his road. */
   private static void moveReal(MinecraftServer server, TradeData data, Trader t, long now) {
      ServerLevel level = level(server, t.dim);
      Entity m = level == null ? null : level.getEntity(t.merchant);
      if (m instanceof MerchantEntity merchant) {
         Entity body = t.kind == Trader.Kind.SHIP && m.getVehicle() != null ? m.getVehicle() : m;
         Vec3 goal = merchant.haggling() ? null : heading(t, now);

         if (goal == null) {
            if (t.kind == Trader.Kind.CARAVAN) {
               merchant.getNavigation().stop();
            } else {
               body.setDeltaMovement(body.getDeltaMovement().multiply(0.8, 1.0, 0.8));
            }

            if (server.getTickCount() % 20 == 7) {
               step(server, data, t, now, body.position());
            }
         } else {
            Vec3 at = body.position();
            double dx = goal.x - at.x;
            double dz = goal.z - at.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (t.kind == Trader.Kind.SHIP) {
               double v = speed(t) / 20.0;
               if (d > 0.5) {
                  float yaw = (float)(Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                  body.setYRot(yaw);
                  body.setDeltaMovement(dx / d * v, body.getDeltaMovement().y, dz / d * v);
               }
            } else if (server.getTickCount() % 10 == 3) {
               double ahead = Math.min(d, 16.0);
               Vec3 step = surface(level, new Vec3(at.x + dx / Math.max(d, 1.0E-6) * ahead, at.y, at.z + dz / Math.max(d, 1.0E-6) * ahead), false);
               double attr = merchant.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
               // a walking mob's speed s (attribute times modifier) carries it about 44·s² blocks a second on grass
               merchant.getNavigation().moveTo(step.x, step.y, step.z, Math.sqrt(speed(t) / 44.0) / Math.max(0.05, attr));
            }

            if (server.getTickCount() % 20 == 7) {
               // stuck for ten seconds: the merchant finds his way round in the books, a few steps further on
               if (t.lastPhysical != null && at.distanceToSqr(t.lastPhysical) < 1.0) {
                  if (++t.stuck >= 10) {
                     t.stuck = 0;
                     Vec3 jump = new Vec3(at.x + dx / Math.max(d, 1.0) * Math.min(d, 6.0), at.y, at.z + dz / Math.max(d, 1.0) * Math.min(d, 6.0));
                     Vec3 to = surface(level, jump, t.kind == Trader.Kind.SHIP);
                     if (t.kind == Trader.Kind.SHIP && !level.getFluidState(BlockPos.containing(to).below()).isSource()) {
                        // aground: the ship sails on out of sight and shows itself again a minute later
                        t.note(Trading.json(Msg.bad("trade.aground", (int)at.x, (int)at.z)));
                        despawn(server, t, true);
                        t.hiddenUntil = server.overworld().getGameTime() + 1200L;
                        return;
                     } else {
                        body.teleportTo(to.x, to.y, to.z);
                        for (UUID id : t.beasts) {
                           Entity e = level.getEntity(id);
                           if (e != null && e != body) {
                              e.teleportTo(to.x, to.y, to.z);
                           }
                        }
                     }
                  }
               } else {
                  t.stuck = 0;
               }

               t.lastPhysical = at;
               step(server, data, t, now, at);
            }
         }
      }
   }

   /** The merchant is dead: whoever killed him takes the goods, and the trader limps home with empty packs. */
   static void onMerchantKilled(ServerLevel level, MerchantEntity merchant, DamageSource source) {
      MinecraftServer server = level.getServer();
      TradeData data = TradeData.get(server);
      Trader t = data.traders.get(merchant.trader);
      if (t != null && merchant.getUUID().equals(t.merchant)) {
         Vec3 at = merchant.position();

         for (ItemStack s : t.cargo.slots) {
            level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, s.copy()));
         }

         for (UUID id : t.beasts) {
            Entity e = level.getEntity(id);
            if (e != null) {
               clearChest(e);
               e.ejectPassengers();
               e.discard();
            }
         }

         t.merchant = null;
         t.beasts.clear();
         t.cargo.slots.clear();
         if (t.stranger) {
            data.traders.remove(t.id);
            return;
         }

         RealmData realms = RealmData.get(server);
         Realm realm = realms.byId(t.realm);
         IColony home = realm == null ? null : Settlements.byKey(realm.capital());
         if (home != null) {
            t.pos = Vec3.atBottomCenterOf(Trading.anchor(home, t.kind));
         }

         t.target = 0;
         t.wp = 0;
         t.leaving = true;
         if (t.auto) {
            AutoTrade.start(server, t);
            t.leaving = true;
         }

         t.dwellUntil = Realms.now(server) + 24000L;
         Entity killer = source.getEntity();
         if (killer == null) {
            // a fall, the water, a fire: nobody to blame
            t.status("lost");
            lost(server, t, Msg.bad("trade.merchant_died", t.name));
         } else {
            t.status("robbed");
            String who = killer instanceof Player p ? p.getGameProfile().getName() : killer.getName().getString();
            if (killer instanceof ServerPlayer robber) {
               Court.robbed(server, t.realm, robber);
            }

            MutableComponent msg = Msg.bad("trade.robbed", t.name, who);
            t.note(Trading.json(msg));
            if (realm != null) {
               Realms.tell(server, realm, msg);
               Realms.chronicle(server, "caravan_robbed", List.of(t.name, who), realm);
            }
         }
      }
   }

   private static void lost(MinecraftServer server, Trader t, MutableComponent msg) {
      t.note(Trading.json(msg));
      Realm r = RealmData.get(server).byId(t.realm);
      if (r != null) {
         Realms.tell(server, r, msg);
      }
   }

   /** A pack mule of a caravan is killed: its chest falls to the ground, and off the trader's books; its killer is a robber. */
   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof AbstractChestedHorse mule && mule.getTags().contains(TAG) && mule.level() instanceof ServerLevel level) {
         for (Trader t : TradeData.get(level.getServer()).traders.values()) {
            if (t.beasts.remove(mule.getUUID())) {
               int slots = 0;

               for (int k = 0; k < Trader.SLOTS_PER_ANIMAL; k++) {
                  ItemStack s = mule.getSlot(500 + k).get();
                  if (!s.isEmpty()) {
                     t.cargo.take(s.getItem(), s.getCount());
                     slots++;
                  }
               }

               if (slots > 0) {
                  lost(level.getServer(), t, Msg.bad("trade.mule_killed", t.name, slots));
               }

               // killing a pack mule for its load is robbery all the same
               if (event.getSource().getEntity() instanceof ServerPlayer robber) {
                  Court.robbed(level.getServer(), t.realm, robber);
               }
            }
         }
      }
   }

   /** Nobody opens the chests of a passing trader or leads his mules away. */
   @SubscribeEvent
   public static void onInteract(PlayerInteractEvent.EntityInteract event) {
      if (event.getTarget().getTags().contains(TAG) && !(event.getTarget() instanceof MerchantEntity)) {
         event.setCanceled(true);
      }
   }

   /** What a player sees when he speaks to the merchant. */
   static MutableComponent card(MinecraftServer server, Trader t) {
      if (t.stranger) {
         MutableComponent c = Msg.t("trade.stranger_card", Msg.t("trade.kind." + t.kind.id()), t.name, Coins.fmt(t.purse));
         return c.append(" ").append(Msg.t("trade.status." + t.status, t.statusArgs.toArray()).withStyle(ChatFormatting.GRAY));
      }

      Realm realm = RealmData.get(server).byId(t.realm);
      MutableComponent c = Msg.t("trade.card", Msg.t("trade.kind." + t.kind.id()), t.name, realm == null ? "?" : realm.name(), t.cargo.used(), t.cargo.size());
      return c.append(" ").append(Msg.t("trade.status." + t.status, t.statusArgs.toArray()).withStyle(ChatFormatting.GRAY));
   }

   /** Mules, boats and merchants of a trader that was not in the world any more are leftovers: they go. */
   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      Entity e = event.getEntity();
      if (!event.getLevel().isClientSide() && e.getTags().contains(TAG) && !claimed(event.getLevel().getServer(), e)) {
         event.setCanceled(true);
      }
   }

   private static boolean claimed(MinecraftServer server, Entity e) {
      if (server == null) {
         return false;
      } else {
         for (Trader t : TradeData.get(server).traders.values()) {
            if (e.getUUID().equals(t.merchant) || t.beasts.contains(e.getUUID())) {
               return true;
            }
         }

         return false;
      }
   }
}
