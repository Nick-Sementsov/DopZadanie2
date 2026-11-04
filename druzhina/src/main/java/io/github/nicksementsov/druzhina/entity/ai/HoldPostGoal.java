package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.March;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A soldier told to hold goes to his place in the line and stands there, facing where he was told to face. On a march
 * (see {@link March}) he keeps his place in the moving ranks on the way: he hurries when he falls behind and slows
 * down when he is ahead.
 */
public class HoldPostGoal extends Goal {
    private final Soldier soldier;
    private int pathDelay;
    private double lastX;
    private double lastZ;

    public HoldPostGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.soldier.orders() == Orders.HOLD && this.soldier.post() != null && this.soldier.getTarget() == null && !this.soldier.isCrew();
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
        this.soldier.setSprinting(false);
    }

    @Override
    public void tick() {
        March march = this.soldier.march();
        if (march != null) {
            march.tick();
            Vec3 point = march.pointFor(this.soldier);
            if (point != null) {
                this.keepStep(point, march);
                return;
            }
        }
        this.soldier.setSprinting(false);
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
                    this.soldier.relocate(post.x, post.y, post.z);
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
            this.face(this.soldier.postYaw());
        }
    }

    /** On the march: to his place in the moving ranks, at the pace of the march. */
    private void keepStep(Vec3 point, March march) {
        double dx = point.x - this.soldier.getX();
        double dz = point.z - this.soldier.getZ();
        double gap = Math.sqrt(dx * dx + dz * dz);
        double step = march.stepFor(this.soldier);
        if (step <= 0 && gap < 0.4) {
            // forming up, and in his place: he waits facing the way they will go
            this.soldier.getNavigation().stop();
            this.face(march.heading());
            return;
        }
        // behind his place he hurries, ahead of it he slows
        double ahead = step > 0 ? (dx * -Mth.sin(march.heading() * Mth.DEG_TO_RAD) + dz * Mth.cos(march.heading() * Mth.DEG_TO_RAD)) : gap;
        if (step > 0 && ahead < -0.4 && gap * gap - ahead * ahead < 1.0) {
            // ahead of his place: he lets the ranks come up to him rather than turn back
            this.soldier.getNavigation().stop();
            this.soldier.setSprinting(false);
            this.face(march.heading());
            return;
        }
        double speed = Mth.clamp(step + Math.max(0, ahead) * 0.25 + gap * 0.08, step > 0 ? Math.min(step, 0.45) : 0.6, 1.3);
        this.soldier.setSprinting(speed > 1.1 && this.soldier.steed() == null);
        if (gap < 2.0 && Math.abs(point.y - this.soldier.getY()) < 1.2) {
            // close by on open ground: straight to it, no path needed
            this.soldier.getNavigation().stop();
            this.soldier.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed);
            return;
        }
        boolean moved = (point.x - this.lastX) * (point.x - this.lastX) + (point.z - this.lastZ) * (point.z - this.lastZ) > 1.0;
        if (--this.pathDelay <= 0 || moved && this.pathDelay < 4 || this.soldier.getNavigation().isDone()) {
            this.pathDelay = 8;
            this.lastX = point.x;
            this.lastZ = point.z;
            this.soldier.getNavigation().moveTo(point.x, point.y, point.z, speed);
        } else {
            this.soldier.getNavigation().setSpeedModifier(speed);
        }
    }

    /** Faces the front, as one with the line, and so does his horse. */
    private void face(float to) {
        float yaw = Mth.approachDegrees(this.soldier.getYRot(), to, 12);
        this.soldier.setYRot(yaw);
        this.soldier.setYHeadRot(yaw);
        this.soldier.yBodyRot = yaw;
        this.soldier.setXRot(0);
        Mob steed = this.soldier.steed();
        if (steed != null) {
            float turn = Mth.approachDegrees(steed.getYRot(), to, 6);
            steed.setYRot(turn);
            steed.yBodyRot = turn;
            steed.setYHeadRot(turn);
        }
    }
}
