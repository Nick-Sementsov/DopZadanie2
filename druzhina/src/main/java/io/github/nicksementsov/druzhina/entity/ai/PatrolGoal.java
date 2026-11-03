package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** A soldier on patrol walks his round from end to end and back, stopping a moment at every place of it. */
public class PatrolGoal extends Goal {
    private final Soldier soldier;
    @Nullable
    private Vec3 to;
    private int pause;
    private int pathDelay;

    public PatrolGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.soldier.orders() == Orders.PATROL && this.soldier.getTarget() == null && !this.soldier.patrol().isEmpty();
    }

    @Override
    public void start() {
        this.to = this.soldier.nextWaypoint(false);
        this.pause = 0;
        this.pathDelay = 0;
    }

    @Override
    public void stop() {
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.to == null) {
            return;
        }
        if (this.pause > 0) {
            this.pause--;
            return;
        }
        double dx = this.to.x - this.soldier.getX();
        double dz = this.to.z - this.soldier.getZ();
        if (dx * dx + dz * dz < 2.0 * 2.0) {
            // a look around at every place of the round
            this.pause = 30 + this.soldier.getRandom().nextInt(50);
            this.to = this.soldier.nextWaypoint(true);
            this.soldier.getNavigation().stop();
            return;
        }
        if (--this.pathDelay <= 0) {
            this.pathDelay = 20;
            if (!this.soldier.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, 0.7) && dx * dx + dz * dz > 100 * 100) {
                this.soldier.teleportTo(this.to.x, this.to.y, this.to.z);
            }
        }
    }
}
