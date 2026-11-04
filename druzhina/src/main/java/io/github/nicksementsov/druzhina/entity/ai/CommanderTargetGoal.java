package io.github.nicksementsov.druzhina.entity.ai;

import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/** Whoever strikes the commander, and whoever he strikes, is the enemy of his soldiers. */
public class CommanderTargetGoal extends TargetGoal {
    private final Soldier soldier;
    @Nullable
    private LivingEntity enemy;
    private int seenHurtBy;
    private int seenHurt;

    public CommanderTargetGoal(Soldier soldier) {
        super(soldier, false);
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        Player boss = this.soldier.commander();
        if (boss == null || boss.level() != this.soldier.level()) {
            return false;
        }
        LivingEntity hurtBy = boss.getLastHurtByMob();
        if (hurtBy != null && boss.getLastHurtByMobTimestamp() != this.seenHurtBy && this.fit(hurtBy)) {
            this.seenHurtBy = boss.getLastHurtByMobTimestamp();
            this.enemy = hurtBy;
            return true;
        }
        LivingEntity hurt = boss.getLastHurtMob();
        if (hurt != null && boss.getLastHurtMobTimestamp() != this.seenHurt && this.fit(hurt)) {
            this.seenHurt = boss.getLastHurtMobTimestamp();
            this.enemy = hurt;
            return true;
        }
        return false;
    }

    private boolean fit(LivingEntity e) {
        return e != this.soldier && this.soldier.canAttack(e) && this.soldier.withinReach(e)
                && this.canAttack(e, TargetingConditions.DEFAULT.ignoreLineOfSight());
    }

    @Override
    public void start() {
        this.mob.setTarget(this.enemy);
        super.start();
    }
}
