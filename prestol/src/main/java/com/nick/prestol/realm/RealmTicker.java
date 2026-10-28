package com.nick.prestol.realm;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.util.Msg;
import com.nick.prestol.war.Wars;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class RealmTicker {
   private final Map<UUID, String> lastColony = new HashMap<>();
   private int ticks;

   @SubscribeEvent
   public void onServerTick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         this.ticks++;
         MinecraftServer server = event.getServer();
         if (server != null && this.ticks % (Integer)PrestolConfig.CHECK_INTERVAL.get() == 0) {
            try {
               recount(server);
            } catch (RuntimeException var4) {
               Prestol.LOGGER.error("Prestol: error while recounting settlements", var4);
            }
         }
      }
   }

   static void recount(MinecraftServer server) {
      RealmData data = RealmData.get(server);
      Realms.cleanup(server, data, null);

      for (IColony colony : Settlements.all()) {
         String key = Settlements.key(colony);
         UUID owner = Settlements.owner(colony);
         Holding h = data.holding(key);
         UUID last = h.lastOwner;
         if (owner != null && !owner.equals(last)) {
            h.lastOwner = owner;
            data.setDirty();
            if (last != null && !"[abandoned]".equals(Settlements.ownerName(colony))) {
               Realms.onOwnerChanged(server, data, colony, owner);
            }
         }
      }

      for (Realm r : new ArrayList<>(data.all())) {
         Realms.fixRuler(server, data, r);
      }

      for (IColony colonyx : Settlements.all()) {
         String key = Settlements.key(colonyx);
         Tier now = Settlements.tierOf(colonyx);
         Holding h = data.holding(key);
         int was = h.tier;
         if (was != now.ordinal()) {
            h.tier = now.ordinal();
            data.setDirty();
            Realms.changed(server);
         }

         if (was >= 0 && was != now.ordinal()) {
            Tier old = Tier.byOrdinal(was);
            boolean up = now.ordinal() > was;
            Realm realm = data.byMember(key);
            MutableComponent msg = up
               ? Msg.good("tier.up", colonyx.getName(), old.title(), now.title())
               : Msg.bad("tier.down", colonyx.getName(), old.title(), now.title());
            if (up && (Boolean)PrestolConfig.ANNOUNCE_GROWTH.get()) {
               Realms.tellAll(server, msg);
            } else if (realm != null) {
               Realms.tell(server, realm, msg);
            } else {
               Realms.tellColony(server, colonyx, msg);
            }

            Realms.chronicle(server, up ? "tier_up" : "tier_down", List.of(colonyx.getName(), "@" + old.key(), "@" + now.key()), realm);
         }
      }

      for (Realm r : data.all()) {
         IColony cap = Settlements.byKey(r.capital());
         if (cap != null) {
            boolean fit = Settlements.canBeCapital(cap);
            if (!fit && !r.capitalWarned()) {
               r.setCapitalWarned(true);
               data.setDirty();
               Realms.tell(server, r, Msg.info("realm.capital_small", cap.getName()));
            } else if (fit && r.capitalWarned()) {
               r.setCapitalWarned(false);
               data.setDirty();
            }
         }
      }

      Wars.applyHostility(server, data);
      Hyw.sync(server, data);
   }

   @SubscribeEvent
   public void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 20 == 0 && (Boolean)PrestolConfig.SHOW_ACTIONBAR.get()) {
         IColony colony = Realms.colonyAt(player);
         String key = colony == null ? "" : Settlements.key(colony);
         String before = this.lastColony.put(player.getUUID(), key);
         if (colony != null && !key.equals(before)) {
            MinecraftServer server = player.getServer();
            if (server != null) {
               RealmData data = RealmData.get(server);
               Realm realm = data.byMember(key);
               MutableComponent line;
               if (realm == null) {
                  line = Msg.t("actionbar.free", Settlements.tierOf(colony).title(), colony.getName());
               } else {
                  MutableComponent role = key.equals(realm.capital()) ? Msg.t("role.capital") : Settlements.tierOf(colony).title();
                  line = Msg.t("actionbar.realm", role, colony.getName(), Component.translatable(Realms.rank(realm).key()), realm.name());
               }

               player.displayClientMessage(line, true);
            }
         }
      }
   }

   @SubscribeEvent
   public void onLogout(PlayerLoggedOutEvent event) {
      this.lastColony.remove(event.getEntity().getUUID());
   }
}
