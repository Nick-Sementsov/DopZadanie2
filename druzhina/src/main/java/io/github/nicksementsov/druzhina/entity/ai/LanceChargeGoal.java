package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.knightcombat.api.KnightCombatApi;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.network.CombatAction;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A knight's charge. He spurs his horse at the enemy from far enough to come at a gallop, lowers the lance under his
 * arm and keeps it aimed at him; the horse carries the lance into him (the force of the blow is the speed of the
 * horse: see Knightly Combat's couched lance). He rides on past, wheels round at a distance and charges again.
 */
public class LanceChargeGoal extends Goal {
    /** He wheels round this far beyond the enemy. */
    private static final double OVERRUN = 9;
    /** Closer than this he cannot get up speed: he rides off first. */
    private static final double RUN_UP = 7;

    private enum Stage {
        /** Riding off to have room for the charge. */
        AWAY,
        /** At the enemy, lance couched. */
        CHARGE,
        /** Past him, before wheeling round. */
        PAST
    }

    private final Soldier soldier;
    @Nullable
    private LivingEntity target;
    private Stage stage = Stage.CHARGE;
    @Nullable
    private Vec3 goal;
    private int pathDelay;
    private int stageTicks;
    private boolean couched;

    public LanceChargeGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = this.soldier.getTarget();
        return t != null && t.isAlive() && this.soldier.lancer() && this.soldier.steed() != null && this.soldier.canAttack(t)
                && this.soldier.withinReach(t) && !this.soldier.routed();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.soldier.getTarget();
        if (t == null || !t.isAlive() || !this.soldier.lancer() || this.soldier.steed() == null || !this.soldier.canAttack(t) || this.soldier.routed()) {
            return false;
        }
        if (!this.soldier.withinReach(t)) {
            this.soldier.setTarget(null);
            return false;
        }
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.target = this.soldier.getTarget();
        this.pathDelay = 0;
        this.stageTicks = 0;
        this.stage = this.target != null && this.target.distanceTo(this.soldier) < RUN_UP ? Stage.AWAY : Stage.CHARGE;
        this.goal = null;
        this.soldier.setAggressive(true);
    }

    @Override
    public void stop() {
        this.raise();
        this.soldier.setAggressive(false);
        this.soldier.getNavigation().stop();
        this.target = null;
    }

    @Override
    public void tick() {
        LivingEntity t = this.soldier.getTarget();
        Mob horse = this.soldier.steed();
        if (t == null || horse == null) {
            return;
        }
        if (t != this.target) {
            this.target = t;
            this.stage = t.distanceTo(this.soldier) < RUN_UP ? Stage.AWAY : Stage.CHARGE;
            this.stageTicks = 0;
        }
        this.stageTicks++;
        double dist = Math.sqrt(horse.distanceToSqr(t.getX(), horse.getY(), t.getZ()));
        switch (this.stage) {
            case AWAY -> {
                this.raise();
                this.look(t, false);
                if (this.goal == null || --this.pathDelay <= 0) {
                    // straight away from him, a little to the side, to come back at him
                    Vec3 from = new Vec3(horse.getX() - t.getX(), 0, horse.getZ() - t.getZ());
                    if (from.lengthSqr() < 1.0E-4) {
                        from = new Vec3(-Mth.sin(horse.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(horse.getYRot() * Mth.DEG_TO_RAD));
                    }
                    Vec3 dir = from.normalize().yRot(0.35f * (this.soldier.getId() % 2 == 0 ? 1 : -1));
                    this.goal = t.position().add(dir.scale(OVERRUN + 4));
                    this.ride(this.goal, 1.2);
                }
                if (dist > OVERRUN || this.stageTicks > 80) {
                    this.next(Stage.CHARGE);
                }
            }
            case CHARGE -> {
                // aimed at where he will be when the horse gets there
                double lead = Math.min(1.2, dist / 12);
                Vec3 at = t.position().add(t.getDeltaMovement().scale(lead * 10));
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 5;
                    Vec3 from = new Vec3(at.x - horse.getX(), 0, at.z - horse.getZ());
                    // aim a little beyond him, so that the horse does not stop short
                    Vec3 beyond = from.lengthSqr() < 1.0E-4 ? at : at.add(from.normalize().scale(3));
                    this.ride(beyond, 1.3);
                }
                this.look(t, true);
                if (dist < 14) {
                    this.couch();
                }
                // past him (he is behind the horse) or the lance has struck: ride on
                Vec3 fwd = new Vec3(-Mth.sin(horse.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(horse.getYRot() * Mth.DEG_TO_RAD));
                Vec3 to = new Vec3(t.getX() - horse.getX(), 0, t.getZ() - horse.getZ());
                boolean behind = to.dot(fwd) < -0.5 && dist < 4;
                CombatState me = KnightCombatApi.state(this.soldier);
                boolean struck = me.phase() == Phase.RECOVERY && (me.flags() & CombatState.LANCE) != 0;
                if (behind || struck || dist < 1.2 || this.stageTicks > 200) {
                    Vec3 on = fwd.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : fwd;
                    this.goal = horse.position().add(on.scale(OVERRUN));
                    this.next(Stage.PAST);
                }
            }
            case PAST -> {
                this.raise();
                if (this.goal != null && --this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    this.ride(this.goal, 1.2);
                }
                this.look(t, false);
                double gx = this.goal == null ? 0 : this.goal.x - horse.getX();
                double gz = this.goal == null ? 0 : this.goal.z - horse.getZ();
                if (gx * gx + gz * gz < 3 * 3 || dist > OVERRUN || this.stageTicks > 60) {
                    this.next(dist < RUN_UP ? Stage.AWAY : Stage.CHARGE);
                }
            }
        }
    }

    private void next(Stage stage) {
        this.stage = stage;
        this.stageTicks = 0;
        this.pathDelay = 0;
        this.goal = null;
    }

    private void ride(Vec3 to, double speed) {
        this.soldier.getNavigation().moveTo(to.x, to.y, to.z, speed);
    }

    /** Head (and so the lance) at him; with the lance couched, a little down to his chest. */
    private void look(LivingEntity t, boolean aim) {
        double dx = t.getX() - this.soldier.getX();
        double dz = t.getZ() - this.soldier.getZ();
        double dy = t.getY(0.6) - this.soldier.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        if (aim) {
            // the lance follows the horse: he turns his head (and the lance) only so far from where the horse runs
            Mob horse = this.soldier.steed();
            if (horse != null) {
                yaw = horse.getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - horse.getYRot()), -35, 35);
            }
        }
        this.soldier.setYRot(yaw);
        this.soldier.setYHeadRot(yaw);
        this.soldier.setXRot(Mth.clamp(pitch, -30, 40));
        this.soldier.getLookControl().setLookAt(t.getX(), t.getY(0.6), t.getZ(), 360, 360);
    }

    private void couch() {
        if (!this.couched) {
            // held: the lance goes down as soon as his hands are free
            KnightCombatApi.input(this.soldier, CombatAction.COUCH_START);
            this.couched = true;
        }
    }

    private void raise() {
        if (this.couched) {
            KnightCombatApi.input(this.soldier, CombatAction.COUCH_STOP);
            this.couched = false;
        }
    }
}
