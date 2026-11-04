package io.github.nicksementsov.druzhina.api;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Role;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.registry.DruzhinaAttributes;
import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.Vec3;

/**
 * What other mods may do with soldiers: raise them for a commander, give them orders, train them, and tell who stands
 * with whom ({@link Relations}). Server side only.
 */
public final class DruzhinaApi {
    private DruzhinaApi() {
    }

    /**
     * Raises a soldier of {@code role} (a role id: swordsman, spearman, axeman, archer, crossbowman, knight, militia)
     * for {@code owner} at {@code at}, armed as the given grade (0 levies, 1 men at arms, 2 picked men) and of the
     * given rank (0 to {@link #maxRank()}). Returns null when the role is unknown or he could not be placed.
     */
    @Nullable
    public static Soldier spawn(ServerLevel level, String role, UUID owner, BlockPos at, int grade, int rank) {
        Role r = Role.byName(role);
        if (r == null || r.bandit) {
            return null;
        }
        Soldier s = DruzhinaRegistry.SOLDIER.get().create(level);
        if (s == null) {
            return null;
        }
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360.0f, 0.0f);
        s.enlist(r, owner, grade, level.random);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null, null);
        if (rank > 0) {
            s.setRank(rank);
        }
        s.order(Orders.HOLD, Vec3.atBottomCenterOf(at), s.getYRot());
        return level.addFreshEntity(s) ? s : null;
    }

    /** A soldier who serves someone: not a bandit, not a free company. */
    public static boolean isSoldier(Entity e) {
        return e instanceof Soldier s && !s.isBandit() && s.getOwnerUUID() != null;
    }

    @Nullable
    public static UUID owner(Entity e) {
        return e instanceof Soldier s ? s.getOwnerUUID() : null;
    }

    /** Stand at {@code home} (and fight what comes close). */
    public static void hold(Entity e, BlockPos home) {
        if (e instanceof Soldier s) {
            s.order(Orders.HOLD, Vec3.atBottomCenterOf(home), s.getYRot());
        }
    }

    /** Walk the round through {@code points}, back and forth. */
    public static void patrol(Entity e, List<BlockPos> points) {
        if (e instanceof Soldier s) {
            List<Vec3> round = new ArrayList<>();
            for (BlockPos p : points) {
                round.add(Vec3.atBottomCenterOf(p));
            }
            if (round.isEmpty()) {
                s.order(Orders.HOLD, s.position(), s.getYRot());
            } else {
                s.patrol(round);
            }
        }
    }

    /** Follow {@code leader} (null: his own commander). */
    public static void follow(Entity e, @Nullable LivingEntity leader) {
        if (e instanceof Soldier s) {
            s.follow(leader == null ? null : leader.getUUID());
        }
    }

    /** His rank, 0 (a recruit) to {@link #maxRank()}; -1 for anything but a soldier. */
    public static int rank(Entity e) {
        return e instanceof Soldier s ? s.rank() : -1;
    }

    public static void promote(Entity e) {
        if (e instanceof Soldier s) {
            s.promote();
        }
    }

    public static int maxRank() {
        return Soldier.maxRank();
    }

    /** Kept by the calling mod: the commander's baton does not move him and he cannot be dismissed by hand. */
    public static void setManaged(Entity e, boolean managed) {
        if (e instanceof Soldier s) {
            s.setManaged(managed);
        }
    }

    /** Names him; the name is kept when he rises in rank. */
    public static void setName(Entity e, Component name) {
        if (e instanceof Soldier s) {
            s.setOwnName(name);
        }
    }

    /** Damage added to every arrow and bolt a soldier shoots. */
    public static Attribute rangedDamage() {
        return DruzhinaAttributes.RANGED_DAMAGE.get();
    }
}
