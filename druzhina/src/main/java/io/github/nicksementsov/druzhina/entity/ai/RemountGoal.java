package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/**
 * A rider who finds himself on foot (thrown from the saddle, or he got down) goes back to his horse or his elephant and
 * mounts it again, unless he has an enemy at his throat and a blade to meet him with.
 */
public class RemountGoal extends Goal {
    /** How far he goes to find his mount. */
    private static final double SEEK = 32;

    private final Soldier soldier;
    @Nullable
    private Mob beast;
    private int pathDelay;
    private int ticks;
    /** Goals are tried every other tick: he looks for his mount every few of those. */
    private int checkDelay;

    public RemountGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--this.checkDelay > 0) {
            return false;
        }
        this.checkDelay = 5;
        if (this.soldier.isPassenger() || this.soldier.mountId() == null || this.soldier.routed() || this.soldier.boarding()) {
            return false;
        }
        Mob m = this.soldier.mountEntity();
        if (m == null || m.distanceToSqr(this.soldier) > SEEK * SEEK || !this.free(m)) {
            return false;
        }
        LivingEntity t = this.soldier.getTarget();
        if (t != null && t.distanceToSqr(this.soldier) < 7 * 7 && this.soldier.hasMeleeWeapon()) {
            // first the one at his throat
            return false;
        }
        this.beast = m;
        return true;
    }

    /** Whether there is a seat for him on {@code m}. */
    private boolean free(Mob m) {
        if (m instanceof WarElephant e) {
            return e.hasSeatFor(this.soldier);
        }
        return m instanceof AbstractHorse && m.getPassengers().isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return this.beast != null && this.beast.isAlive() && !this.soldier.isPassenger() && this.ticks < 400 && this.free(this.beast)
                && !this.soldier.routed();
    }

    @Override
    public void start() {
        this.pathDelay = 0;
        this.ticks = 0;
        if (this.beast != null) {
            // the beast waits for him
            this.beast.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        this.beast = null;
        this.soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.beast == null) {
            return;
        }
        this.ticks++;
        this.soldier.getLookControl().setLookAt(this.beast, 30, 30);
        double reach = this.beast.getBbWidth() / 2 + 1.6;
        double dx = this.beast.getX() - this.soldier.getX();
        double dz = this.beast.getZ() - this.soldier.getZ();
        if (dx * dx + dz * dz < reach * reach) {
            this.soldier.getNavigation().stop();
            if (this.soldier.startRiding(this.beast)) {
                this.soldier.playSound(net.minecraft.sounds.SoundEvents.HORSE_SADDLE, 0.5f, 1.0f);
            }
            return;
        }
        if (--this.pathDelay <= 0) {
            this.pathDelay = 10;
            this.soldier.getNavigation().moveTo(this.beast, 1.1);
        }
    }
}
