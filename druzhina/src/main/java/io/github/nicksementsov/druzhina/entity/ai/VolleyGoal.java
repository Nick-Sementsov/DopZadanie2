package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;

/**
 * An archer's fight: he keeps his distance, draws, and shoots when the bow is full drawn. An enemy who comes too
 * close makes him step back while he draws.
 */
public class VolleyGoal extends Goal {
    private static final float RANGE = 22;

    private final Soldier soldier;
    private int seeTime;
    private int pathDelay;
    private int strafe;
    private int strafeTicks;
    private int drawFor;

    public VolleyGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = this.soldier.getTarget();
        return t != null && t.isAlive() && this.launcher() && !(this.soldier.steed() instanceof WarElephant)
                && this.soldier.canAttack(t) && this.soldier.withinReach(t) && !this.soldier.routed();
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.canUse()) {
            LivingEntity t = this.soldier.getTarget();
            if (t != null && !this.soldier.withinReach(t)) {
                this.soldier.setTarget(null);
            }
            return false;
        }
        return true;
    }

    /** A bow or a crossbow in hand. */
    private boolean launcher() {
        Item item = this.soldier.getMainHandItem().getItem();
        return item instanceof BowItem || item instanceof CrossbowItem;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.seeTime = 0;
        this.soldier.setAggressive(true);
    }

    @Override
    public void stop() {
        this.soldier.setAggressive(false);
        this.soldier.stopUsingItem();
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = this.soldier.getTarget();
        if (t == null) {
            return;
        }
        double dist = this.soldier.distanceTo(t);
        boolean sees = this.soldier.getSensing().hasLineOfSight(t);
        this.seeTime = sees ? Math.max(0, this.seeTime) + 1 : Math.min(0, this.seeTime) - 1;
        this.soldier.getLookControl().setLookAt(t, 30, 30);

        // feet: close in until he is in range and seen, keep away from blades
        net.minecraft.world.entity.Mob horse = this.soldier.steed();
        if (this.soldier.isCrew()) {
            // in a howdah: he only shoots
        } else if (horse != null) {
            this.circle(horse, t, dist);
        } else if (dist > RANGE || !sees && this.seeTime < -20) {
            if (--this.pathDelay <= 0) {
                this.pathDelay = 10;
                this.soldier.getNavigation().moveTo(t, 1.0);
            }
        } else {
            this.soldier.getNavigation().stop();
            if (--this.strafeTicks <= 0) {
                this.strafeTicks = 20 + this.soldier.getRandom().nextInt(40);
                this.strafe = this.soldier.getRandom().nextInt(3) - 1;
            }
            float forward = dist < 6 ? -0.8f : dist > RANGE * 0.8 ? 0.5f : 0;
            this.soldier.getMoveControl().strafe(forward, this.strafe * 0.4f);
            this.soldier.setYRot(this.soldier.getYHeadRot());
            this.soldier.yBodyRot = this.soldier.getYHeadRot();
        }

        // hands: draw and loose
        if (this.soldier.isUsingItem()) {
            if (!sees && this.seeTime < -40) {
                this.soldier.stopUsingItem();
            } else if (sees && this.soldier.getTicksUsingItem() >= this.drawFor && this.clearShot(t)) {
                this.soldier.stopUsingItem();
                this.soldier.performRangedAttack(t, this.soldier.getMainHandItem().getItem() instanceof CrossbowItem ? 1.0f : BowItem.getPowerForTime(this.drawFor));
            }
        } else if (this.seeTime >= -20 && dist <= RANGE + 4) {
            // the better archer draws faster
            boolean crossbow = this.soldier.getMainHandItem().getItem() instanceof CrossbowItem;
            // the better archer draws faster; a crossbow is spanned at its own pace
            this.drawFor = crossbow ? 34 + this.soldier.getRandom().nextInt(10)
                    : Mth.clamp(Math.round(26 - 10 * this.soldier.skill()), 15, 24) + this.soldier.getRandom().nextInt(6);
            this.soldier.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.soldier, item -> item instanceof BowItem || item instanceof CrossbowItem));
        }
    }

    /**
     * A mounted archer rides round the enemy at a bowshot, shooting as he goes, and never lets him come close.
     */
    private void circle(net.minecraft.world.entity.Mob horse, LivingEntity t, double dist) {
        if (--this.pathDelay > 0 && dist > 6) {
            return;
        }
        this.pathDelay = 12;
        if (this.strafe == 0) {
            this.strafe = this.soldier.getRandom().nextBoolean() ? 1 : -1;
        }
        double radius = RANGE * 0.6;
        double angle = Math.atan2(horse.getZ() - t.getZ(), horse.getX() - t.getX()) + this.strafe * 0.7;
        double r = dist < 8 ? radius + 4 : radius;
        double x = t.getX() + Math.cos(angle) * r;
        double z = t.getZ() + Math.sin(angle) * r;
        if (!this.soldier.getNavigation().moveTo(x, t.getY(), z, dist < 8 ? 1.25 : 0.95) || horse.horizontalCollision && this.soldier.getRandom().nextInt(4) == 0) {
            // something in the way: the other way round
            this.strafe = -this.strafe;
        }
    }

    /** No friend standing right in the line of fire. */
    private boolean clearShot(LivingEntity t) {
        var from = this.soldier.getEyePosition();
        var to = t.getEyePosition();
        var box = this.soldier.getBoundingBox().expandTowards(to.subtract(from)).inflate(1);
        for (LivingEntity e : this.soldier.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this.soldier && e != t && this.soldier.isAlliedTo(e)
                && e != this.soldier.getVehicle() && !this.soldier.isPassengerOfSameVehicle(e))) {
            if (e.getBoundingBox().inflate(0.3).clip(from, to).isPresent() && e.distanceToSqr(this.soldier) < t.distanceToSqr(this.soldier)) {
                return false;
            }
        }
        // nor a boat or ship of his side (its hull would take the arrow)
        for (net.minecraft.world.entity.Entity v : this.soldier.level().getEntities(this.soldier, box, v -> Vessels.isVessel(v)
                && v != this.soldier.getVehicle() && v != t.getVehicle() && v.getPassengers().stream().anyMatch(this.soldier::isAlliedTo))) {
            if (v.getBoundingBox().inflate(0.3).clip(from, to).isPresent() && v.distanceToSqr(this.soldier) < t.distanceToSqr(this.soldier)) {
                return false;
            }
        }
        return true;
    }
}
