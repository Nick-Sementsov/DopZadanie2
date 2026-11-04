package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Soldiers told to follow walk behind their commander, and catch up with him when he rides away. */
public class FollowCommanderGoal extends Goal {
    private static final double START = 6;
    private static final double STOP = 3.5;
    private static final double TELEPORT = 40;

    private final Soldier soldier;
    @Nullable
    private Player commander;
    private int pathDelay;

    public FollowCommanderGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.soldier.orders() == Orders.HOLD || this.soldier.orders() == Orders.PATROL || this.soldier.getTarget() != null || this.soldier.isCrew()) {
            return false;
        }
        Player boss = this.soldier.commander();
        double start = START + this.room();
        if (boss == null || boss.isSpectator() || boss.level() != this.soldier.level() || this.soldier.distanceToSqr(boss) < start * start) {
            return false;
        }
        this.commander = boss;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.commander != null && this.commander.isAlive() && !this.commander.isSpectator() && this.soldier.getTarget() == null
                && this.soldier.orders() != Orders.HOLD && this.soldier.orders() != Orders.PATROL && this.commander.level() == this.soldier.level()
                && !this.soldier.isCrew() && this.soldier.distanceToSqr(this.commander) > (STOP + this.room()) * (STOP + this.room());
    }

    /** A rider keeps further off: the horse (or the elephant) is wider than a man. */
    private double room() {
        Mob steed = this.soldier.steed();
        return steed == null ? 0 : steed.getBbWidth();
    }

    @Override
    public void start() {
        this.pathDelay = 0;
    }

    @Override
    public void stop() {
        this.commander = null;
        this.soldier.getNavigation().stop();
        this.soldier.setSprinting(false);
    }

    @Override
    public void tick() {
        if (this.commander == null) {
            return;
        }
        this.soldier.getLookControl().setLookAt(this.commander, 10, this.soldier.getMaxHeadXRot());
        if (--this.pathDelay > 0) {
            return;
        }
        this.pathDelay = 10;
        double dist = this.soldier.distanceTo(this.commander);
        Mob steed = this.soldier.steed();
        if (dist > TELEPORT && (!this.soldier.isPassenger() || steed != null)) {
            this.teleportNear(this.commander, steed);
            return;
        }
        boolean hurry = dist > 12 || this.commander.isSprinting() || this.commander.isPassenger();
        this.soldier.setSprinting(hurry && dist > 8 && steed == null);
        // a horse walks beside a man on foot, and gallops after a rider
        double speed = hurry ? 1.2 : 0.95;
        this.soldier.getNavigation().moveTo(this.commander, steed != null && !hurry ? speed * 0.6 : speed);
    }

    /** Somewhere free a few blocks behind the commander. */
    private void teleportNear(Player boss, @Nullable Mob steed) {
        BlockPos at = boss.blockPosition();
        Entity body = steed != null ? steed : this.soldier;
        int spread = steed != null ? 5 : 3;
        for (int i = 0; i < 12; i++) {
            int x = at.getX() + this.soldier.getRandom().nextInt(2 * spread + 1) - spread;
            int z = at.getZ() + this.soldier.getRandom().nextInt(2 * spread + 1) - spread;
            int y = at.getY() + this.soldier.getRandom().nextInt(3) - 1;
            if (Math.abs(x - at.getX()) < 2 && Math.abs(z - at.getZ()) < 2) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (WalkNodeEvaluator.getBlockPathTypeStatic(this.soldier.level(), pos.mutable()) == BlockPathTypes.WALKABLE
                    && !(this.soldier.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
                    && this.soldier.level().noCollision(body, body.getBoundingBox().move(pos.getCenter().subtract(body.position()).add(0, -0.5, 0)))) {
                if (steed != null) {
                    this.soldier.relocate(x + 0.5, y, z + 0.5);
                } else {
                    this.soldier.moveTo(x + 0.5, y, z + 0.5, this.soldier.getYRot(), this.soldier.getXRot());
                }
                this.soldier.getNavigation().stop();
                return;
            }
        }
    }
}
