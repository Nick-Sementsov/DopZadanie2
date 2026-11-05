package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;

/**
 * A clay pot of burning pitch thrown by a mangonel: it bursts where it falls, setting fire to the men about and, where
 * the engines may break things, to the ground and what burns on it.
 */
public class FirePot extends Hurled {
    private static final double RADIUS = 2.4;

    public FirePot(EntityType<? extends FirePot> type, Level level) {
        super(type, level);
    }

    public FirePot(Level level, LivingEntity thrower) {
        this(DruzhinaRegistry.FIRE_POT.get(), level);
        this.setOwner(thrower);
    }

    @Override
    protected void trail() {
        this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.25, this.getZ(), 0, 0.01, 0);
        this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 0, 0.02, 0);
    }

    @Override
    protected void impact(Vec3 at, @Nullable BlockPos block) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = this.getOwner();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(RADIUS))) {
            double d = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
            if (d > RADIUS || this.spared(e)) {
                continue;
            }
            e.hurt(this.damageSources().onFire(), (float) (3 + 5 * (1 - d / RADIUS)));
            e.setSecondsOnFire(7);
        }
        if (SiegeDamage.allowed(level, owner != null ? owner : this)) {
            BlockPos c = BlockPos.containing(at);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 2, 2))) {
                if (p.distToCenterSqr(at) <= RADIUS * RADIUS && this.random.nextFloat() < 0.45f && level.isEmptyBlock(p)
                        && BaseFireBlock.canBePlacedAt(level, p, Direction.UP)) {
                    level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                }
            }
            SiegeDamage.blast(level, at, 1.2, 0.35f, owner != null ? owner : this);
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 50, 1.0, 0.5, 1.0, 0.06);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 12, 0.6, 0.3, 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 14, 0.8, 0.4, 0.8, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DECORATED_POT_SHATTER, SoundSource.NEUTRAL, 1.6f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.6f, 0.7f);
    }
}
