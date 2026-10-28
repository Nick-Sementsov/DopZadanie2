package com.nick.prestol.work;

import com.google.common.collect.UnmodifiableIterator;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLumberjack;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.colony.jobs.JobLumberjack;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.nick.prestol.PrestolConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public final class WorkerFixes {
   private static final int AREA_SLACK = 4;

   private WorkerFixes() {
   }

   public static void tick(MinecraftServer server) {
      for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
         if (colony.isActive()) {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
               if (citizen.getJob() != null) {
                  ITickingStateAI entity = citizen.getJob().getWorkerAI();
                  if (entity instanceof AbstractEntityAIBasic) {
                     AbstractEntityAIBasic<?, ?> ai = (AbstractEntityAIBasic<?, ?>)entity;
                     AbstractEntityCitizen entityx = (AbstractEntityCitizen)citizen.getEntity().orElse(null);
                     if (entityx != null) {
                        IState state = ai.getState();
                        if (state == AIWorkerState.NEEDS_ITEM && citizen.getWorkBuilding() instanceof AbstractBuilding building) {
                           dropToolRequestsHeldAlready(citizen, building, entityx.getInventoryCitizen());
                           dropItemRequestsHeldAlready(citizen, building, entityx.getInventoryCitizen());
                        }

                        if (citizen.getJob() instanceof JobLumberjack) {
                           IBuilding var12 = citizen.getWorkBuilding();
                           if (var12 instanceof BuildingLumberjack) {
                              BuildingLumberjack hut = (BuildingLumberjack)var12;
                              if (state == AIWorkerState.START_WORKING || state == AIWorkerState.LUMBERJACK_START_WORKING) {
                                 skipWalkHome(ai, hut, entityx);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void dropToolRequestsHeldAlready(ICitizenData citizen, AbstractBuilding building, IItemHandler bag) {
      UnmodifiableIterator var3 = building.getOpenRequestsOfTypeFiltered(citizen, TypeToken.of(Tool.class), r -> true).iterator();

      while (var3.hasNext()) {
         IRequest<? extends Tool> request = (IRequest<? extends Tool>)var3.next();
         if (holds(bag, (IDeliverable)request.getRequest())) {
            building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
         }
      }
   }

   private static void dropItemRequestsHeldAlready(ICitizenData citizen, AbstractBuilding building, IItemHandler bag) {
      UnmodifiableIterator var3 = building.getOpenRequestsOfTypeFiltered(citizen, TypeConstants.DELIVERABLE, r -> !(r.getRequest() instanceof Tool)).iterator();

      while (var3.hasNext()) {
         IRequest<? extends IDeliverable> request = (IRequest<? extends IDeliverable>)var3.next();
         IDeliverable wanted = (IDeliverable)request.getRequest();
         int have = 0;

         for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack s = bag.getStackInSlot(i);
            if (!s.isEmpty() && wanted.matches(s)) {
               have += s.getCount();
            }
         }

         if (have > 0 && have >= Math.max(1, wanted.getMinimumCount())) {
            building.getColony().getRequestManager().updateRequestState(request.getId(), RequestState.CANCELLED);
         }
      }
   }

   private static boolean holds(IItemHandler bag, IDeliverable wanted) {
      for (int i = 0; i < bag.getSlots(); i++) {
         ItemStack s = bag.getStackInSlot(i);
         if (!s.isEmpty() && wanted.matches(s)) {
            return true;
         }
      }

      return false;
   }

   private static void skipWalkHome(AbstractEntityAIBasic<?, ?> ai, BuildingLumberjack hut, AbstractEntityCitizen entity) {
      BlockPos a = hut.getStartRestriction();
      BlockPos b = hut.getEndRestriction();
      if (hut.shouldRestrict() && a != null && b != null && inside(entity.blockPosition(), a, b)) {
         IItemHandler bag = entity.getInventoryCitizen();
         boolean axe = false;

         for (int i = 0; i < bag.getSlots() && !axe; i++) {
            axe = bag.getStackInSlot(i).getItem() instanceof AxeItem;
         }

         if (axe) {
            ai.getStateAI().transitionToNext(new TickingTransition(AIWorkerState.PREPARING, () -> true, () -> AIWorkerState.PREPARING, 1));
         }
      }
   }

   private static boolean inside(BlockPos p, BlockPos a, BlockPos b) {
      return p.getX() >= Math.min(a.getX(), b.getX()) - 4
         && p.getX() <= Math.max(a.getX(), b.getX()) + 4
         && p.getZ() >= Math.min(a.getZ(), b.getZ()) - 4
         && p.getZ() <= Math.max(a.getZ(), b.getZ()) + 4;
   }

   public static EatingState foodWithoutRestaurant(AbstractEntityCitizen citizen) {
      ICitizenData data = citizen.getCitizenData();
      IColony colony = data == null ? null : data.getColony();
      if (colony != null && !(data.getSaturation() >= 10.0) && (Boolean)PrestolConfig.AI_FIXES.get()) {
         BlockPos from = data.getWorkBuilding() != null ? data.getWorkBuilding().getPosition() : citizen.blockPosition();
         if (colony.getServerBuildingManager().getBestBuilding(from, BuildingCook.class) != null) {
            return null;
         } else {
            List<IBuilding> places = new ArrayList<>();
            if (data.getHomeBuilding() != null) {
               places.add(data.getHomeBuilding());
            }

            for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
               if (b instanceof BuildingWareHouse && !places.contains(b)) {
                  places.add(b);
               }
            }

            IBuilding hall = (IBuilding)colony.getServerBuildingManager().getTownHall();
            if (hall != null && !places.contains(hall)) {
               places.add(hall);
            }

            places.sort(Comparator.comparingDouble(bx -> bx.getPosition().distSqr(citizen.blockPosition())));

            for (IBuilding place : places) {
               if (WorldUtil.isBlockLoaded(colony.getWorld(), place.getPosition())) {
                  ItemStorage food = FoodUtils.checkForFoodInBuilding(data, null, place);
                  if (food != null) {
                     if (!EntityNavigationUtils.walkToBuilding(citizen, place)) {
                        return EatingState.SEARCH_RESTAURANT;
                     }

                     if (InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(place, food, 4, citizen.getInventoryCitizen())) {
                        return EatingState.CHECK_FOR_FOOD;
                     }
                  }
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean empty(ItemStack s) {
      return ItemStackUtils.isEmpty(s);
   }
}
