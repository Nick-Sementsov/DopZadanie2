package com.nick.prestol.war;

import com.minecolonies.api.colony.IColony;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.army.Morale;
import com.nick.prestol.compat.Hyw;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;

/**
 * Siege camps. A realm at war sets up a war tent near an enemy settlement and its army settles there: chests by the
 * tent feed its soldiers, the soldiers around it fight as if on their own land, a siege does not lift as soon as the
 * attackers step back from the town hall, and nobody trades with the blockaded settlement. Burning the tent is a blow
 * to the besiegers; when the war ends the camp is struck.
 */
public final class Camps {
   private static final List<Camps.Camp> CAMPS = new ArrayList<>();
   /** Soldiers this close to their camp count it as home and eat from its stores. */
   public static final double AURA = 48.0;
   /** Chests and barrels this close to the tent are the camp's stores. */
   private static final int STORE_REACH = 4;
   private static final double RAZED_SHOCK = -15.0;

   private Camps() {
   }

   public static boolean on() {
      try {
         return PrestolConfig.CAMP_RANGE.get() > 0;
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   @SubscribeEvent
   public static void onPlace(BlockEvent.EntityPlaceEvent event) {
      if (event.getPlacedBlock().is(WarBlocks.CAMP.get()) && event.getLevel() instanceof ServerLevel level) {
         if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
         } else {
            Component refusal = pitch(level, player, event.getPos());
            if (refusal != null) {
               event.setCanceled(true);
               player.displayClientMessage(refusal, true);
            }
         }
      }
   }

   /** Sets up the camp, or says why it cannot stand here. */
   private static Component pitch(ServerLevel level, ServerPlayer player, BlockPos pos) {
      if (!on()) {
         return Msg.bad("camp.off");
      } else {
         MinecraftServer server = level.getServer();
         RealmData realms = RealmData.get(server);
         Realm mine = Realms.realmOf(server, player);
         if (mine == null) {
            return Msg.bad("camp.no_realm");
         } else {
            int range = PrestolConfig.CAMP_RANGE.get();
            IColony target = null;
            double best = (double)range * range;

            for (IColony c : Settlements.all()) {
               Realm r = realms.byMember(Settlements.key(c));
               double d = c.getCenter().distSqr(pos);
               if (c.getDimension().equals(level.dimension()) && r != null && Wars.atWar(realms, mine, r) && d <= best) {
                  best = d;
                  target = c;
               }
            }

            if (target == null) {
               return Msg.bad("camp.no_enemy", range);
            } else {
               String key = Settlements.key(target);

               for (Camps.Camp c : CAMPS) {
                  if (c.realm.equals(mine.id()) && c.target.equals(key)) {
                     return Msg.bad("camp.already", target.getName(), c.pos.getX(), c.pos.getZ());
                  }
               }

               Camps.Camp camp = new Camps.Camp(level.dimension().location().toString(), pos.immutable(), mine.id(), key, player.getUUID(), Realms.now(server));
               CAMPS.add(camp);
               Realm defender = realms.byMember(key);
               Realms.tell(server, mine, Msg.war("camp.pitched_own", target.getName()));
               if (defender != null) {
                  Realms.tell(server, defender, Msg.war("camp.pitched_enemy", mine.name(), target.getName()));
               }

               Realms.chronicle(server, "camp", List.of(mine.name(), target.getName()), mine, defender);
               return null;
            }
         }
      }
   }

   /** The tent is gone (broken, burnt, blown up): the besiegers lose heart. */
   static void removed(ServerLevel level, BlockPos pos) {
      Camps.Camp camp = at(level, pos);
      if (camp != null) {
         CAMPS.remove(camp);
         MinecraftServer server = level.getServer();
         RealmData realms = RealmData.get(server);
         Realm mine = realms.byId(camp.realm);
         Realm defender = realms.byMember(camp.target);
         IColony target = Settlements.byKey(camp.target);
         String name = target == null ? "?" : target.getName();
         String who = mine == null ? "?" : mine.name();
         Component msg = Msg.war("camp.razed", who, name);
         if (mine != null) {
            Realms.tell(server, mine, msg);
         }

         if (defender != null) {
            Realms.tell(server, defender, msg);
         }

         Realms.chronicle(server, "camp_razed", List.of(who, name), mine, defender);
         AABB around = new AABB(pos).inflate(AURA);

         for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, around, Hyw::eater)) {
            Realm theirs = Sieges.realmOfPlayer(server, realms, ((OwnableEntity)e).getOwnerUUID());
            if (mine != null && theirs == mine) {
               Morale.shock(e, RAZED_SHOCK);
            }
         }
      }
   }

   /** Every second: a camp whose war is over, or whose target is gone or taken, is struck. */
   public static void tick(MinecraftServer server) {
      if (!CAMPS.isEmpty()) {
         RealmData realms = RealmData.get(server);
         Iterator<Camps.Camp> it = CAMPS.iterator();

         while (it.hasNext()) {
            Camps.Camp camp = it.next();
            Realm mine = realms.byId(camp.realm);
            Realm defender = realms.byMember(camp.target);
            if (mine == null || defender == null || defender == mine || !Wars.atWar(realms, mine, defender)) {
               it.remove();
               strike(server, camp, mine);
            }
         }
      }
   }

   private static void strike(MinecraftServer server, Camps.Camp camp, Realm mine) {
      ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(camp.dim)));
      if (level != null && level.isLoaded(camp.pos) && level.getBlockState(camp.pos).is(WarBlocks.CAMP.get())) {
         level.removeBlock(camp.pos, false);
         Block.popResource(level, camp.pos, new ItemStack(WarBlocks.CAMP_ITEM.get()));
      }

      IColony target = Settlements.byKey(camp.target);
      if (mine != null) {
         Realms.tell(server, mine, Msg.info("camp.struck", target == null ? "?" : target.getName()));
      }
   }

   private static Camps.Camp at(Level level, BlockPos pos) {
      String dim = level.dimension().location().toString();

      for (Camps.Camp c : CAMPS) {
         if (c.pos.equals(pos) && c.dim.equals(dim)) {
            return c;
         }
      }

      return null;
   }

   /** The camp of {@code realm} whose aura covers {@code pos}, if any. */
   public static Camps.Camp near(Level level, BlockPos pos, UUID realm) {
      if (realm != null && !CAMPS.isEmpty()) {
         String dim = level.dimension().location().toString();

         for (Camps.Camp c : CAMPS) {
            if (c.realm.equals(realm) && c.dim.equals(dim) && c.pos.distSqr(pos) <= AURA * AURA) {
               return c;
            }
         }
      }

      return null;
   }

   /** Whether {@code realm} has a camp before the settlement {@code key}. */
   public static boolean besieging(UUID realm, String key) {
      for (Camps.Camp c : CAMPS) {
         if (c.realm.equals(realm) && c.target.equals(key)) {
            return true;
         }
      }

      return false;
   }

   /** The camp that blockades the settlement {@code key} for a trader of {@code realm}: one of a realm that is not his or his ally's. */
   public static Camps.Camp blockade(RealmData realms, String key, UUID realm) {
      Realm mine = realms.byId(realm);

      for (Camps.Camp c : CAMPS) {
         if (c.target.equals(key) && !c.realm.equals(realm)) {
            Realm theirs = realms.byId(c.realm);
            if (theirs == null || mine == null || !Wars.allied(mine, theirs)) {
               return c;
            }
         }
      }

      return null;
   }

   /** Whether an enemy camp stands before the settlement {@code key} of {@code realm}. */
   public static boolean encircled(String key, UUID realm) {
      for (Camps.Camp c : CAMPS) {
         if (c.target.equals(key) && !c.realm.equals(realm)) {
            return true;
         }
      }

      return false;
   }

   /** The chests and barrels around the tent. */
   public static List<IItemHandler> stores(ServerLevel level, Camps.Camp camp) {
      List<IItemHandler> out = new ArrayList<>();

      for (BlockPos p : BlockPos.betweenClosed(camp.pos.offset(-STORE_REACH, -2, -STORE_REACH), camp.pos.offset(STORE_REACH, 2, STORE_REACH))) {
         if (level.isLoaded(p)) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof RandomizableContainerBlockEntity) {
               be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().ifPresent(out::add);
            }
         }
      }

      return out;
   }

   /** The camps the book shows a realm: its own and the enemy's before its settlements. */
   public static ListTag describe(MinecraftServer server, Realm mine) {
      ListTag out = new ListTag();
      RealmData realms = RealmData.get(server);

      for (Camps.Camp c : CAMPS) {
         boolean own = c.realm.equals(mine.id());
         Realm defender = realms.byMember(c.target);
         if (own || defender == mine) {
            IColony target = Settlements.byKey(c.target);
            Realm theirs = realms.byId(c.realm);
            CompoundTag t = new CompoundTag();
            t.putString("target", target == null ? "?" : target.getName());
            t.putString("realm", theirs == null ? "?" : theirs.name());
            t.putBoolean("mine", own);
            t.putInt("x", c.pos.getX());
            t.putInt("z", c.pos.getZ());
            out.add(t);
         }
      }

      return out;
   }

   static ListTag saveAll() {
      ListTag out = new ListTag();

      for (Camps.Camp c : CAMPS) {
         CompoundTag t = new CompoundTag();
         t.putString("dim", c.dim);
         t.put("pos", NbtUtils.writeBlockPos(c.pos));
         t.putUUID("realm", c.realm);
         t.putString("target", c.target);
         t.putUUID("by", c.by);
         t.putLong("since", c.since);
         out.add(t);
      }

      return out;
   }

   static void restore(ListTag list) {
      CAMPS.clear();

      for (int i = 0; i < list.size(); i++) {
         CompoundTag t = list.getCompound(i);
         if (t.hasUUID("realm") && t.hasUUID("by")) {
            CAMPS.add(new Camps.Camp(t.getString("dim"), NbtUtils.readBlockPos(t.getCompound("pos")), t.getUUID("realm"), t.getString("target"), t.getUUID("by"), t.getLong("since")));
         }
      }
   }

   public static void clear() {
      CAMPS.clear();
   }

   public static record Camp(String dim, BlockPos pos, UUID realm, String target, UUID by, long since) {
   }
}
