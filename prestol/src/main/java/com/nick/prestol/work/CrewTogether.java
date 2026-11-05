package com.nick.prestol.work;

import com.ecominecolonies.crew.CrewMember;
import com.ecominecolonies.crew.CrewSession;
import com.ecominecolonies.crew.CrewSessions;
import com.ecominecolonies.crew.StageOrder;
import com.ldtteam.structurize.placement.AbstractBlueprintIterator;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.nick.prestol.Prestol;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;

final class CrewTogether {
   private static final int SITE = 48;
   private static Field lo;
   private static Field hi;
   private static Field finished;
   private static Field waiting;
   private static Field stage;
   private static Method dirty;
   private static boolean broken;

   private CrewTogether() {
   }

   static boolean atSite(AbstractEntityCitizen worker) {
      CrewMember member = CrewSessions.get().memberOf(worker);
      if (member == null) {
         return false;
      } else {
         CrewSession session = member.getSession();
         if (session != null && session.getMembers().size() >= 2) {
            BlockPos site = session.getSite();
            double dx = worker.getX() - (double)site.getX();
            double dz = worker.getZ() - (double)site.getZ();
            return dx * dx + dz * dz <= 2304.0;
         } else {
            return false;
         }
      }
   }

   static void tick() {
      if (!broken) {
         try {
            init();

            for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
               for (CrewSession session : CrewSessions.get().sessionsOf(colony)) {
                  if (session.hasGeometry() && session.getMembers().size() >= 2) {
                     share(colony, session);
                  }
               }
            }
         } catch (RuntimeException | ReflectiveOperationException var4) {
            broken = true;
            Prestol.LOGGER.warn("Prestol: cannot share crew parts; crews keep EcoMinecolonies' own way", var4);
         }
      }
   }

   private static void init() throws ReflectiveOperationException {
      if (lo == null) {
         lo = field("lo");
         hi = field("hi");
         finished = field("finishedRank");
         waiting = field("waiting");
         stage = field("currentStage");
         dirty = CrewSessions.class.getDeclaredMethod("markDirty");
         dirty.setAccessible(true);
      }
   }

   private static Field field(String name) throws NoSuchFieldException {
      Field f = CrewMember.class.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   private static void share(IColony colony, CrewSession session) throws ReflectiveOperationException {
      for (CrewMember idle : session.getMembers()) {
         if (idle.isWaiting() || idle.width() <= 0) {
            CrewMember donor = null;
            BuildingProgressStage now = null;

            for (CrewMember m : session.getMembers()) {
               if (m != idle && !m.isWaiting() && rows(session, m) >= 2) {
                  BuildingProgressStage theirs = stageOf(colony, m);
                  if (theirs != null
                     && !StageOrder.isFinal(theirs)
                     && finished.getInt(m) < StageOrder.rank(theirs)
                     && (donor == null || rows(session, m) > rows(session, donor))) {
                     donor = m;
                     now = theirs;
                  }
               }
            }

            if (donor != null) {
               AbstractBuildingStructureBuilder idleHut = hut(colony, idle.getHut());
               if (idleHut != null && idleHut.getWorkOrder() != null && idleHut.getWorkOrder().getID() == session.getWorkOrderId()) {
                  int rank = StageOrder.rank(now);
                  int dLo = lo.getInt(donor);
                  int dHi = hi.getInt(donor);
                  int mid = dLo + rows(session, donor) / 2 - 1;
                  lo.setInt(idle, mid + 1);
                  hi.setInt(idle, dHi);
                  hi.setInt(donor, mid);
                  finished.setInt(idle, rank - 1);
                  waiting.setBoolean(idle, false);
                  stage.set(idle, now);
                  idleHut.setProgressPos(AbstractBlueprintIterator.NULL_POS, now);
                  dirty.invoke(CrewSessions.get());
                  Prestol.LOGGER
                     .info(
                        "Prestol: crew at {} - builder of {} takes rows {}..{} of {} from {}",
                        new Object[]{
                           session.getSite().toShortString(),
                           idle.getHut().toShortString(),
                           mid + 1,
                           dHi == Integer.MAX_VALUE ? session.getLength() : dHi,
                           now,
                           donor.getHut().toShortString()
                        }
                     );
                  return;
               }
            }
         }
      }
   }

   private static int rows(CrewSession session, CrewMember m) throws IllegalAccessException {
      int h = hi.getInt(m) == Integer.MAX_VALUE ? session.getLength() : hi.getInt(m);
      return Math.max(0, h - lo.getInt(m) + 1);
   }

   private static BuildingProgressStage stageOf(IColony colony, CrewMember member) throws ReflectiveOperationException {
      BuildingProgressStage s = (BuildingProgressStage)stage.get(member);
      if (s != null) {
         return s;
      } else {
         AbstractBuildingStructureBuilder hut = hut(colony, member.getHut());
         if (hut != null) {
            Tuple<?, ?> progress = hut.getProgress();
            if (progress != null) {
               Object var6 = progress.getB();
               if (var6 instanceof BuildingProgressStage) {
                  return (BuildingProgressStage)var6;
               }
            }
         }

         if (hut != null && hut.getWorkOrder() != null) {
            return hut.getWorkOrder().getWorkOrderType() == WorkOrderType.REMOVE ? BuildingProgressStage.REMOVE_WATER : BuildingProgressStage.CLEAR;
         } else {
            return null;
         }
      }
   }

   private static AbstractBuildingStructureBuilder hut(IColony colony, BlockPos pos) {
      IBuilding b = (IBuilding)colony.getServerBuildingManager().getBuilding(pos);
      return b instanceof AbstractBuildingStructureBuilder s ? s : null;
   }
}
