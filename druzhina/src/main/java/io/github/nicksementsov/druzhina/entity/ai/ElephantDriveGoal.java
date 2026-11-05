package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The driver of a war elephant takes it at the enemy: the beast tramples and tosses on its tusks whatever is in its
 * way (see {@link WarElephant}), the archer in the howdah shoots over the driver's head.
 */
public class ElephantDriveGoal extends Goal {
    private final Soldier soldier;
    private int pathDelay;

    public ElephantDriveGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean driving() {
        return this.soldier.steed() instanceof WarElephant;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = this.soldier.getTarget();
        return t != null && t.isAlive() && this.driving() && this.soldier.canAttack(t) && this.soldier.withinReach(t) && !this.soldier.routed();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.soldier.getTarget();
        if (t == null || !t.isAlive() || !this.driving() || !this.soldier.canAttack(t) || this.soldier.routed()) {
            return false;
        }
        if (!this.soldier.withinReach(t)) {
            this.soldier.setTarget(null);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        this.pathDelay = 0;
        this.soldier.setAggressive(true);
    }

    @Override
    public void stop() {
        this.soldier.setAggressive(false);
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = this.soldier.getTarget();
        if (t == null || !(this.soldier.steed() instanceof WarElephant beast)) {
            return;
        }
        this.soldier.getLookControl().setLookAt(t, 30, 30);
        double reach = beast.getBbWidth() / 2 + t.getBbWidth() / 2 + 0.6;
        double dx = t.getX() - beast.getX();
        double dz = t.getZ() - beast.getZ();
        if (dx * dx + dz * dz < reach * reach) {
            // at him: face him and let the beast use its tusks
            this.soldier.getNavigation().stop();
            beast.getLookControl().setLookAt(t, 20, 20);
            beast.turnTo(t);
        } else if (--this.pathDelay <= 0) {
            this.pathDelay = 8;
            this.soldier.getNavigation().moveTo(t, 1.25);
        }
    }
}
