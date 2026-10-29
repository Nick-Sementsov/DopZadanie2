package com.nick.prestol.army;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingArchery;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCombatAcademy;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobArcherTraining;
import com.minecolonies.core.colony.jobs.JobCombatTraining;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Conquest;
import com.nick.prestol.war.Sieges;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class Army {
   static final String TAG = "prestol_army";
   private static final int RETRY_TICKS = 200;
   private static final Map<String, Integer> STRENGTH = new ConcurrentHashMap<>();
   private static final Map<UUID, String> GIVEN = new ConcurrentHashMap<>();
   private static long nextTraining;
   private static Field patrolTargets;
   private static Field guardPos;
   private static boolean fieldsBroken;

   private Army() {
   }

   public static boolean garrisons() {
      try {
         return (Boolean)PrestolConfig.ARMY_GARRISON.get() && Hyw.present();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static boolean militiaOn() {
      try {
         return (Boolean)PrestolConfig.MILITIA.get() && Hyw.present();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static boolean isTraining(WorkerBuildingModule m) {
      return m.getJobEntry() != null && String.valueOf(m.getJobEntry().getKey()).contains("training");
   }

   public static boolean isPost(IBuilding b) {
      return b instanceof AbstractBuildingGuards;
   }

   public static String postKey(IColony colony, BlockPos pos) {
      return Settlements.key(colony) + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
   }

   static String settlementOf(String postKey) {
      int at = postKey.indexOf(64);
      return at < 0 ? postKey : postKey.substring(0, at);
   }

   static BlockPos posOf(String postKey) {
      String[] p = postKey.substring(postKey.indexOf(64) + 1).split(",");
      return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
   }

   public static int slots(IBuilding b) {
      return b.getBuildingLevel() <= 0 ? 0 : b.getBuildingLevel() * (Integer)PrestolConfig.ARMY_UNITS_PER_LEVEL.get();
   }

   private static UnitType firstKind(IBuilding b) {
      return b instanceof BuildingGuardTower ? UnitType.ARCHER : UnitType.WARRIOR;
   }

   private static BlockPos stand(ServerLevel level, IBuilding b) {
      List<BlockPos> spots = Spots.outside(level, b, 1);
      return spots.isEmpty() ? b.getPosition().above() : spots.get(0);
   }

   private static int tier(int level) {
      return level >= 5 ? 2 : (level >= 3 ? 1 : 0);
   }

   public static int strength(IColony colony) {
      return STRENGTH.getOrDefault(Settlements.key(colony), 0);
   }

   private static long now(MinecraftServer server) {
      return server.overworld().getGameTime();
   }

   public static void tick(MinecraftServer server) {
      if (Hyw.present()) {
         ArmyData data = ArmyData.get(server);
         long now = now(server);
         if (Mercenaries.on() || !data.hired.isEmpty()) {
            Mercenaries.tick(server, data, now, GIVEN);
         }

         Provisions.tick(server, data, 100, now);
         Morale.tick(server, data, 100, now);

         if (garrisons()) {
            if (now >= nextTraining) {
               if (nextTraining > 0L) {
                  train(server, data);
               }

               nextTraining = now + (long)((Integer)PrestolConfig.TRAINING_MINUTES.get()).intValue() * 1200L;
            }

            Set<String> seen = new HashSet<>();
            Map<String, Integer> strength = new HashMap<>();

            for (IColony colony : Settlements.all()) {
               ServerLevel level = server.getLevel(colony.getDimension());
               if (level != null) {
                  String settlement = Settlements.key(colony);
                  UUID owner = Settlements.owner(colony);
                  boolean freed = freeGuards(colony);
                  Map<ResourceLocation, Double> bonus = null;

                  for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
                     if (isPost(b)) {
                        String key = postKey(colony, b.getPosition());
                        seen.add(key);
                        ArmyData.Post p = data.post(key, firstKind(b));
                        if (owner != null && !owner.equals(p.owner)) {
                           // the old owner's garrison lays down its arms instead of staying in the town as a stray squad
                           for (UUID u : new ArrayList<>(p.units)) {
                              dismiss(server, data, u);
                           }

                           p.units.clear();
                           p.owner = owner;
                           p.nextAt = now;
                           data.setDirty();
                        }

                        int slots = slots(b);

                        while (p.units.size() > slots) {
                           UUID last = new ArrayList<>(p.units).get(p.units.size() - 1);
                           p.units.remove(last);
                           dismiss(server, data, last);
                           data.setDirty();
                        }

                        strength.merge(settlement, p.units.size(), Integer::sum);
                        command(level, colony, b, p);
                        if (!p.units.isEmpty()) {
                           if (bonus == null) {
                              bonus = DecreeBonus.of(server, colony);
                           }

                           for (UUID id : p.units) {
                              Entity unit = level.getEntity(id);
                              if (unit != null) {
                                 DecreeBonus.apply(unit, bonus);
                              }
                           }
                        }
                        if (owner != null && p.units.size() < slots && now >= p.nextAt && !Sieges.isBesieged(settlement) && level.isLoaded(b.getPosition())) {
                           recruit(level, colony, b, key, p, data, now);
                        }
                     }
                  }

                  if (freed && data.guardsFreed.add(settlement)) {
                     data.setDirty();
                     Realms.tellColony(server, colony, Msg.info("army.guards_freed", colony.getName()));
                     Prestol.LOGGER.info("Prestol: the MineColonies guards of {} were sent to other work; garrisons keep watch", colony.getName());
                  }
               }
            }

            Iterator<Entry<String, ArmyData.Post>> it = data.posts.entrySet().iterator();

            while (it.hasNext()) {
               Entry<String, ArmyData.Post> e = it.next();
               // a post is gone only when its colony is loaded and the tower is not there any more;
               // a colony that failed to load keeps its garrison
               if (!seen.contains(e.getKey()) && Settlements.byKey(settlementOf(e.getKey())) != null) {
                  it.remove();

                  for (UUID u : new ArrayList<>(e.getValue().units)) {
                     dismiss(server, data, u);
                  }

                  data.setDirty();
               }
            }

            STRENGTH.clear();
            STRENGTH.putAll(strength);
         }
      }
   }

   private static void command(ServerLevel level, IColony colony, IBuilding b, ArmyData.Post p) {
      if (!p.units.isEmpty() && b instanceof AbstractBuildingGuards g) {
         String task = g.getTask();
         BlockPos tower = stand(level, b);
         BlockPos home = tower;
         ArrayList route = new ArrayList();
         Player follow = null;
         if ("com.minecolonies.core.guard.setting.follow".equals(task)) {
            follow = g.getPlayerToFollowOrRally();
            if (follow != null && follow.level() != level) {
               follow = null;
            }
         } else if ("com.minecolonies.core.guard.setting.guard".equals(task)) {
            BlockPos spot = field(guardPos(), g);
            if (spot != null) {
               home = spot;
            }
         } else if ("com.minecolonies.core.guard.setting.patrol_mine".equals(task) && g.getMinePos() != null) {
            route.add(tower);
            route.add(g.getMinePos());
         } else {
            route.add(tower);
            List<BlockPos> manual = field(patrolTargets(), g);
            if (g.shallPatrolManually() && manual != null && !manual.isEmpty()) {
               route.addAll(manual);
            } else {
               IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
               if (hall != null && hall != b) {
                  route.add(stand(level, hall));
               }
            }
         }

         List<BlockPos> var14 = Spots.densify(level, route, 24);
         String orders = home.asLong() + "|" + var14 + "|" + (follow == null ? "" : follow.getUUID());

         for (UUID id : p.units) {
            Entity unit = level.getEntity(id);
            if (unit != null && !orders.equals(GIVEN.get(id)) && Hyw.orders(unit, home, var14, follow)) {
               GIVEN.put(id, orders);
            }
         }
      }
   }

   private static Field patrolTargets() {
      if (patrolTargets == null && !fieldsBroken) {
         findFields();
      }

      return patrolTargets;
   }

   private static Field guardPos() {
      if (guardPos == null && !fieldsBroken) {
         findFields();
      }

      return guardPos;
   }

   private static void findFields() {
      try {
         patrolTargets = AbstractBuildingGuards.class.getDeclaredField("patrolTargets");
         patrolTargets.setAccessible(true);
         guardPos = AbstractBuildingGuards.class.getDeclaredField("guardPos");
         guardPos.setAccessible(true);
      } catch (RuntimeException | ReflectiveOperationException var1) {
         fieldsBroken = true;
         Prestol.LOGGER.warn("Prestol: cannot read the guard towers' patrol points; garrisons patrol to the town hall", var1);
      }
   }

   private static <T> T field(Field f, Object owner) {
      try {
         return (T)(f == null ? null : f.get(owner));
      } catch (RuntimeException | ReflectiveOperationException var3) {
         return null;
      }
   }

   private static String task(IBuilding b) {
      String t = b instanceof AbstractBuildingGuards g ? g.getTask() : "";
      if ("com.minecolonies.core.guard.setting.guard".equals(t)) {
         return "guard";
      } else if ("com.minecolonies.core.guard.setting.follow".equals(t)) {
         return "follow";
      } else {
         return "com.minecolonies.core.guard.setting.patrol_mine".equals(t) ? "patrol_mine" : "patrol";
      }
   }

   private static boolean freeGuards(IColony colony) {
      boolean any = false;

      for (ICitizenData c : new ArrayList<>(colony.getCitizenManager().getCitizens())) {
         if (c.getJob() instanceof AbstractJobGuard || c.getJob() instanceof JobArcherTraining || c.getJob() instanceof JobCombatTraining) {
            any = true;
            IBuilding work = c.getWorkBuilding();
            boolean let = false;
            if (work != null) {
               for (WorkerBuildingModule m : work.getModulesByType(WorkerBuildingModule.class)) {
                  if (m.hasAssignedCitizen(c)) {
                     let |= m.removeCitizen(c);
                  }
               }
            }

            if (!let && c.getJob() != null) {
               c.getJob().onRemoval();
               if (work != null) {
                  work.cancelAllRequestsOfCitizenOrBuilding(c);
               }

               c.setJob(null);
            }
         }
      }

      return any;
   }

   private static void recruit(ServerLevel level, IColony colony, IBuilding b, String key, ArmyData.Post p, ArmyData data, long now) {
      List<Supplies.Need> needs = Supplies.needs(p.type);
      List<IItemHandler> stores = Supplies.stores(colony, b);
      List<String> missing = new ArrayList<>(Supplies.missing(stores, needs).stream().map(n -> n.name()).toList());
      ICitizenData recruit = Conscripts.on() ? Conscripts.pick(level.getServer(), colony) : null;
      if (Conscripts.on() && recruit == null) {
         missing.add(0, "CITIZEN");
      }

      if (!missing.isEmpty()) {
         String lack = String.join(",", missing);
         if (!lack.equals(p.lack)) {
            p.lack = lack;
            data.setDirty();
         }

         p.nextAt = now + 200L;
      } else {
         BlockPos at = stand(level, b);
         Entity unit = Hyw.spawn(level, p.type.entity, p.owner, at, at, b.getBuildingLevel(), tier(b.getBuildingLevel()));
         if (unit == null) {
            p.lack = "SPAWN";
            p.nextAt = now + 600L;
            data.setDirty();
         } else if (!Supplies.take(stores, needs)) {
            unit.discard();
            p.nextAt = now + 200L;
         } else {
            unit.getPersistentData().putString("prestol_army", "g|" + key);
            Provisions.fill(unit);
            if (recruit != null) {
               String name = recruit.getName();
               data.conscripts.put(unit.getUUID(), new ArmyData.Conscript(Settlements.key(colony), Conscripts.enlist(colony, recruit)));
               unit.setCustomName(Conscripts.soldierName(name, p.type));
               Realms.tellColony(level.getServer(), colony, Msg.info("army.conscripted", name, colony.getName()));
            }

            p.units.add(unit.getUUID());
            p.lack = "";
            p.nextAt = now + (long)((Integer)PrestolConfig.ARMY_REFILL_SECONDS.get()).intValue() * 20L;
            data.setDirty();
         }
      }
   }

   /** A starving garrison soldier leaves his post; a recruit goes back to his colony. */
   static void desert(MinecraftServer server, ArmyData data, Entity unit) {
      String tag = unit.getPersistentData().getString("prestol_army");
      if (tag.startsWith("g|")) {
         ArmyData.Post p = data.peek(tag.substring(2));
         if (p != null && p.units.remove(unit.getUUID())) {
            p.nextAt = Math.max(p.nextAt, now(server) + (long)PrestolConfig.ARMY_REFILL_SECONDS.get() * 20L);
         }
      }

      dismiss(server, data, unit.getUUID());
      data.setDirty();
   }

   public static void inspect(MinecraftServer server) {
      if (Hyw.present() && (Provisions.on() || Morale.on())) {
         Provisions.inspect(server);
      }
   }

   static void dismiss(MinecraftServer server, ArmyData data, UUID id) {
      ArmyData.Conscript conscript = data.conscripts.remove(id);

      for (ServerLevel l : server.getAllLevels()) {
         Entity e = l.getEntity(id);
         if (e != null) {
            if (conscript != null) {
               data.setDirty();
               Conscripts.discharge(server, conscript, e.blockPosition());
            }

            e.discard();
            return;
         }
      }

      if (conscript != null) {
         IColony colony = Settlements.byKey(conscript.settlement());
         Conscripts.discharge(server, conscript, colony == null ? BlockPos.ZERO : colony.getCenter());
      }

      data.dismissed.add(id);
      data.setDirty();
   }

   public static Realms.Result order(MinecraftServer server, ServerPlayer player, String postKey, String kind) {
      UnitType type = UnitType.byId(kind);
      IColony colony = Settlements.byKey(settlementOf(postKey));
      if (type != null && UnitType.GARRISON.contains(type) && colony != null) {
         IBuilding b = (IBuilding)colony.getServerBuildingManager().getBuilding(posOf(postKey));
         if (b == null || !isPost(b)) {
            return Realms.Result.no(Msg.bad("army.no_post"));
         } else if (!mayCommand(server, player, colony)) {
            return Realms.Result.no(Msg.bad("err.not_your_settlement"));
         } else {
            ArmyData data = ArmyData.get(server);
            ArmyData.Post p = data.post(postKey, firstKind(b));
            p.type = type;
            p.lack = "";
            p.nextAt = Math.min(p.nextAt, now(server));
            data.setDirty();
            return Realms.Result.yes(Msg.good("army.kind_set", colony.getName(), Component.translatable(type.langKey())));
         }
      } else {
         return Realms.Result.no(Msg.bad("err.unknown"));
      }
   }

   private static boolean mayCommand(MinecraftServer server, ServerPlayer player, IColony colony) {
      if (Settlements.isManager(player, colony)) {
         return true;
      } else {
         Realm realm = RealmData.get(server).byMember(Settlements.key(colony));
         return realm != null && Realms.isRuler(realm, player);
      }
   }

   public static ListTag describe(MinecraftServer server, ServerPlayer player) {
      ListTag out = new ListTag();
      if (!garrisons()) {
         return out;
      } else {
         ArmyData data = ArmyData.get(server);
         long now = now(server);

         for (IColony colony : Settlements.all()) {
            if (mayCommand(server, player, colony)) {
               for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
                  if (isPost(b)) {
                     String key = postKey(colony, b.getPosition());
                     ArmyData.Post p = data.peek(key);
                     CompoundTag t = new CompoundTag();
                     t.putString("key", key);
                     t.putString("settlement", colony.getName());
                     t.putString("building", b instanceof BuildingGuardTower ? "guardtower" : "barrackstower");
                     t.putInt("level", b.getBuildingLevel());
                     t.putInt("slots", slots(b));
                     t.putInt("have", p == null ? 0 : p.units.size());
                     t.putString("type", (p == null ? firstKind(b) : p.type).id());
                     if (p != null && p.nextAt > now) {
                        t.putInt("wait", (int)((p.nextAt - now) / 20L));
                     }

                     ListTag lack = new ListTag();
                     if (p != null && !p.lack.isEmpty()) {
                        for (String n : p.lack.split(",")) {
                           lack.add(StringTag.valueOf(n));
                        }
                     }

                     t.put("lack", lack);
                     t.putBoolean("siege", Sieges.isBesieged(Settlements.key(colony)));
                     t.putString("task", task(b));
                     UnitType kind = p == null ? firstKind(b) : p.type;
                     int school = school(colony, kind);
                     t.putString("school", ranged(kind) ? "archery" : "academy");
                     t.putInt("schoolLevel", school);
                     t.putInt("cap", cap(b, school));
                     int low = Integer.MAX_VALUE;
                     int high = -1;
                     ServerLevel level = server.getLevel(colony.getDimension());

                     for (UUID id : p == null ? Set.<UUID>of() : p.units) {
                        Entity unit = level == null ? null : level.getEntity(id);
                        int rank = unit == null ? -1 : Hyw.rank(unit);
                        if (rank >= 0) {
                           low = Math.min(low, rank);
                           high = Math.max(high, rank);
                        }
                     }

                     if (high >= 0) {
                        t.putInt("rankLow", low);
                        t.putInt("rankHigh", high);
                     }

                     if (Provisions.on()) {
                        double food = 0.0;
                        int fed = 0;

                        for (UUID id : p == null ? Set.<UUID>of() : p.units) {
                           Entity unit = level == null ? null : level.getEntity(id);
                           if (unit != null) {
                              food += Provisions.get(unit);
                              fed++;
                           }
                        }

                        t.putInt("food", fed == 0 ? 100 : (int)Math.round(food / fed));
                        int daily = PrestolConfig.DAILY_RATION.get() * Math.max(1, p == null ? 0 : p.units.size());
                        t.putInt("stockDays", Supplies.foodIn(Supplies.stores(colony, b)) / daily);
                     }

                     if (Morale.on()) {
                        double spirit = 0.0;
                        int seen = 0;

                        for (UUID id : p == null ? Set.<UUID>of() : p.units) {
                           Entity unit = level == null ? null : level.getEntity(id);
                           if (unit != null) {
                              spirit += Morale.get(unit);
                              seen++;
                           }
                        }

                        if (seen > 0) {
                           t.putInt("morale", (int)Math.round(spirit / seen));
                        }
                     }

                     out.add(t);
                  }
               }
            }
         }

         return out;
      }
   }

   private static boolean ranged(UnitType t) {
      return t == UnitType.ARCHER || t == UnitType.CROSSBOWMAN;
   }

   private static int school(IColony colony, UnitType kind) {
      int best = 0;

      for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
         if (ranged(kind) ? b instanceof BuildingArchery : b instanceof BuildingCombatAcademy) {
            best = Math.max(best, b.getBuildingLevel());
         }
      }

      return best;
   }

   private static int cap(IBuilding post, int school) {
      return post.getBuildingLevel() + 2 * school;
   }

   private static void train(MinecraftServer server, ArmyData data) {
      int taught = 0;

      for (IColony colony : Settlements.all()) {
         ServerLevel level = server.getLevel(colony.getDimension());
         if (level != null) {
            for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
               ArmyData.Post p = isPost(b) ? data.peek(postKey(colony, b.getPosition())) : null;
               if (p != null && !p.units.isEmpty()) {
                  int school = school(colony, p.type);
                  if (school > 0) {
                     int cap = cap(b, school);

                     for (UUID id : p.units) {
                        Entity unit = level.getEntity(id);
                        int rank = unit == null ? -1 : Hyw.rank(unit);
                        if (rank >= 0 && rank < cap) {
                           Hyw.rankUp(unit);
                           taught++;
                        }
                     }
                  }
               }
            }
         }
      }

      if (taught > 0) {
         Prestol.LOGGER.debug("Prestol: the schools trained {} garrison soldiers", taught);
      }
   }

   public static int militiaSize(IColony colony) {
      if (militiaOn() && !Conquest.enslaved(colony)) {
         int adults = 0;

         for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
            if (!c.isChild()) {
               adults++;
            }
         }
         int cap = switch (Settlements.tierOf(colony)) {
            case HAMLET -> PrestolConfig.MILITIA_MAX_HAMLET.get();
            case VILLAGE -> PrestolConfig.MILITIA_MAX_VILLAGE.get();
            case TOWN -> PrestolConfig.MILITIA_MAX_TOWN.get();
         };
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         Realm realm = server == null ? null : RealmData.get(server).byMember(Settlements.key(colony));
         if (realm != null && Settlements.key(colony).equals(realm.capital())) {
            cap = cap * (100 + (Integer)PrestolConfig.MILITIA_CAPITAL_PERCENT.get()) / 100;
         }

         return Math.min(cap, adults / (Integer)PrestolConfig.MILITIA_CITIZENS.get());
      } else {
         return 0;
      }
   }

   public static int militiaUp(MinecraftServer server, String settlement) {
      int n = 0;

      for (ArmyData.Levy l : ArmyData.get(server).militia.values()) {
         if (l.settlement().equals(settlement)) {
            n++;
         }
      }

      return n;
   }

   public static void muster(MinecraftServer server, IColony colony) {
      String settlement = Settlements.key(colony);
      int size = militiaSize(colony);
      UUID owner = Settlements.owner(colony);
      ServerLevel level = server.getLevel(colony.getDimension());
      if (size > 0 && owner != null && level != null && militiaUp(server, settlement) <= 0) {
         IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
         BlockPos home = hall != null ? hall.getPosition() : colony.getCenter();
         int tier = tier(hall == null ? 1 : hall.getBuildingLevel());
         List<ICitizenData> adults = new ArrayList<>();

         for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
            if (!c.isChild()) {
               adults.add(c);
            }
         }

         Collections.shuffle(adults);
         List<BlockPos> spots = hall != null ? Spots.outside(level, hall, size) : Spots.around(level, home, size);
         ArmyData data = ArmyData.get(server);
         int raised = 0;

         for (int i = 0; i < size && i < adults.size(); i++) {
            BlockPos at = spots.isEmpty() ? home.above() : spots.get(i % spots.size());
            Entity unit = Hyw.spawn(level, UnitType.MILITIA.entity, owner, at, at, 1, tier);
            if (unit == null) {
               break;
            }

            ICitizenData cx = adults.get(i);
            unit.getPersistentData().putString("prestol_army", "m|" + settlement + "|" + cx.getId());
            data.militia.put(unit.getUUID(), new ArmyData.Levy(settlement, cx.getId()));
            raised++;
         }

         if (raised > 0) {
            data.setDirty();
            Realms.tellAll(server, Msg.war("militia.raised", colony.getName(), raised));
         }
      }
   }

   public static void disband(MinecraftServer server, String settlement) {
      ArmyData data = ArmyData.get(server);
      int gone = 0;
      Iterator<Entry<UUID, ArmyData.Levy>> it = data.militia.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, ArmyData.Levy> e = it.next();
         if (e.getValue().settlement().equals(settlement)) {
            it.remove();
            dismiss(server, data, e.getKey());
            gone++;
         }
      }

      if (gone > 0) {
         data.setDirty();
         IColony colony = Settlements.byKey(settlement);
         if (colony != null) {
            Realms.tellColony(server, colony, Msg.info("militia.disbanded", colony.getName()));
         }
      }
   }

   private static void fallen(MinecraftServer server, ArmyData.Levy levy) {
      IColony colony = Settlements.byKey(levy.settlement());
      if (colony != null) {
         ICitizenData citizen = colony.getCitizenManager().getCivilian(levy.citizen());
         if (citizen != null) {
            String name = citizen.getName();
            citizen.getEntity().ifPresent(Entity::discard);
            colony.getCitizenManager().removeCivilian(citizen);
            Component msg = Msg.bad("militia.fallen", name, colony.getName());
            Realm realm = RealmData.get(server).byMember(levy.settlement());
            if (realm != null) {
               Realms.tell(server, realm, msg);
            } else {
               Realms.tellColony(server, colony, msg);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      Morale.onDeath(event);
      Entity e = event.getEntity();
      String tag = e.getPersistentData().getString("prestol_army");
      MinecraftServer server = e.getServer();
      if (!tag.isEmpty() && server != null && !e.level().isClientSide()) {
         ArmyData data = ArmyData.get(server);
         if (tag.startsWith("g|")) {
            ArmyData.Post p = data.peek(tag.substring(2));
            if (p != null && p.units.remove(e.getUUID())) {
               p.nextAt = Math.max(p.nextAt, now(server) + (long)((Integer)PrestolConfig.ARMY_REFILL_SECONDS.get()).intValue() * 20L);
               data.setDirty();
            }

            ArmyData.Conscript fallen = data.conscripts.remove(e.getUUID());
            if (fallen != null) {
               data.setDirty();
               Conscripts.fallen(server, fallen);
            }
         } else if (tag.startsWith("m|")) {
            ArmyData.Levy levy = data.militia.remove(e.getUUID());
            if (levy != null) {
               data.setDirty();
               fallen(server, levy);
            }
         } else if (tag.startsWith("x|") && data.hired.remove(e.getUUID()) != null) {
            data.setDirty();
         }
      }
   }

   @SubscribeEvent
   public static void onLeave(EntityLeaveLevelEvent event) {
      Entity e = event.getEntity();
      GIVEN.remove(e.getUUID());
      RemovalReason why = e.getRemovalReason();
      if (!event.getLevel().isClientSide() && why != null && why.shouldDestroy() && why != RemovalReason.KILLED) {
         String tag = e.getPersistentData().getString("prestol_army");
         MinecraftServer server = e.getServer();
         if (!tag.isEmpty() && server != null) {
            ArmyData data = ArmyData.get(server);
            if (tag.startsWith("g|")) {
               ArmyData.Post p = data.peek(tag.substring(2));
               if (p != null && p.units.remove(e.getUUID())) {
                  data.setDirty();
               }

               // removed by something other than us (a command, another mod): the recruit still goes home
               ArmyData.Conscript conscript = data.conscripts.remove(e.getUUID());
               if (conscript != null) {
                  data.setDirty();
                  Conscripts.discharge(server, conscript, e.blockPosition());
               }
            } else if (data.militia.remove(e.getUUID()) != null | data.hired.remove(e.getUUID()) != null) {
               data.setDirty();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onJoin(EntityJoinLevelEvent event) {
      Entity e = event.getEntity();
      Level level = event.getLevel();
      if (!level.isClientSide()) {
         Morale.teachFlight(e);
      }

      if (!level.isClientSide() && e.getPersistentData().contains("prestol_army")) {
         MinecraftServer server = level.getServer();
         if (server != null) {
            ArmyData data = ArmyData.get(server);
            String tag = e.getPersistentData().getString("prestol_army");
            UUID id = e.getUUID();
            if (data.dismissed.remove(id)) {
               data.setDirty();
               event.setCanceled(true);
            } else {
               if (tag.startsWith("m|")) {
                  ArmyData.Levy levy = data.militia.get(id);
                  if (levy == null || !Sieges.isBesieged(levy.settlement())) {
                     if (data.militia.remove(id) != null) {
                        data.setDirty();
                     }

                     event.setCanceled(true);
                  }
               } else if (tag.startsWith("x|")) {
                  ArmyData.Hire hire = data.hired.get(id);
                  if (hire == null || now(server) >= hire.until()) {
                     if (data.hired.remove(id) != null) {
                        data.setDirty();
                     }

                     event.setCanceled(true);
                  }
               } else if (tag.startsWith("g|")) {
                  ArmyData.Post p = data.peek(tag.substring(2));
                  if (p == null || !p.units.contains(id)) {
                     e.getPersistentData().remove("prestol_army");
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onInteract(EntityInteract event) {
      keep(event, event.getTarget());
   }

   @SubscribeEvent
   public static void onInteractAt(EntityInteractSpecific event) {
      keep(event, event.getTarget());
   }

   private static void keep(PlayerInteractEvent event, Entity target) {
      if (event.getEntity().isShiftKeyDown() && target.getPersistentData().contains("prestol_army") && !event.getLevel().isClientSide()) {
         event.setCanceled(true);
         event.setCancellationResult(InteractionResult.FAIL);
         event.getEntity().displayClientMessage(Msg.bad("army.no_recall"), true);
      }
   }

   public static void onStart(MinecraftServer server) {
      // militia stands only while its town is besieged; sieges come back after a restart, their militia too
      ArmyData data = ArmyData.get(server);
      Iterator<Entry<UUID, ArmyData.Levy>> it = data.militia.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, ArmyData.Levy> e = it.next();
         if (!Sieges.isBesieged(e.getValue().settlement())) {
            data.dismissed.add(e.getKey());
            it.remove();
            data.setDirty();
         }
      }
   }

   public static void clear() {
      STRENGTH.clear();
      GIVEN.clear();
      nextTraining = 0L;
   }

   public static List<String> debug(MinecraftServer server) {
      ArmyData data = ArmyData.get(server);
      List<String> out = new ArrayList<>();
      data.posts
         .forEach(
            (k, p) -> out.add(
                  "post "
                     + k
                     + " "
                     + p.type.id()
                     + " "
                     + p.units.size()
                     + " units, next in "
                     + Math.max(0L, (p.nextAt - now(server)) / 20L)
                     + " s"
                     + (p.lack.isEmpty() ? "" : ", lacks " + p.lack)
               )
         );
      data.militia.forEach((u, l) -> out.add("militia " + u.toString().substring(0, 8) + " of " + l.settlement() + " is citizen " + l.citizen()));
      out.add("dismissed " + data.dismissed.size() + ", strength " + STRENGTH);
      return out;
   }
}
