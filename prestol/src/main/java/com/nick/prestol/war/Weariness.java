package com.nick.prestol.war;

import com.minecolonies.api.research.util.ResearchConstants;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.util.Msg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * War weariness (0-100) of each realm: every day of active war, every soldier fallen to the enemy and every
 * settlement lost wears the people down, a settlement taken cheers them. The weary are unhappy in every settlement
 * of the realm; in peace the weariness wears off day by day.
 */
public final class Weariness {
   private static final Map<UUID, Double> LEVEL = new HashMap<>();
   private static final double PER_WAR_DAY = 4.0;
   private static final double PER_SOLDIER = 0.5;
   private static final double PER_LOST = 15.0;
   private static final double PER_WON = -10.0;
   private static final double PEACE_DAY = -10.0;
   private static long lastDay = -1L;

   private Weariness() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.WEARINESS_HAPPINESS.get() > 0;
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static double of(UUID realm) {
      return LEVEL.getOrDefault(realm, 0.0);
   }

   static void add(MinecraftServer server, Realm realm, double v) {
      if (on() && realm != null && v != 0.0) {
         double was = of(realm.id());
         double now = Math.max(0.0, Math.min(100.0, was + v));
         if (now <= 0.0) {
            LEVEL.remove(realm.id());
         } else {
            LEVEL.put(realm.id(), now);
         }

         if (was < 50.0 && now >= 50.0) {
            Realms.tell(server, realm, Msg.bad("war.weary", (int)now));
         } else if (was < 80.0 && now >= 80.0) {
            Realms.tell(server, realm, Msg.bad("war.exhausted", (int)now));
         }
      }
   }

   /** Every second: on a new game day the wars wear the realms down and peace eases them. */
   public static void tick(MinecraftServer server) {
      if (on()) {
         long day = Realms.day(Realms.now(server));
         if (lastDay < 0L) {
            lastDay = day;
         } else if (day != lastDay) {
            lastDay = day;
            RealmData data = RealmData.get(server);

            for (Realm r : data.all()) {
               int wars = 0;

               for (Realm other : data.all()) {
                  if (other != r && Wars.fighting(server, data, r, other)) {
                     wars++;
                  }
               }

               add(server, r, wars > 0 ? PER_WAR_DAY * wars : PEACE_DAY);
            }

            LEVEL.keySet().removeIf(id -> data.byId(id) == null);
         }
      }
   }

   /** A soldier fallen to the enemy is a son lost. */
   public static void onDeath(LivingDeathEvent event) {
      LivingEntity dead = event.getEntity();
      MinecraftServer server = dead.getServer();
      if (on() && server != null && Hyw.soldier(dead)) {
         RealmData data = RealmData.get(server);
         Realm mine = Sieges.realmOfPlayer(server, data, ((OwnableEntity)dead).getOwnerUUID());
         Entity killer = event.getSource().getEntity();
         java.util.UUID by = killer instanceof Player p ? p.getUUID() : (killer instanceof OwnableEntity own ? own.getOwnerUUID() : null);
         Realm theirs = by == null ? null : Sieges.realmOfPlayer(server, data, by);
         if (mine != null && theirs != null && Wars.fighting(server, data, mine, theirs)) {
            add(server, mine, PER_SOLDIER);
         }
      }
   }

   /** A settlement changed hands in war or by treaty. */
   static void settlementChanged(MinecraftServer server, Realm lost, Realm won) {
      add(server, lost, PER_LOST);
      add(server, won, PER_WON);
   }

   /** The unhappiness of a weary realm, merged into the decree effects of each of its settlements. */
   public static void addEffects(MinecraftServer server, String key, Map<ResourceLocation, Double> out) {
      if (on()) {
         Realm realm = RealmData.get(server).byMember(key);
         double w = realm == null ? 0.0 : of(realm.id());
         if (w > 0.0) {
            out.merge(ResearchConstants.HAPPINESS, -PrestolConfig.WEARINESS_HAPPINESS.get() / 100.0 * w / 100.0, Double::sum);
         }
      }
   }

   public static boolean any() {
      return !LEVEL.isEmpty();
   }

   static CompoundTag save() {
      CompoundTag t = new CompoundTag();
      LEVEL.forEach((id, v) -> t.putDouble(id.toString(), v));
      t.putLong("@day", lastDay);
      return t;
   }

   static void restore(CompoundTag t) {
      clear();

      for (String k : t.getAllKeys()) {
         if (k.equals("@day")) {
            lastDay = t.getLong(k);
         } else {
            try {
               LEVEL.put(UUID.fromString(k), t.getDouble(k));
            } catch (IllegalArgumentException var4) {
            }
         }
      }

      if (!t.contains("@day")) {
         lastDay = -1L;
      }
   }

   public static void clear() {
      LEVEL.clear();
      lastDay = -1L;
   }
}
