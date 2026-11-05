package com.nick.prestol.work;

import com.ecominecolonies.crew.CrewMember;
import com.ecominecolonies.crew.CrewSession;
import com.ecominecolonies.crew.CrewSessions;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.nick.prestol.Prestol;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

public final class CrewMaterials {
   private static final Set<IAIState> SAFE = Set.of(
      AIWorkerState.BUILDING_STEP, AIWorkerState.NEEDS_ITEM, AIWorkerState.START_WORKING, AIWorkerState.IDLE, AIWorkerState.START_BUILDING
   );
   private static final Map<String, Long> PRICED = new HashMap<>();
   private static Field lo;
   private static Field hi;
   private static boolean warned;

   private CrewMaterials() {
   }

   public static void forgetAll() {
      PRICED.clear();
   }

   public static void tick(MinecraftServer server) {
      Set<String> seen = new HashSet<>();

      for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
         for (CrewSession session : CrewSessions.get().sessionsOf(colony)) {
            if (session.hasGeometry() && session.getMembers().size() >= 2) {
               for (CrewMember member : session.getMembers()) {
                  String key = colony.getID() + "/" + member.getHut().asLong();
                  seen.add(key);
                  long sector = sector(member);
                  Long priced = PRICED.get(key);
                  if (priced == null || priced != sector) {
                     boolean whole = (int)(sector >> 32) <= 0 && (int)sector >= session.getLength() - 1;
                     if (member.width() > 0 && requested(colony, member) && (priced != null || !whole)) {
                        if (reprice(colony, member)) {
                           PRICED.put(key, sector);
                        }
                     } else {
                        PRICED.put(key, sector);
                     }
                  }
               }
            }
         }
      }

      PRICED.keySet().retainAll(seen);
   }

   private static boolean requested(IColony colony, CrewMember member) {
      if (!member.isLead()) {
         return member.isRequested();
      } else {
         AbstractBuildingStructureBuilder hut = hut(colony, member);
         IBuilderWorkOrder order = hut == null ? null : hut.getWorkOrder();
         return order != null && order.isRequested();
      }
   }

   private static boolean reprice(IColony colony, CrewMember member) {
      AbstractBuildingStructureBuilder hut = hut(colony, member);
      if (hut != null && hut.getWorkOrder() != null) {
         ICitizenData builder = (ICitizenData)hut.getAllAssignedCitizen().stream().findFirst().orElse(null);
         if (builder == null || builder.getJob() == null || !(builder.getJob().getWorkerAI() instanceof AbstractEntityAIStructure<?, ?> ai)) {
            return true;
         } else if (!SAFE.contains(ai.getState())) {
            return false;
         } else {
            int before = hut.getNeededResources().values().stream().mapToInt(r -> r.getAmount()).sum();
            if (member.isLead()) {
               hut.getWorkOrder().setRequested(false);
            } else {
               member.setRequested(false);
            }

            hut.resetNeededResources();
            int cancelled = 0;

            for (IRequest<?> request : hut.getOpenRequestsOfCitizenOrBuilding(builder.getId(), r -> r.getRequest() instanceof Stack)) {
               colony.getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
               cancelled++;
            }

            ai.getStateAI().transitionToNext(new TickingTransition(AIWorkerState.LOAD_STRUCTURE, () -> true, () -> AIWorkerState.LOAD_STRUCTURE, 1));
            Prestol.LOGGER
               .info(
                  "Prestol: crew builder {} of {} re-prices his part {} (list had {} items, {} deliveries called off)",
                  new Object[]{builder.getName(), colony.getName(), sectorText(member), before, cancelled}
               );
            return true;
         }
      } else {
         return true;
      }
   }

   private static AbstractBuildingStructureBuilder hut(IColony colony, CrewMember member) {
      IBuilding building = (IBuilding)colony.getServerBuildingManager().getBuilding(member.getHut());
      return building instanceof AbstractBuildingStructureBuilder builder ? builder : null;
   }

   private static long sector(CrewMember member) {
      try {
         if (lo == null) {
            lo = CrewMember.class.getDeclaredField("lo");
            hi = CrewMember.class.getDeclaredField("hi");
            lo.setAccessible(true);
            hi.setAccessible(true);
         }

         return (long)lo.getInt(member) << 32 | (long)hi.getInt(member) & 4294967295L;
      } catch (RuntimeException | ReflectiveOperationException var2) {
         if (!warned) {
            warned = true;
            Prestol.LOGGER.warn("Prestol: cannot read crew sectors of EcoMinecolonies; crews keep their own material lists", var2);
         }

         return (long)member.width();
      }
   }

   private static String sectorText(CrewMember member) {
      long s = sector(member);
      return (int)(s >> 32) + ".." + (int)s;
   }
}
