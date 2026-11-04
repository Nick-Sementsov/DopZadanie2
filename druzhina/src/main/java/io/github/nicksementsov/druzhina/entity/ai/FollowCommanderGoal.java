package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

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
        if (Vessels.vesselOf(this.soldier.commander()) != null) {
            // he is at sea: the men go aboard (BoardGoal) or wait on the shore, they do not swim after him
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
                && !this.soldier.isCrew() && Vessels.vesselOf(this.commander) == null
                && this.soldier.distanceToSqr(this.commander) > (STOP + this.room()) * (STOP + this.room());
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
            this.soldier.teleportNear(this.commander);
            return;
        }
        boolean hurry = dist > 12 || this.commander.isSprinting() || this.commander.isPassenger();
        this.soldier.setSprinting(hurry && dist > 8 && steed == null);
        // a horse walks beside a man on foot, and gallops after a rider
        double speed = hurry ? 1.2 : 0.95;
        this.soldier.getNavigation().moveTo(this.commander, steed != null && !hurry ? speed * 0.6 : speed);
    }
}
