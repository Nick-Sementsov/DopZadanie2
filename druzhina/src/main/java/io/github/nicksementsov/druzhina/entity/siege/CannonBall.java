package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A bombard's ball of stone or iron: fast and nearly flat, it smashes a wall where it strikes and the men by it. */
public class CannonBall extends Hurled {
    private static final double RADIUS = 2.2;

    public CannonBall(EntityType<? extends CannonBall> type, Level level) {
        super(type, level);
    }

    public CannonBall(Level level, LivingEntity gunner) {
        this(DruzhinaRegistry.CANNON_BALL.get(), level);
        this.setOwner(gunner);
    }

    @Override
    protected void trail() {
        this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0.01, 0);
        if (this.tickCount < 6) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0.02, 0);
        }
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
            e.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), (float) (10 + 22 * (1 - d / RADIUS)));
            Vec3 push = e.position().subtract(at).normalize().scale(1.1 * (1 - d / RADIUS));
            e.push(push.x, 0.45, push.z);
        }
        SiegeDamage.blast(level, at, 2.0, 2.4f, owner != null ? owner : this);
        BlockState shown = block != null ? level.getBlockState(block) : Blocks.STONE.defaultBlockState();
        if (shown.isAir()) {
            shown = Blocks.COBBLESTONE.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), at.x, at.y, at.z, 60, 1.0, 0.8, 1.0, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.6, 0.4, 0.6, 0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 16, 0.8, 0.5, 0.8, 0.03);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.5f, 0.75f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 2.0f, 0.45f);
    }
}
