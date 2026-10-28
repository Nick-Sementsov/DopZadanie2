package com.nick.prestol.army;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.nick.prestol.Prestol;
import com.nick.prestol.PrestolConfig;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import com.nick.prestol.util.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/**
 * Garrison soldiers are the settlement's own people: a recruit leaves the colony when he takes up arms
 * and comes back, with his name and skills, when he is discharged.
 */
final class Conscripts {
   private Conscripts() {
   }

   static boolean on() {
      try {
         return PrestolConfig.CONSCRIPTION.get();
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   /** The adult who answers the call: idle people first, workers only if the server allows it. */
   static ICitizenData pick(MinecraftServer server, IColony colony) {
      Set<Integer> levied = new HashSet<>();
      String settlement = Settlements.key(colony);

      for (ArmyData.Levy l : ArmyData.get(server).militia.values()) {
         if (l.settlement().equals(settlement)) {
            levied.add(l.citizen());
         }
      }

      boolean employed = PrestolConfig.CONSCRIPT_EMPLOYED.get();
      List<ICitizenData> idle = new ArrayList<>();
      List<ICitizenData> working = new ArrayList<>();

      for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
         if (!c.isChild() && !levied.contains(c.getId())) {
            if (c.getJob() == null) {
               idle.add(c);
            } else if (employed) {
               working.add(c);
            }
         }
      }

      Comparator<ICitizenData> byId = Comparator.comparingInt(ICitizenData::getId);
      idle.sort(byId);
      working.sort(byId);
      if (!idle.isEmpty()) {
         return idle.get(0);
      } else {
         return working.isEmpty() ? null : working.get(0);
      }
   }

   /** Takes the citizen out of the colony and remembers who he was. */
   static CompoundTag enlist(IColony colony, ICitizenData citizen) {
      CompoundTag who = new CompoundTag();
      who.putString("name", citizen.getName());
      who.putBoolean("female", citizen.isFemale());

      try {
         who.put("skills", citizen.getCitizenSkillHandler().write());
      } catch (RuntimeException var4) {
         Prestol.LOGGER.debug("Prestol: could not read the skills of {}", citizen.getName(), var4);
      }

      citizen.getEntity().ifPresent(Entity::discard);
      colony.getCitizenManager().removeCivilian(citizen);
      return who;
   }

   /** Brings a discharged soldier home as a citizen again. */
   static void discharge(MinecraftServer server, ArmyData.Conscript conscript, BlockPos at) {
      IColony colony = Settlements.byKey(conscript.settlement());
      if (colony != null && colony.getWorld() != null) {
         CompoundTag who = conscript.citizen();

         try {
            ICitizenData fresh = colony.getCitizenManager().createAndRegisterCivilianData();
            fresh.setGenderAndGenerateName(who.getBoolean("female"));
            if (!who.getString("name").isEmpty()) {
               fresh.setName(who.getString("name"));
            }

            if (who.contains("skills")) {
               fresh.getCitizenSkillHandler().read(who.getCompound("skills"));
            }

            fresh.markDirty(0);
            colony.getCitizenManager().spawnOrCreateCitizen(fresh, colony.getWorld(), at);
            tell(server, conscript.settlement(), Msg.info("army.conscript_home", fresh.getName(), colony.getName()));
         } catch (RuntimeException var6) {
            Prestol.LOGGER.warn("Prestol: a discharged soldier could not return to {}", colony.getName(), var6);
         }
      }
   }

   static void fallen(MinecraftServer server, ArmyData.Conscript conscript) {
      IColony colony = Settlements.byKey(conscript.settlement());
      if (colony != null) {
         tell(server, conscript.settlement(), Msg.bad("army.conscript_fallen", conscript.citizen().getString("name"), colony.getName()));
      }
   }

   static Component soldierName(String name, UnitType type) {
      return Component.translatable(type.langKey()).append(" " + name);
   }

   private static void tell(MinecraftServer server, String settlement, Component msg) {
      IColony colony = Settlements.byKey(settlement);
      if (colony != null) {
         Realm realm = RealmData.get(server).byMember(settlement);
         if (realm != null) {
            Realms.tell(server, realm, msg);
         } else {
            Realms.tellColony(server, colony, msg);
         }
      }
   }
}
