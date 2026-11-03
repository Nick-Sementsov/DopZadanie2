package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;

/** A soldier told to hold goes to his place in the line and stands there, facing where he was told to face. */
public class HoldPostGoal extends Goal {
    private final Soldier soldier;
    private int pathDelay;

    public HoldPostGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.soldier.orders() == Orders.HOLD && this.soldier.post() != null && this.soldier.getTarget() == null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.pathDelay = 0;
    }

    @Override
    public void stop() {
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        Vec3 post = this.soldier.post();
        if (post == null) {
            return;
        }
        double dx = post.x - this.soldier.getX();
        double dz = post.z - this.soldier.getZ();
        double dist = dx * dx + dz * dz;
        if (dist > 0.35 * 0.35 || Math.abs(post.y - this.soldier.getY()) > 2.5) {
            if (--this.pathDelay <= 0) {
                this.pathDelay = 10;
                if (dist > 100 * 100) {
                    // far away (a new post across the land): he gets there at once
                    this.soldier.teleportTo(post.x, post.y, post.z);
                } else if (dist > 1.5 * 1.5) {
                    this.soldier.getNavigation().moveTo(post.x, post.y, post.z, dist > 64 ? 1.15 : 0.9);
                }
            }
            if (dist <= 1.5 * 1.5) {
                // the last steps to his exact place in the line
                this.soldier.getNavigation().stop();
                this.soldier.getMoveControl().setWantedPosition(post.x, post.y, post.z, 0.6);
            }
        } else {
            this.soldier.getNavigation().stop();
            // at his place: face the front, as one with the line
            float yaw = Mth.approachDegrees(this.soldier.getYRot(), this.soldier.postYaw(), 12);
            this.soldier.setYRot(yaw);
            this.soldier.setYHeadRot(yaw);
            this.soldier.yBodyRot = yaw;
            this.soldier.setXRot(0);
        }
    }
}
