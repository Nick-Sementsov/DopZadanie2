package com.nick.prestol.war;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.net.Net;
import com.nick.prestol.realm.Holding;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

public final class Conquest {
   private static final Map<String, Conquest.Pending> PENDING = new HashMap<>();
   private static final Map<BlockPos, UUID> LOOT = new HashMap<>();
   private static final Map<BlockPos, Long> LOOT_UNTIL = new HashMap<>();
   private static final Set<String> ENSLAVED = Collections.synchronizedSet(new LinkedHashSet<>());

   private Conquest() {
   }

   public static boolean pending(String key) {
      return PENDING.containsKey(key);
   }

   public static boolean enslaved(IColony colony) {
      return colony != null && !ENSLAVED.isEmpty() && ENSLAVED.contains(Settlements.key(colony));
   }

   public static void reload(RealmData data) {
      ENSLAVED.clear();
      data.holdings().forEach((k, h) -> {
         if (h.enslaved) {
            ENSLAVED.add(k);
         }
      });
   }

   static void captured(MinecraftServer server, IColony colony, UUID attackerRealm, UUID commander) {
      RealmData data = RealmData.get(server);
      Realm victorRealm = data.byId(attackerRealm);
      Realm loser = data.byMember(Settlements.key(colony));
      String key = Settlements.key(colony);
      UUID victor = commander != null ? commander : (victorRealm != null ? victorRealm.ruler() : null);
      if (victor != null) {
         String name = Owners.nameOf(server, victor, colony.getPermissions());
         long deadline = Realms.now(server) + (long)((Integer)PrestolConfig.CHOICE_SECONDS.get()).intValue() * 20L;
         PENDING.put(key, new Conquest.Pending(key, victor, name, attackerRealm, deadline));
         Realms.tellAll(server, Msg.war("siege.won", colony.getName(), victorRealm == null ? name : victorRealm.name(), loser == null ? "" : loser.name()));
         ServerLevel level = server.getLevel(colony.getDimension());
         if (level != null) {
            level.playSound(null, colony.getCenter(), (SoundEvent)SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 32.0F, 0.8F);
         }

         ServerPlayer p = server.getPlayerList().getPlayer(victor);
         if (p != null) {
            Net.openChoice(p, key, colony.getName(), (Integer)PrestolConfig.CHOICE_SECONDS.get());
         }
      }
   }

   public static void tick(MinecraftServer server) {
      long now = Realms.now(server);

      for (Conquest.Pending p : new ArrayList<>(PENDING.values())) {
         if (now >= p.deadline()) {
            apply(server, p, Conquest.Choice.KEEP);
         }
      }

      Iterator<Entry<BlockPos, Long>> it = LOOT_UNTIL.entrySet().iterator();

      while (it.hasNext()) {
         Entry<BlockPos, Long> e = it.next();
         if (now >= e.getValue()) {
            LOOT.remove(e.getKey());
            it.remove();
         }
      }

      if (now % 24000L < 20L) {
         dawn(server);
      }
   }

   public static Realms.Result choose(MinecraftServer server, ServerPlayer player, String key, String what) {
      Conquest.Pending p = PENDING.get(key);
      if (p != null && p.victor().equals(player.getUUID())) {
         Conquest.Choice c;
         try {
            c = Conquest.Choice.valueOf(what.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var7) {
            return Realms.Result.no(Msg.bad("err.unknown"));
         }

         apply(server, p, c);
         return Realms.Result.yes(Msg.good("conquest.chosen." + c.name().toLowerCase(Locale.ROOT)));
      } else {
         return Realms.Result.no(Msg.bad("conquest.not_yours"));
      }
   }

   private static void apply(MinecraftServer server, Conquest.Pending p, Conquest.Choice choice) {
      PENDING.remove(p.key());
      IColony colony = Settlements.byKey(p.key());
      if (colony != null) {
         RealmData data = RealmData.get(server);
         Realm loser = data.byMember(p.key());
         Realm victorRealm = data.byId(p.realm());
         Holding h = data.holding(p.key());
         long now = Realms.now(server);
         h.immuneUntil = now + (long)((Integer)PrestolConfig.IMMUNITY_DAYS.get()).intValue() * 24000L;
         String victorName = victorRealm == null ? p.victorName() : victorRealm.name();
         if (choice == Conquest.Choice.SACK) {
            int stacks = sack(server, colony, p.realm());
            data.setDirty();
            Realms.tellAll(server, Msg.war("conquest.sacked", colony.getName(), victorName, stacks));
            Realms.chronicle(server, "sacked", List.of(colony.getName(), victorName), victorRealm, loser);
         } else {
            UUID oldOwner = Settlements.owner(colony);
            h.formerOwner = oldOwner;
            h.formerOwnerName = Settlements.ownerName(colony);
            h.formerRealm = loser == null ? null : loser.id();
            h.capturedAt = now;
            if (Owners.setOwner(server, colony, p.victor(), p.victorName())) {
               h.lastOwner = p.victor();
               h.enslaved = choice == Conquest.Choice.ENSLAVE;
               h.unhappyDays = 0;
               if (h.enslaved) {
                  ENSLAVED.add(p.key());
               } else {
                  ENSLAVED.remove(p.key());
               }

               if (loser != null) {
                  loser.members().remove(p.key());
                  data.setDirty();
                  Realms.fixCapital(server, data, loser);
               }

               if (victorRealm != null && data.byId(victorRealm.id()) != null) {
                  if (Realms.checkJoin(data, victorRealm, colony) == null) {
                     Realms.attach(server, data, victorRealm, colony);
                  } else {
                     Realms.tell(server, victorRealm, Msg.bad("conquest.no_room", colony.getName()));
                  }
               }

               data.setDirty();
               Wars.applyHostility(server, data);
               Realms.changed(server);
               Realms.tellAll(server, Msg.war(choice == Conquest.Choice.ENSLAVE ? "conquest.enslaved" : "conquest.kept", colony.getName(), victorName));
               Realms.chronicle(server, choice == Conquest.Choice.ENSLAVE ? "enslaved" : "taken", List.of(colony.getName(), victorName), victorRealm, loser);
            }
         }
      }
   }

   private static int sack(MinecraftServer server, IColony colony, UUID realm) {
      ServerLevel level = server.getLevel(colony.getDimension());
      if (level == null) {
         return 0;
      } else {
         List<ItemStack> loot = new ArrayList<>();
         List<IBuilding> warehouses = new ArrayList<>();

         for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
            boolean ware = b instanceof BuildingWareHouse;
            if (ware || b instanceof BuildingTownHall) {
               if (ware) {
                  warehouses.add(b);
               }

               Set<BlockPos> spots = new LinkedHashSet<>(b.getContainers());
               spots.add(b.getPosition());

               for (BlockPos pos : spots) {
                  if (level.isLoaded(pos)) {
                     BlockEntity be = level.getBlockEntity(pos);
                     if (be != null) {
                        IItemHandler inv = (IItemHandler)be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
                        if (inv != null) {
                           for (int i = 0; i < inv.getSlots(); i++) {
                              for (ItemStack s = inv.extractItem(i, Integer.MAX_VALUE, false); !s.isEmpty(); s = inv.extractItem(i, Integer.MAX_VALUE, false)) {
                                 loot.add(s);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         int count = loot.size();
         placeLoot(level, colony.getCenter(), loot, realm);

         for (IBuilding w : warehouses) {
            try {
               w.destroy();
               w.deconstruct();
            } catch (RuntimeException var16) {
               Prestol.LOGGER.warn("Prestol: could not pull down the warehouse of {}", colony.getName(), var16);
            }
         }

         return count;
      }
   }

   private static void placeLoot(ServerLevel level, BlockPos center, List<ItemStack> loot, UUID realm) {
      if (!loot.isEmpty()) {
         long until = level.getServer().overworld().getGameTime() + (long)((Integer)PrestolConfig.LOOT_LOCK_MINUTES.get()).intValue() * 1200L;
         int index = 0;

         for (int ring = 3; ring <= 10 && index < loot.size(); ring++) {
            for (int dx = -ring; dx <= ring && index < loot.size(); dx++) {
               for (int dz = -ring; dz <= ring && index < loot.size(); dz++) {
                  if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && (dx + dz & 1) == 0) {
                     int x = center.getX() + dx;
                     int z = center.getZ() + dz;
                     BlockPos pos = new BlockPos(x, level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                     if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
                        BlockEntity slot = level.getBlockEntity(pos);
                        if (slot instanceof ChestBlockEntity) {
                           ChestBlockEntity chest = (ChestBlockEntity)slot;

                           for (int slotx = 0; slotx < chest.getContainerSize() && index < loot.size(); slotx++) {
                              chest.setItem(slotx, loot.get(index++));
                           }

                           chest.setCustomName(Msg.t("conquest.loot_chest"));
                           chest.setChanged();
                           if (realm != null && until > 0L) {
                              LOOT.put(pos.immutable(), realm);
                              LOOT_UNTIL.put(pos.immutable(), until);
                           }
                        }
                     }
                  }
               }
            }
         }

         while (index < loot.size()) {
            Containers.dropItemStack(level, (double)center.getX() + 0.5, (double)center.getY() + 1.5, (double)center.getZ() + 0.5, loot.get(index++));
         }
      }
   }

   private static boolean mayOpen(Player player, BlockPos pos) {
      UUID realm = LOOT.get(pos);
      if (realm != null && player instanceof ServerPlayer sp) {
         Realm r = Realms.realmOf(sp.server, sp);
         return r != null && r.id().equals(realm);
      } else {
         return true;
      }
   }

   @SubscribeEvent
   public static void onOpen(RightClickBlock event) {
      if (!LOOT.isEmpty() && !event.getLevel().isClientSide() && !mayOpen(event.getEntity(), event.getPos())) {
         event.setCanceled(true);
         event.getEntity().displayClientMessage(Msg.bad("conquest.loot_locked"), true);
      }
   }

   @SubscribeEvent
   public static void onBreak(BreakEvent event) {
      if (!LOOT.isEmpty() && !mayOpen(event.getPlayer(), event.getPos())) {
         event.setCanceled(true);
         event.getPlayer().displayClientMessage(Msg.bad("conquest.loot_locked"), true);
      }
   }

   static void revolutionWon(MinecraftServer server, IColony colony, UUID claimant) {
      RealmData data = RealmData.get(server);
      String key = Settlements.key(colony);
      Holding h = data.holding(key);
      Realm current = data.byMember(key);
      String name = h.formerOwnerName != null && !h.formerOwnerName.isEmpty() ? h.formerOwnerName : Owners.nameOf(server, claimant, colony.getPermissions());
      if (Owners.setOwner(server, colony, claimant, name)) {
         h.lastOwner = claimant;
         h.enslaved = false;
         ENSLAVED.remove(key);
         h.immuneUntil = Realms.now(server) + (long)((Integer)PrestolConfig.IMMUNITY_DAYS.get()).intValue() * 24000L;
         Realm home = data.byId(h.formerRealm);
         h.formerOwner = null;
         h.formerOwnerName = "";
         h.formerRealm = null;
         if (current != null) {
            current.members().remove(key);
            data.setDirty();
            Realms.fixCapital(server, data, current);
         }

         if (home != null && data.byId(home.id()) != null && Realms.checkJoin(data, home, colony) == null) {
            Realms.attach(server, data, home, colony);
         }

         data.setDirty();
         Wars.applyHostility(server, data);
         Realms.changed(server);
         Realms.tellAll(server, Msg.war("revolt.won", colony.getName(), name));
         Realms.chronicle(server, "revolt_won", List.of(colony.getName(), name), current, home);
      }
   }

   public static Realms.Result free(MinecraftServer server, ServerPlayer player, String key) {
      RealmData data = RealmData.get(server);
      IColony colony = Settlements.byKey(key);
      Holding h = data.peek(key);
      if (colony != null && h != null && h.enslaved) {
         Realm realm = data.byMember(key);
         if (!Settlements.isOwner(player, colony) && !Realms.isRuler(realm, player)) {
            return Realms.Result.no(Msg.bad("err.not_your_settlement"));
         } else {
            h.enslaved = false;
            ENSLAVED.remove(key);
            data.setDirty();
            Realms.tellAll(server, Msg.good("conquest.freed", colony.getName()));
            Realms.chronicle(server, "freed", List.of(colony.getName()), realm);
            return Realms.Result.yes(Msg.good("conquest.freed", colony.getName()));
         }
      } else {
         return Realms.Result.no(Msg.bad("conquest.not_enslaved"));
      }
   }

   public static void dawn(MinecraftServer server) {
      RealmData data = RealmData.get(server);
      long day = Realms.day(Realms.now(server));

      for (IColony colony : Settlements.all()) {
         String key = Settlements.key(colony);
         Holding h = data.peek(key);
         Realm realm = data.byMember(key);
         if (h != null || realm != null) {
            h = data.holding(key);
            if (h.lastDay != day) {
               h.lastDay = day;
               data.setDirty();
               if (h.enslaved) {
                  toll(server, colony);
               }

               int days = (Integer)PrestolConfig.MUTINY_DAYS.get();
               if (realm != null && days > 0 && !key.equals(realm.capital())) {
                  if (Settlements.stats(colony).citizens() > 0 && Settlements.happiness(colony) < (Double)PrestolConfig.MUTINY_HAPPINESS.get()) {
                     h.unhappyDays++;
                     if (h.unhappyDays >= days) {
                        h.unhappyDays = 0;
                        Realms.detach(server, data, realm, colony);
                        Realms.tellAll(server, Msg.war("unrest.mutiny", colony.getName(), realm.name()));
                        Realms.chronicle(server, "mutiny", List.of(colony.getName(), realm.name()), realm);
                     } else {
                        Realms.tellColony(server, colony, Msg.bad("unrest.warning", colony.getName(), h.unhappyDays, days));
                     }
                  } else {
                     h.unhappyDays = 0;
                  }
               } else {
                  h.unhappyDays = 0;
               }
            }
         }
      }
   }

   private static void toll(MinecraftServer server, IColony colony) {
      List<ICitizenData> people = new ArrayList<>(colony.getCitizenManager().getCitizens());
      if (!people.isEmpty()) {
         int dead = Math.max(1, (int)Math.ceil((double)(people.size() * (Integer)PrestolConfig.SLAVE_DEATH_PERCENT.get()) / 100.0));
         if ((Integer)PrestolConfig.SLAVE_DEATH_PERCENT.get() > 0) {
            Collections.shuffle(people);
            int done = 0;

            for (ICitizenData c : people) {
               if (done >= dead) {
                  break;
               }

               Optional<AbstractEntityCitizen> entity = c.getEntity();
               if (entity.isPresent() && entity.get().isAlive()) {
                  entity.get().hurt(entity.get().damageSources().starve(), Float.MAX_VALUE);
               } else {
                  colony.getCitizenManager().removeCivilian(c);
               }

               done++;
            }

            Realms.tellColony(server, colony, Msg.bad("conquest.slaves_died", colony.getName(), done));
         }
      }
   }

   /** The victor's choice and the locked loot chests survive restarts through {@link WarData}. */
   static ListTag savePending() {
      ListTag out = new ListTag();

      for (Conquest.Pending p : PENDING.values()) {
         CompoundTag t = new CompoundTag();
         t.putString("key", p.key());
         t.putUUID("victor", p.victor());
         t.putString("victorName", p.victorName());
         if (p.realm() != null) {
            t.putUUID("realm", p.realm());
         }

         t.putLong("deadline", p.deadline());
         out.add(t);
      }

      return out;
   }

   static ListTag saveLoot() {
      ListTag out = new ListTag();
      LOOT.forEach((pos, realm) -> {
         CompoundTag t = new CompoundTag();
         t.putLong("pos", pos.asLong());
         t.putUUID("realm", realm);
         t.putLong("until", LOOT_UNTIL.getOrDefault(pos, 0L));
         out.add(t);
      });
      return out;
   }

   static void restore(ListTag pending, ListTag loot) {
      for (int i = 0; i < pending.size(); i++) {
         CompoundTag t = pending.getCompound(i);
         if (t.hasUUID("victor")) {
            String key = t.getString("key");
            PENDING.put(
               key, new Conquest.Pending(key, t.getUUID("victor"), t.getString("victorName"), t.hasUUID("realm") ? t.getUUID("realm") : null, t.getLong("deadline"))
            );
         }
      }

      for (int i = 0; i < loot.size(); i++) {
         CompoundTag t = loot.getCompound(i);
         if (t.hasUUID("realm")) {
            BlockPos pos = BlockPos.of(t.getLong("pos"));
            LOOT.put(pos, t.getUUID("realm"));
            LOOT_UNTIL.put(pos, t.getLong("until"));
         }
      }
   }

   public static void clear() {
      PENDING.clear();
      LOOT.clear();
      LOOT_UNTIL.clear();
      ENSLAVED.clear();
   }

   public static CompoundTag pendingFor(ServerPlayer player, MinecraftServer server) {
      long now = Realms.now(server);

      for (Conquest.Pending p : PENDING.values()) {
         if (p.victor().equals(player.getUUID())) {
            CompoundTag t = new CompoundTag();
            IColony c = Settlements.byKey(p.key());
            t.putString("key", p.key());
            t.putString("name", c == null ? "?" : c.getName());
            t.putInt("seconds", (int)Math.max(0L, (p.deadline() - now) / 20L));
            return t;
         }
      }

      return null;
   }

   public static enum Choice {
      KEEP,
      SACK,
      ENSLAVE;
   }

   static record Pending(String key, UUID victor, String victorName, UUID realm, long deadline) {
   }
}
