package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
        if (this.soldier.orders() == Orders.HOLD || this.soldier.getTarget() != null) {
            return false;
        }
        Player boss = this.soldier.commander();
        if (boss == null || boss.isSpectator() || boss.level() != this.soldier.level() || this.soldier.distanceToSqr(boss) < START * START) {
            return false;
        }
        this.commander = boss;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.commander != null && this.commander.isAlive() && !this.commander.isSpectator() && this.soldier.getTarget() == null
                && this.soldier.orders() != Orders.HOLD && this.commander.level() == this.soldier.level()
                && this.soldier.distanceToSqr(this.commander) > STOP * STOP;
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
        if (dist > TELEPORT && !this.soldier.isPassenger()) {
            this.teleportNear(this.commander);
            return;
        }
        boolean hurry = dist > 12 || this.commander.isSprinting() || this.commander.isPassenger();
        this.soldier.setSprinting(hurry && dist > 8);
        this.soldier.getNavigation().moveTo(this.commander, hurry ? 1.2 : 0.95);
    }

    /** Somewhere free a few blocks behind the commander. */
    private void teleportNear(Player boss) {
        BlockPos at = boss.blockPosition();
        for (int i = 0; i < 12; i++) {
            int x = at.getX() + this.soldier.getRandom().nextInt(7) - 3;
            int z = at.getZ() + this.soldier.getRandom().nextInt(7) - 3;
            int y = at.getY() + this.soldier.getRandom().nextInt(3) - 1;
            if (Math.abs(x - at.getX()) < 2 && Math.abs(z - at.getZ()) < 2) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (WalkNodeEvaluator.getBlockPathTypeStatic(this.soldier.level(), pos.mutable()) == BlockPathTypes.WALKABLE
                    && !(this.soldier.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
                    && this.soldier.level().noCollision(this.soldier, this.soldier.getBoundingBox().move(pos.getCenter().subtract(this.soldier.position()).add(0, -0.5, 0)))) {
                this.soldier.moveTo(x + 0.5, y, z + 0.5, this.soldier.getYRot(), this.soldier.getXRot());
                this.soldier.getNavigation().stop();
                return;
            }
        }
    }
}
