package com.nick.prestol.book;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Court;
import com.nick.prestol.realm.Fealty;
import com.nick.prestol.war.Camps;
import com.nick.prestol.trade.Trading;
import com.nick.prestol.economy.Treasury;
import com.nick.prestol.army.Morale;
import com.nick.prestol.army.Army;
import com.nick.prestol.army.Conscripts;
import com.nick.prestol.army.Mercenaries;
import com.nick.prestol.army.Provisions;
import com.nick.prestol.colony.PrestolHuts;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.flag.Flags;
import com.nick.prestol.realm.Settlements;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The guide of the realm book ("Наставление"): the server sends the numbers of its own config, so the guide
 * always tells the rules of this server; and a newcomer is told once where to find it.
 */
public final class Guide {
   private static final String GREETED = "prestol_greeted";

   private Guide() {
   }

   static CompoundTag rules() {
      CompoundTag r = new CompoundTag();
      r.putInt("villageCitizens", PrestolConfig.VILLAGE_CITIZENS.get());
      r.putInt("villageHall", PrestolConfig.VILLAGE_HALL.get());
      r.putInt("villageBuildings", PrestolConfig.VILLAGE_BUILDINGS.get());
      r.putInt("townCitizens", PrestolConfig.TOWN_CITIZENS.get());
      r.putInt("townHall", PrestolConfig.TOWN_HALL.get());
      r.putInt("townBuildings", PrestolConfig.TOWN_BUILDINGS.get());
      r.putInt("capitalCitizens", PrestolConfig.CAPITAL_CITIZENS.get());
      r.putInt("baseSettlements", PrestolConfig.BASE_SETTLEMENTS.get());
      r.putInt("citizensPerSettlement", PrestolConfig.CITIZENS_PER_SETTLEMENT.get());
      r.putInt("kingdomPopulation", PrestolConfig.KINGDOM_POPULATION.get());
      r.putInt("kingdomTowns", PrestolConfig.KINGDOM_TOWNS.get());
      r.putInt("empirePopulation", PrestolConfig.EMPIRE_POPULATION.get());
      r.putInt("empireTowns", PrestolConfig.EMPIRE_TOWNS.get());
      r.putInt("mutinyHappiness", (int)Math.round(PrestolConfig.MUTINY_HAPPINESS.get()));
      r.putInt("mutinyDays", PrestolConfig.MUTINY_DAYS.get());
      r.putInt("landPerSettlement", PrestolConfig.LAND_PER_SETTLEMENT.get());
      r.putInt("landPerHall", PrestolConfig.LAND_PER_HALL_LEVEL.get());
      r.putInt("landPerBuilding", PrestolConfig.LAND_PER_BUILDING.get());
      r.putInt("landReach", PrestolConfig.LAND_REACH.get());
      r.putInt("capitalBonus", PrestolConfig.CAPITAL_BONUS.get());
      r.putInt("lawLock", PrestolConfig.LAW_LOCK_MINUTES.get());
      r.putInt("unitsPerLevel", PrestolConfig.ARMY_UNITS_PER_LEVEL.get());
      r.putInt("recruitSeconds", PrestolConfig.ARMY_REFILL_SECONDS.get());
      r.putInt("trainingMinutes", PrestolConfig.TRAINING_MINUTES.get());
      r.putInt("militiaCitizens", PrestolConfig.MILITIA_CITIZENS.get());
      r.putInt("mercenaryGold", PrestolConfig.MERCENARY_GOLD.get());
      r.putInt("mercenaryMinutes", PrestolConfig.MERCENARY_MINUTES.get());
      r.putInt("provisionDays", PrestolConfig.PROVISION_DAYS.get());
      r.putInt("desertDays", PrestolConfig.DESERT_DAYS.get());
      r.putInt("mobilizeMinutes", PrestolConfig.MOBILIZE_MINUTES.get());
      r.putInt("siegeRadius", PrestolConfig.SIEGE_RADIUS.get());
      r.putInt("holdMinutes", Math.max(1, PrestolConfig.HOLD_SECONDS.get() / 60));
      r.putInt("revoltHoldMinutes", Math.max(1, PrestolConfig.REVOLT_HOLD_SECONDS.get() / 60));
      r.putInt("immunityDays", PrestolConfig.IMMUNITY_DAYS.get());
      r.putInt("truceDays", PrestolConfig.TRUCE_DAYS.get());
      r.putInt("lootMinutes", PrestolConfig.LOOT_LOCK_MINUTES.get());
      r.putInt("slaveDeath", PrestolConfig.SLAVE_DEATH_PERCENT.get());
      r.putInt("revoltDays", PrestolConfig.REVOLT_AFTER_DAYS.get());
      r.putInt("revoltHappiness", (int)Math.round(PrestolConfig.REVOLT_HAPPINESS.get()));
      r.putInt("standardWool", PrestolConfig.STANDARD_WOOL.get());
      r.putBoolean("hyw", Hyw.present());
      r.putBoolean("garrisons", Army.garrisons());
      r.putBoolean("conscription", Conscripts.on());
      r.putBoolean("militia", Army.militiaOn());
      r.putBoolean("mercenaries", Mercenaries.on());
      r.putBoolean("provisions", Provisions.on());
      r.putBoolean("defenderOnline", PrestolConfig.DEFENDER_ONLINE.get());
      r.putBoolean("capitalLast", PrestolConfig.CAPITAL_LAST.get());
      r.putBoolean("paintings", Flags.paintings());
      r.putBoolean("brewery", PrestolHuts.BREWERY);
      r.putBoolean("vinery", PrestolHuts.VINERY);
      r.putBoolean("economy", Treasury.on());
      r.putInt("taxLow", PrestolConfig.TAX_LOW.get());
      r.putInt("taxNormal", PrestolConfig.TAX_NORMAL.get());
      r.putInt("taxHigh", PrestolConfig.TAX_HIGH.get());
      r.putInt("soldierPay", PrestolConfig.SOLDIER_PAY.get());
      r.putBoolean("trade", Trading.on());
      r.putInt("caravanCost", PrestolConfig.CARAVAN_COST.get());
      r.putInt("muleCost", PrestolConfig.PACK_ANIMAL_COST.get());
      r.putInt("shipCost", PrestolConfig.SHIP_COST.get());
      r.putInt("maxTraders", PrestolConfig.MAX_TRADERS.get());
      r.putInt("dwellSeconds", PrestolConfig.DWELL_SECONDS.get());
      r.putInt("robberyChance", PrestolConfig.ROBBERY_CHANCE.get());
      r.putInt("escortCost", PrestolConfig.ESCORT_COST.get());
      r.putInt("autoRange", PrestolConfig.TRADE_RANGE.get());
      r.putInt("markup", PrestolConfig.MERCHANT_MARKUP.get());
      r.putBoolean("morale", Morale.on());
      r.putInt("routBelow", PrestolConfig.ROUT_BELOW.get());
      r.putBoolean("camps", Camps.on());
      r.putInt("campRange", PrestolConfig.CAMP_RANGE.get());
      r.putInt("campHoldMinutes", Math.max(1, PrestolConfig.CAMP_HOLD_SECONDS.get() / 60));
      r.putBoolean("enginesAtWar", Hyw.present() && PrestolConfig.SIEGE_ENGINES_AT_WAR_ONLY.get());
      r.putBoolean("court", Court.on());
      r.putInt("outlawDays", PrestolConfig.OUTLAW_DAYS.get());
      r.putBoolean("fealty", Fealty.on());
      r.putInt("fealtyShare", PrestolConfig.FEALTY_SHARE.get());
      r.putBoolean("weariness", PrestolConfig.WEARINESS_HAPPINESS.get() > 0);
      r.putInt("wearinessHappiness", PrestolConfig.WEARINESS_HAPPINESS.get());
      return r;
   }

   /** The first time a player comes to the server he is shown the key of the book and its guide. */
   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         CompoundTag data = player.getPersistentData();
         CompoundTag kept = data.getCompound(Player.PERSISTED_NBT_TAG);
         if (!kept.getBoolean(GREETED)) {
            kept.putBoolean(GREETED, true);
            data.put(Player.PERSISTED_NBT_TAG, kept);
            Component key = Component.keybind("key.prestol.book").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            player.sendSystemMessage(Component.translatable("prestol.guide.welcome", key).withStyle(ChatFormatting.YELLOW));
         }
      }
   }

   /** A new town hall: its owner learns what the settlement can grow into. */
   public static void onColonyFounded(MinecraftServer server, IColony colony) {
      UUID owner = Settlements.owner(colony);
      ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
      if (player != null) {
         Component key = Component.keybind("key.prestol.book").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
         player.sendSystemMessage(Component.translatable("prestol.guide.founded", colony.getName(), key).withStyle(ChatFormatting.YELLOW));
      }
   }
}
