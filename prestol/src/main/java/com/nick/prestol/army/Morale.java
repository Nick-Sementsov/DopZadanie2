package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.land.Territory;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Sieges;
import com.nick.prestol.war.Wars;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Morale: besides his provisions every soldier of a player has a spirit (0-100). It drifts towards what his
 * situation deserves (fed or hungry, paid or not, on his realm's land or deep in an enemy's, with his lord beside
 * him or alone, a veteran or a raw recruit, outnumbered or not) and jumps when comrades or enemies fall around him.
 * High spirit hits harder and low spirit softer; a soldier whose spirit breaks flees the fight and falls back to his
 * post or his lord until he rallies, and holds no ground in a siege meanwhile. A garrison left unpaid and broken
 * deserts.
 */
public final class Morale {
   static final String KEY = "prestol_morale";
   static final String ROUT = "prestol_rout";
   private static final double START = 70.0;
   /** Points the spirit moves towards its calm level every 100 ticks: it breaks faster than it heals. */
   private static final double RECOVER = 4.0;
   private static final double SINK = 8.0;
   private static final double HIGH = 80.0;
   private static final double LOW = 40.0;
   /** Where a routed soldier's spirit stands once he rallies. */
   private static final double RALLY = 40.0;
   private static final int ROUT_TICKS = 600;
   private static final double NEAR = 16.0;
   private static final double LORD_NEAR = 24.0;
   private static final int UNPAID_DESERT_DAYS = 3;
   private static final double DESERT_BELOW = 15.0;
   private static final UUID MELEE = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a11");
   private static final UUID RANGED = UUID.fromString("7d0f0b8e-5a5c-4f43-9f2e-3a1d9c6e1a12");

   private Morale() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.MORALE.get() && Hyw.present();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static double get(Entity unit) {
      return unit.getPersistentData().contains(KEY) ? unit.getPersistentData().getDouble(KEY) : START;
   }

   static void set(Entity unit, double value) {
      unit.getPersistentData().putDouble(KEY, Mth.clamp(value, 0.0, 100.0));
   }

   /** A routed soldier picks no fights and holds no ground. */
   public static boolean routed(Entity unit) {
      return unit.getPersistentData().getLong(ROUT) > unit.level().getGameTime();
   }

   /** Runs every {@code interval} ticks over every loaded soldier of a player. */
   static void tick(MinecraftServer server, ArmyData data, int interval, long now) {
      if (on()) {
         Context cx = new Context(server);
         Map<UUID, Integer> fled = new HashMap<>();
         Map<UUID, Integer> deserted = new HashMap<>();

         for (ServerLevel level : server.getAllLevels()) {
            List<Entity> soldiers = new ArrayList<>();

            for (Entity e : level.getAllEntities()) {
               if (Hyw.eater(e)) {
                  soldiers.add(e);
               }
            }

            for (Entity unit : soldiers) {
               UUID owner = ((OwnableEntity)unit).getOwnerUUID();
               int[] odds = odds(cx, level, unit, owner);
               double calm = calm(cx, level, unit, owner, odds);
               double m = get(unit);
               m = m < calm ? Math.min(calm, m + RECOVER * interval / 100.0) : Math.max(calm, m - SINK * interval / 100.0);
               long rout = unit.getPersistentData().getLong(ROUT);
               if (rout > 0L && rout <= now) {
                  unit.getPersistentData().remove(ROUT);
                  m = Math.max(m, RALLY);
               }

               if (shake(unit, m - get(unit), now, odds[1] > 0)) {
                  fled.merge(owner, 1, Integer::sum);
               }

               effects(unit, m, now);
               String tag = unit.getPersistentData().getString("prestol_army");
               if (tag.startsWith("g|") && m < DESERT_BELOW && cx.unpaid(owner) >= UNPAID_DESERT_DAYS) {
                  Army.desert(server, data, unit);
                  deserted.merge(owner, 1, Integer::sum);
               }
            }
         }

         fled.forEach((owner, n) -> tell(server, owner, Msg.bad("army.routed", n)));
         deserted.forEach((owner, n) -> tell(server, owner, Msg.bad("army.deserted_unpaid", n)));
      }
   }

   /** Moves a soldier's spirit by {@code delta}; true when it breaks in battle and he flees. */
   private static boolean shake(Entity unit, double delta, long now, boolean battle) {
      double m = Mth.clamp(get(unit) + delta, 0.0, 100.0);
      set(unit, m);
      if (battle && m < PrestolConfig.ROUT_BELOW.get() && unit.getPersistentData().getLong(ROUT) == 0L) {
         unit.getPersistentData().putLong(ROUT, now + ROUT_TICKS);
         return true;
      } else {
         return false;
      }
   }

   /** The spirit a soldier's situation deserves; his own drifts towards it. */
   private static double calm(Context cx, ServerLevel level, Entity unit, UUID owner, int[] odds) {
      String tag = unit.getPersistentData().getString("prestol_army");
      double m = 50.0;
      // militia stands for a few minutes and mercenaries bring their own bread
      if (Provisions.on() && !tag.startsWith("m|") && !tag.startsWith("x|")) {
         double food = Provisions.get(unit);
         m += food >= 50.0 ? 10.0 : (food <= 0.0 ? -30.0 : (food < 25.0 ? -15.0 : 0.0));
      }

      if (tag.startsWith("g|") && Treasury.on()) {
         int unpaid = cx.unpaid(owner);
         m += unpaid == 0 ? 5.0 : -12.0 * Math.min(6, unpaid);
      }

      Realm mine = cx.realm(owner);
      Realm land = cx.landRealm(level, unit);
      if (land != null && mine != null && (land == mine || Wars.allied(land, mine))) {
         m += 10.0;
      } else if (land != null && mine != null && Wars.atWar(cx.realms, land, mine)) {
         m -= 10.0;
      }

      ServerPlayer lord = cx.server.getPlayerList().getPlayer(owner);
      if (lord != null && lord.isAlive() && lord.level() == level && lord.distanceTo(unit) <= LORD_NEAR) {
         m += 10.0;
      }

      m += Math.min(10.0, 2.0 * Math.max(0, Hyw.rank(unit) - 1));
      int friends = odds[0];
      int foes = odds[1];
      if (foes > 2 * friends) {
         m -= 25.0;
      } else if (2 * foes > 3 * friends) {
         m -= 10.0;
      } else if (foes > friends) {
         m -= 5.0;
      } else if (foes > 0 && friends >= 2 * foes) {
         m += 5.0;
      }

      return Mth.clamp(m, 0.0, 100.0);
   }

   /** Comrades (himself included) and foes within reach: a foe is whoever fights his side, or whom his side fights. */
   private static int[] odds(Context cx, ServerLevel level, Entity unit, UUID owner) {
      int friends = 0;
      Set<Entity> foes = new HashSet<>();

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, unit.getBoundingBox().inflate(NEAR), LivingEntity::isAlive)) {
         if (e instanceof Mob mob && Hyw.eater(e) && cx.sameSide(owner, ((OwnableEntity)e).getOwnerUUID())) {
            friends++;
            LivingEntity t = mob.getTarget();
            if (t != null && t.isAlive() && t.distanceTo(unit) <= NEAR) {
               foes.add(t);
            }
         } else if (e instanceof Mob mob && mob.getTarget() != null && friendOf(cx, mob.getTarget(), owner)) {
            foes.add(e);
         }
      }

      return new int[]{friends, foes.size()};
   }

   private static boolean friendOf(Context cx, Entity e, UUID owner) {
      if (e instanceof Player p) {
         return cx.sameSide(owner, p.getUUID());
      } else {
         return Hyw.eater(e) && cx.sameSide(owner, ((OwnableEntity)e).getOwnerUUID());
      }
   }

   private static void effects(Entity e, double m, long now) {
      if (e instanceof LivingEntity unit) {
         double bonus = m >= HIGH ? 0.1 : (m < LOW ? -0.15 : 0.0);
         DecreeBonus.set(unit, Attributes.ATTACK_DAMAGE, MELEE, "prestol_morale_melee", bonus, Operation.MULTIPLY_TOTAL);
         Attribute ranged = Hyw.rangedDamage();
         if (ranged != null) {
            DecreeBonus.set(unit, ranged, RANGED, "prestol_morale_ranged", bonus, Operation.MULTIPLY_TOTAL);
         }

         if (unit.getPersistentData().getLong(ROUT) > now) {
            if (unit instanceof Mob mob) {
               mob.setTarget(null);
            }

            unit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 1, false, false));
         }
      }
   }

   /** Comrades lose heart when one of them falls beside them and their foes take it, and the other way round. */
   static void onDeath(LivingDeathEvent event) {
      LivingEntity dead = event.getEntity();
      MinecraftServer server = dead.getServer();
      if (on() && server != null && dead.level() instanceof ServerLevel level) {
         UUID side;
         boolean lord = dead instanceof Player;
         if (lord) {
            side = dead.getUUID();
         } else {
            if (!Hyw.soldier(dead)) {
               return;
            }

            side = ((OwnableEntity)dead).getOwnerUUID();
         }

         Context cx = new Context(server);
         double range = lord ? 32.0 : NEAR;
         long now = level.getGameTime();
         LivingEntity killer = dead.getLastHurtByMob();
         Map<UUID, Integer> fled = new HashMap<>();

         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(range), x -> x != dead && Hyw.eater(x))) {
            UUID owner = ((OwnableEntity)e).getOwnerUUID();
            double delta = 0.0;
            if (lord && side.equals(owner)) {
               delta = -25.0;
            } else if (!lord && cx.sameSide(owner, side) && e.distanceTo(dead) <= NEAR) {
               // veterans have seen it before
               double steady = Math.min(0.5, 0.06 * Math.max(0, Hyw.rank(e) - 1));
               delta = -10.0 * (1.0 - steady);
            } else if (!lord && e.distanceTo(dead) <= NEAR && killer != null && friendOf(cx, killer, owner)) {
               delta = 3.0;
            }

            if (delta != 0.0 && shake(e, delta, now, true)) {
               fled.merge(owner, 1, Integer::sum);
            }
         }

         fled.forEach((owner, n) -> tell(server, owner, Msg.bad("army.routed", n)));
      }
   }

   /** Gives a soldier the flight he takes when his spirit breaks: above all his other goals, it holds his legs. */
   static void teachFlight(Entity e) {
      if (e instanceof PathfinderMob mob && Hyw.fighter(e)) {
         mob.goalSelector.addGoal(0, new Flight(mob));
      }
   }

   /** A routed soldier runs from the foes around him; once none is near, his own AI takes him home or to his lord. */
   static final class Flight extends Goal {
      private final PathfinderMob mob;
      private Vec3 from;
      private int next;

      Flight(PathfinderMob mob) {
         this.mob = mob;
         this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
      }

      @Override
      public boolean canUse() {
         return routed(this.mob) && (this.from = this.danger()) != null;
      }

      @Override
      public boolean canContinueToUse() {
         return routed(this.mob) && this.from != null;
      }

      @Override
      public void start() {
         this.next = 0;
      }

      @Override
      public void tick() {
         if (--this.next <= 0) {
            this.next = 20;
            this.from = this.danger();
            if (this.from != null) {
               Vec3 to = DefaultRandomPos.getPosAway(this.mob, 16, 7, this.from);
               if (to != null) {
                  this.mob.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
               }
            }
         }
      }

      @Override
      public void stop() {
         this.mob.getNavigation().stop();
      }

      /** The middle of the foes near him: whoever has him or one of his comrades as target. */
      private Vec3 danger() {
         UUID owner = this.mob instanceof OwnableEntity own ? own.getOwnerUUID() : null;
         double x = 0.0;
         double z = 0.0;
         int n = 0;

         for (Mob m : this.mob.level().getEntitiesOfClass(Mob.class, this.mob.getBoundingBox().inflate(NEAR), Mob::isAlive)) {
            LivingEntity t = m.getTarget();
            if (t != null && (t == this.mob || owner != null && t instanceof OwnableEntity o && owner.equals(o.getOwnerUUID()))) {
               x += m.getX();
               z += m.getZ();
               n++;
            }
         }

         return n == 0 ? null : new Vec3(x / n, this.mob.getY(), z / n);
      }
   }

   /** The soldier's spirit for the line over the hotbar. */
   static Component describe(Entity unit) {
      if (routed(unit)) {
         return Msg.t("army.morale_routed").withStyle(ChatFormatting.RED);
      } else {
         int m = (int)Math.round(get(unit));
         ChatFormatting color = m >= HIGH ? ChatFormatting.GREEN : (m >= LOW ? ChatFormatting.GOLD : ChatFormatting.RED);
         return Msg.t("army.morale", m).withStyle(color);
      }
   }

   private static void tell(MinecraftServer server, UUID owner, Component msg) {
      ServerPlayer p = owner == null ? null : server.getPlayerList().getPlayer(owner);
      if (p != null) {
         p.sendSystemMessage(msg);
      }
   }

   /** Per-tick caches: realm lookups for offline owners walk every colony. */
   private static final class Context {
      final MinecraftServer server;
      final RealmData realms;
      private final Map<UUID, Optional<Realm>> realmOf = new HashMap<>();
      private EconomyData eco;

      Context(MinecraftServer server) {
         this.server = server;
         this.realms = RealmData.get(server);
      }

      Realm realm(UUID player) {
         return player == null ? null : this.realmOf.computeIfAbsent(player, p -> Optional.ofNullable(Sieges.realmOfPlayer(this.server, this.realms, p))).orElse(null);
      }

      boolean sameSide(UUID a, UUID b) {
         if (a == null || b == null) {
            return false;
         } else if (a.equals(b)) {
            return true;
         } else {
            Realm ra = this.realm(a);
            Realm rb = this.realm(b);
            return ra != null && rb != null && (ra == rb || Wars.allied(ra, rb));
         }
      }

      int unpaid(UUID owner) {
         Realm r = this.realm(owner);
         if (r == null || !Treasury.on()) {
            return 0;
         } else {
            if (this.eco == null) {
               this.eco = EconomyData.get(this.server);
            }

            return this.eco.unpaidDays(r.id());
         }
      }

      Realm landRealm(ServerLevel level, Entity unit) {
         int id = Territory.ownerAt(level, unit.getBlockX() >> 4, unit.getBlockZ() >> 4);
         IColony c = id <= 0 ? null : IColonyManager.getInstance().getColonyByWorld(id, level);
         return c == null ? null : this.realms.byMember(Settlements.key(c));
      }
   }
}
