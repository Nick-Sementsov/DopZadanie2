package com.nick.prestol.army;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.economy.Coins;
import com.nick.prestol.economy.EconomyData;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class Mercenaries {
   private static final long PAUSE = 48000L;
   private static final UnitType[] SQUAD = new UnitType[]{UnitType.WARRIOR, UnitType.SPEARMAN, UnitType.ARCHER, UnitType.CROSSBOWMAN, UnitType.SHIELDMAN};
   private static final TagKey<Item> GOLD = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "ingots/gold"));

   private Mercenaries() {
   }

   public static boolean on() {
      try {
         return (Boolean)PrestolConfig.MERCENARIES.get() && Hyw.present();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   public static int squad(IColony colony) {
      return switch (Settlements.tierOf(colony)) {
         case HAMLET -> PrestolConfig.MERCENARY_HAMLET.get();
         case VILLAGE -> PrestolConfig.MERCENARY_VILLAGE.get();
         case TOWN -> PrestolConfig.MERCENARY_TOWN.get();
      };
   }

   public static void hire(MinecraftServer server, ServerPlayer player, IColony colony) {
      ServerLevel level = player.serverLevel();
      long now = level.getGameTime();
      long used = colony.getMercenaryUseTime();
      if (used > 0L && now - used < 48000L) {
         long days = (48000L - (now - used) + 23999L) / 24000L;
         player.sendSystemMessage(Msg.bad("mercenary.not_yet", days));
      } else {
         int size = squad(colony);
         int price = size * (Integer)PrestolConfig.MERCENARY_GOLD.get();
         // the realm's officers hire on the treasury (an ingot is 9 coins); anyone else, or an empty treasury, pays in gold
         Realm realm = RealmData.get(server).byMember(Settlements.key(colony));
         EconomyData eco = Treasury.on() && realm != null ? EconomyData.get(server) : null;
         boolean treasury = eco != null
            && (Court.may(server, realm, player, Court.Office.MARSHAL) || Court.may(server, realm, player, Court.Office.TREASURER))
            && eco.balance(realm.id()) >= (long)price * 9L;
         if (!player.isCreative() && !treasury && gold(player) < price) {
            player.sendSystemMessage(Msg.bad("mercenary.no_gold", price, size));
         } else {
            IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
            int rank = Math.max(1, hall == null ? 1 : hall.getBuildingLevel());
            int tier = rank >= 5 ? 2 : (rank >= 3 ? 1 : 0);
            long until = now + (long)((Integer)PrestolConfig.MERCENARY_MINUTES.get()).intValue() * 1200L;
            List<BlockPos> spots = Spots.around(level, player.blockPosition(), size);
            ArmyData data = ArmyData.get(server);
            int came = 0;

            for (int i = 0; i < size; i++) {
               BlockPos at = spots.isEmpty() ? player.blockPosition() : spots.get(i % spots.size());
               Entity unit = Hyw.spawn(level, SQUAD[i % SQUAD.length].entity, player.getUUID(), at, at, rank, tier);
               if (unit == null) {
                  break;
               }

               unit.getPersistentData().putString("prestol_army", "x|" + until);
               Hyw.orders(unit, at, List.of(), player);
               data.hired.put(unit.getUUID(), new ArmyData.Hire(player.getUUID(), until));
               came++;
            }

            if (came == 0) {
               player.sendSystemMessage(Msg.bad("army.need.spawn"));
            } else {
               int paid = came * (Integer)PrestolConfig.MERCENARY_GOLD.get();
               if (treasury) {
                  eco.take(realm.id(), (long)paid * 9L);
                  eco.log(realm.id(), Realms.day(Realms.now(server)), "mercenaries", -(long)paid * 9L, colony.getName(), String.valueOf(came));
                  player.sendSystemMessage(Msg.good("mercenary.hired_treasury", came, Coins.fmt((long)paid * 9L), PrestolConfig.MERCENARY_MINUTES.get()));
               } else {
                  if (!player.isCreative()) {
                     take(player, paid);
                  }

                  player.sendSystemMessage(Msg.good("mercenary.hired", came, paid, PrestolConfig.MERCENARY_MINUTES.get()));
               }

               data.setDirty();
               colony.usedMercenaries();
            }
         }
      }
   }

   private static int gold(ServerPlayer player) {
      int n = 0;

      for (ItemStack s : player.getInventory().items) {
         if (s.is(GOLD) || s.is(Items.GOLD_INGOT)) {
            n += s.getCount();
         }
      }

      return n;
   }

   private static void take(ServerPlayer player, int amount) {
      for (ItemStack s : player.getInventory().items) {
         if (amount <= 0) {
            break;
         }

         if (s.is(GOLD) || s.is(Items.GOLD_INGOT)) {
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
         }
      }

      player.getInventory().setChanged();
   }

   static void tick(MinecraftServer server, ArmyData data, long now, Map<UUID, String> given) {
      Iterator<Entry<UUID, ArmyData.Hire>> it = data.hired.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, ArmyData.Hire> e = it.next();
         ArmyData.Hire h = e.getValue();
         if (now >= h.until()) {
            it.remove();
            data.setDirty();
            Army.dismiss(server, data, e.getKey());
            ServerPlayer p = server.getPlayerList().getPlayer(h.player());
            if (p != null && !data.hired.containsValue(h)) {
               p.sendSystemMessage(Msg.info("mercenary.gone"));
            }
         } else {
            ServerPlayer p = server.getPlayerList().getPlayer(h.player());
            if (p != null) {
               Entity unit = p.serverLevel().getEntity(e.getKey());
               String orders = "follow|" + p.getUUID();
               if (unit != null && !orders.equals(given.get(e.getKey())) && Hyw.orders(unit, unit.blockPosition(), List.of(), p)) {
                  given.put(e.getKey(), orders);
               }
            }
         }
      }
   }
}
