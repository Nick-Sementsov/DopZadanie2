package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.Orders;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * When his commander sits in a boat or aboard a ship (one of Small Ships), a soldier who follows him goes down to it
 * and climbs in, if there is a seat for him: a rider leaves his horse on the shore. Without a seat he waits on the
 * shore. Elephants do not go to sea.
 */
public class BoardGoal extends Goal {
    /** How far from the vessel he still goes to it. */
    private static final double REACH = 24;
    private static final int GIVE_UP = 300;
    private static final String TOLD = "druzhina.no_seats";

    private final Soldier soldier;
    @Nullable
    private Entity vessel;
    private int pathDelay;
    private int ticks;
    private long retryAt;
    private int checkDelay;
    /** Where he stood on dry land before he went down to the water. */
    @Nullable
    private Vec3 shore;

    public BoardGoal(Soldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // looked at now and then (goals are tried every other tick, so a counter, not the tick number)
        if (--this.checkDelay > 0 || this.soldier.level().getGameTime() < this.retryAt) {
            return false;
        }
        this.checkDelay = 5;
        Entity v = this.wanted();
        if (v == null) {
            return false;
        }
        int free = Vessels.freeSeats(v);
        if (free == 0) {
            return false;
        }
        if (free > 0) {
            // as many go down to it as there are seats
            int going = this.soldier.level().getEntitiesOfClass(Soldier.class, v.getBoundingBox().inflate(REACH + 8),
                    o -> o != this.soldier && o.boardingTo() == v).size();
            if (going >= free) {
                return false;
            }
        }
        this.vessel = v;
        return true;
    }

    /** The vessel he was sent aboard, or the one his commander sits in if he is to follow him into it. */
    @Nullable
    private Entity wanted() {
        Soldier s = this.soldier;
        if (s.isBandit() || s.getOwnerUUID() == null || s.routed() || s.isCrew() || s.steed() instanceof WarElephant) {
            return null;
        }
        Entity ordered = s.shipOrdered();
        if (ordered != null) {
            return ordered.level() == s.level() && ordered.distanceToSqr(s) < 64 * 64 ? ordered : null;
        }
        if (s.orders() != Orders.FOLLOW && s.orders() != Orders.CHARGE || s.getTarget() != null) {
            return null;
        }
        Player boss = s.commander();
        Entity v = Vessels.vesselOf(boss);
        if (v == null || v.level() != s.level() || v.distanceToSqr(s) > REACH * REACH) {
            return null;
        }
        return v;
    }

    @Override
    public boolean canContinueToUse() {
        return this.vessel != null && this.vessel.isAlive() && this.wanted() == this.vessel && this.ticks < GIVE_UP;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.pathDelay = 0;
        this.soldier.setBoardingTo(this.vessel);
        Entity body = this.soldier.getVehicle() != null ? this.soldier.getVehicle() : this.soldier;
        this.shore = body.isInWater() ? null : body.position();
    }

    @Override
    public void stop() {
        if (this.ticks >= GIVE_UP) {
            this.retryAt = this.soldier.level().getGameTime() + 100;
        }
        this.vessel = null;
        this.soldier.setBoardingTo(null);
        this.soldier.getNavigation().stop();
        Entity body = this.soldier.getVehicle() != null ? this.soldier.getVehicle() : this.soldier;
        if (Vessels.vesselOf(this.soldier) == null && body.isInWater() && this.shore != null) {
            // left behind in the water: back to the shore he came from
            this.soldier.getNavigation().moveTo(this.shore.x, this.shore.y, this.shore.z, 1.0);
        }
        this.shore = null;
    }

    @Override
    public void tick() {
        Entity v = this.vessel;
        if (v == null) {
            return;
        }
        this.ticks++;
        this.soldier.getLookControl().setLookAt(v, 30, 30);
        // a boat pushes away whoever stands against it: he climbs in from a few steps off
        if (Vessels.within(v, this.soldier, 4.0)) {
            this.climbIn(v);
            return;
        }
        if (--this.pathDelay <= 0) {
            this.pathDelay = 10;
            this.soldier.getNavigation().moveTo(v.getX(), v.getY(), v.getZ(), 1.15);
        }
    }

    private void climbIn(Entity v) {
        this.soldier.getNavigation().stop();
        boolean room = Vessels.freeSeats(v) != 0;
        Entity horse = this.soldier.getVehicle();
        if (room && horse != null) {
            // off the horse: it waits on the shore
            this.soldier.stopRiding();
            this.soldier.readyToBoard();
        }
        Vessels.allowSoldiersAtHelm();
        if (room && this.soldier.startRiding(v)) {
            if (this.soldier.shipOrdered() == v) {
                // sent aboard: he stays there (the first at the helm)
                this.soldier.shipBoarded();
                this.soldier.order(Orders.HOLD, null, this.soldier.getYRot());
                if (Vessels.isHelmsman(v, this.soldier)) {
                    Player boss = this.soldier.commander();
                    if (boss != null) {
                        boss.displayClientMessage(Component.translatable("druzhina.msg.captain", this.soldier.plainName()).withStyle(ChatFormatting.GOLD), true);
                    }
                }
            }
            Vessels.settle(this.soldier, v);
            this.soldier.playSound(net.minecraft.sounds.SoundEvents.WOOD_STEP, 0.8f, 0.9f);
        } else {
            if (horse != null && !this.soldier.isPassenger() && horse.isAlive()) {
                // back into the saddle, if he got down for nothing
                this.soldier.readyToBoard();
                this.soldier.startRiding(horse);
            }
            // no room for him: he waits on the shore, and the commander hears of it (once a while)
            this.retryAt = this.soldier.level().getGameTime() + 200;
            Player boss = this.soldier.commander();
            if (boss != null && boss.level().getGameTime() - boss.getPersistentData().getLong(TOLD) > 200) {
                boss.getPersistentData().putLong(TOLD, boss.level().getGameTime());
                boss.displayClientMessage(Component.translatable("druzhina.msg.no_seats").withStyle(ChatFormatting.YELLOW), true);
            }
        }
        this.vessel = null;
    }
}
