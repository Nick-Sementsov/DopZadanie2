package com.nick.prestol.compat;

import com.minecolonies.api.colony.IColony;
import com.mojang.authlib.GameProfile;
import com.nick.prestol.realm.Realm;
import com.nick.prestol.realm.RealmData;
import com.nick.prestol.realm.Realms;
import com.nick.prestol.realm.Settlements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import ydmsama.hundred_years_war.main.entity.entities.BaseCombatEntity;
import ydmsama.hundred_years_war.main.registry.HywAttributes;
import ydmsama.hundred_years_war.main.entity.entities.puppets.IPuppet;
import ydmsama.hundred_years_war.main.entity.entities.tags.NonCombatUnit;
import ydmsama.hundred_years_war.main.entity.entities.tags.SiegeUnit;
import ydmsama.hundred_years_war.main.supply.SupplyManager;
import ydmsama.hundred_years_war.main.supply.SupplyPointBlockEntity;
import ydmsama.hundred_years_war.main.utils.RelationSystem;
import ydmsama.hundred_years_war.main.utils.TeamRelationData;
import ydmsama.hundred_years_war.main.utils.RelationSystem.RelationType;
import ydmsama.hundred_years_war.main.utils.TeamRelationData.MemberType;

final class HywBridge {
   private HywBridge() {
   }

   static void sync(MinecraftServer server, RealmData data) {
      Set<UUID> changed = new HashSet<>();
      Map<UUID, Map<UUID, MemberType>> wanted = assign(data);
      Set<UUID> realmTeams = new HashSet<>();

      for (Realm r : data.all()) {
         realmTeams.add(r.id());
      }

      for (Realm realm : data.all()) {
         UUID teamId = realm.id();
         Map<UUID, MemberType> want = wanted.getOrDefault(teamId, Map.of());
         TeamRelationData team = RelationSystem.getOrCreateTeamRelationData(teamId, realm.name());
         if (!realm.name().equals(team.getTeamName())) {
            team.setTeamName(realm.name());
         }

         for (Entry<UUID, MemberType> e : want.entrySet()) {
            UUID player = e.getKey();
            if (RelationSystem.getRelationData(player) == null) {
               RelationSystem.addPlayerAndEnsureRelations(player, nameOf(server, player));
            }

            if (!team.isMember(player)) {
               RelationSystem.joinTeam(player, teamId, e.getValue());
               changed.add(player);
            } else if (team.getMemberType(player) != e.getValue()) {
               team.updateMemberType(player, e.getValue());
               changed.add(player);
            }

            for (UUID other : new ArrayList<>(RelationSystem.getPlayerTeams(player))) {
               if (!other.equals(teamId) && !realmTeams.contains(other)) {
                  RelationSystem.leaveTeam(player, other);
                  changed.add(player);
               }
            }
         }

         for (UUID member : new ArrayList<>(team.getAllMembers().keySet())) {
            if (!want.containsKey(member)) {
               if (team.getMemberType(member) == MemberType.OWNER) {
                  team.updateMemberType(member, MemberType.MEMBER);
               }

               RelationSystem.leaveTeam(member, teamId);
               changed.add(member);
            }
         }
      }

      List<Realm> realms = new ArrayList<>(data.all());

      for (int i = 0; i < realms.size(); i++) {
         for (int j = i + 1; j < realms.size(); j++) {
            Realm a = realms.get(i);
            Realm b = realms.get(j);
            RelationType want = data.war(a.id(), b.id()) != null
               ? RelationType.HOSTILE
               : (a.allies().contains(b.id()) && b.allies().contains(a.id()) ? RelationType.FRIENDLY : RelationType.NEUTRAL);
            Set<UUID> left = new HashSet<>(members(a));
            left.add(a.id());
            Set<UUID> right = new HashSet<>(members(b));
            right.add(b.id());

            for (UUID x : left) {
               for (UUID y : right) {
                  setBoth(x, y, want, changed);
               }
            }
         }
      }

      for (UUID id : changed) {
         RelationSystem.syncRelationsToClient(id);
      }
   }

   private static Map<UUID, Map<UUID, MemberType>> assign(RealmData data) {
      Map<UUID, Map<UUID, MemberType>> out = new LinkedHashMap<>();
      Map<UUID, UUID> claimed = new HashMap<>();

      for (Realm r : data.all()) {
         out.put(r.id(), new LinkedHashMap<>());
         if (claimed.putIfAbsent(r.ruler(), r.id()) == null) {
            out.get(r.id()).put(r.ruler(), MemberType.OWNER);
         }
      }

      for (Realm rx : data.all()) {
         for (IColony c : Realms.members(rx)) {
            UUID owner = Settlements.owner(c);
            if (owner != null && claimed.putIfAbsent(owner, rx.id()) == null) {
               out.get(rx.id()).put(owner, MemberType.ADMIN);
            }
         }
      }

      return out;
   }

   static void removeTeam(MinecraftServer server, Realm realm) {
      TeamRelationData team = RelationSystem.getTeamRelationData(realm.id());
      if (team != null) {
         Set<UUID> mine = new HashSet<>(team.getAllMembers().keySet());

         for (UUID a : mine) {
            Map<UUID, RelationType> rel = RelationSystem.getAllRelations(a);
            if (rel != null) {
               for (UUID b : new ArrayList<>(rel.keySet())) {
                  if (RelationSystem.getRelation(a, b) == RelationType.HOSTILE) {
                     setBoth(a, b, RelationType.NEUTRAL, null);
                  }
               }
            }
         }

         List<UUID> owners = team.getOwners();
         UUID owner = owners.isEmpty() ? (UUID)team.getAllMembers().keySet().stream().findFirst().orElse(null) : owners.get(0);
         if (owner != null) {
            if (owners.isEmpty()) {
               team.updateMemberType(owner, MemberType.OWNER);
            }

            RelationSystem.disbandTeam(realm.id(), owner);
         }

         for (UUID m : mine) {
            RelationSystem.syncRelationsToClient(m);
         }
      }
   }

   private static void setBoth(UUID a, UUID b, RelationType want, Set<UUID> changed) {
      if (!a.equals(b)) {
         if (RelationSystem.getRelation(a, b) != want) {
            RelationSystem.setRelation(a, b, want);
            if (changed != null) {
               changed.add(a);
            }
         }

         if (RelationSystem.getRelation(b, a) != want) {
            RelationSystem.setRelation(b, a, want);
            if (changed != null) {
               changed.add(b);
            }
         }
      }
   }

   private static Set<UUID> members(Realm realm) {
      TeamRelationData team = RelationSystem.getTeamRelationData(realm.id());
      return team == null ? Set.of() : team.getAllMembers().keySet();
   }

   private static String nameOf(MinecraftServer server, UUID id) {
      ServerPlayer online = server.getPlayerList().getPlayer(id);
      return online != null
         ? online.getGameProfile().getName()
         : server.getProfileCache().get(id).<String>map(GameProfile::getName).orElse(id.toString().substring(0, 8));
   }

   static Entity spawn(ServerLevel level, String kind, UUID owner, BlockPos at, BlockPos home, int rank, int tier) {
      EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation("hundred_years_war", kind)).orElse(null);
      Entity e = type == null ? null : type.create(level);
      if (e instanceof BaseCombatEntity unit) {
         unit.moveTo((double)at.getX() + 0.5, (double)at.getY(), (double)at.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
         unit.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
         CompoundTag t = new CompoundTag();
         unit.saveWithoutId(t);
         t.putUUID("OwnerUUID", owner);
         unit.load(t);
         // setLevel() only changes the number; applyLevelWithStats() also grants the health and damage of each level
         unit.applyLevelWithStats(Math.max(1, rank));
         unit.setEquipment(Math.max(0, tier));
         unit.setRequiresSupply(false);
         unit.setHomePosition(home);
         unit.setPersistenceRequired();
         return level.addFreshEntity(unit) ? unit : null;
      } else {
         if (e != null) {
            e.discard();
         }

         return null;
      }
   }

   static void orders(Entity e, BlockPos home, List<BlockPos> patrol, LivingEntity follow) {
      if (e instanceof BaseCombatEntity unit) {
         unit.clearCommandedGoals();
         unit.clearFollowTargets();
         unit.getPatrolPoints().clear();
         unit.setHomePosition(home);
         if (follow != null) {
            unit.setFollowTarget(follow);
         } else {
            unit.getPatrolPoints().addAll(patrol);
         }
      }
   }

   /** Soldiers that eat: owned, fighting, alive units; not workers, siege engines or puppets. */
   static boolean eater(Entity e) {
      return e instanceof BaseCombatEntity u
         && !(e instanceof NonCombatUnit)
         && !(e instanceof SiegeUnit)
         && !(e instanceof IPuppet)
         && u.isAlive()
         && u.getOwnerUUID() != null;
   }

   /** Switches a unit from Hundred Years War supply to Prestol provisions. */
   static void noSupply(Entity e) {
      if (e instanceof BaseCombatEntity u && u.requiresSupply()) {
         u.setRequiresSupply(false);
      }
   }

   /**
    * Draws up to {@code points} of provisions from the owner's supply points in range.
    * A unit of HYW supply feeds a soldier for a quarter of a day; a full provisions bar (100) lasts {@code days} days.
    */
   static double fromSupplyPoints(ServerLevel level, BlockPos pos, UUID owner, double points, int days) {
      double perPoint = 4.0 * days / 100.0;
      double got = 0.0;

      for (SupplyManager.SupplySource source : SupplyManager.getSupplySources().values()) {
         if (got >= points) {
            break;
         }

         if (source.getType() == SupplyManager.SupplySourceType.SUPPLY_POINT
            && source.getBlockPos() != null
            && level.dimension().equals(source.getDimension())
            && (source.getOwnerId() == null || source.getOwnerId().equals(owner))) {
            int range = level.getBlockEntity(source.getBlockPos()) instanceof SupplyPointBlockEntity sp ? sp.getSupplyRange() : 16;
            if (source.getBlockPos().distSqr(pos) <= (double)range * range) {
               double want = Math.min(points - got, source.getSupplyValue() / perPoint);
               if (want > 0.0 && source.consumeSupply(want * perPoint)) {
                  got += want;
               }
            }
         }
      }

      return got;
   }

   static Attribute rangedDamage() {
      return HywAttributes.RANGED_ATTACK_DAMAGE.get();
   }

   static boolean fighter(Entity e) {
      return e instanceof BaseCombatEntity && !(e instanceof NonCombatUnit);
   }

   static int rank(Entity e) {
      return e instanceof BaseCombatEntity unit ? unit.getLevel() : -1;
   }

   static void rankUp(Entity e) {
      if (e instanceof BaseCombatEntity unit) {
         // levelUp() subtracts the level threshold from the experience and drives it below zero;
         // giving exactly the missing experience lets HYW level the unit up the normal way
         int missing = unit.getLevel() * 20 - unit.getExperiencePoints();
         unit.addExperience(Math.max(1, missing));
      }
   }
}
