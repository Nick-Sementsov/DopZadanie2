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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A stone hurled by a trebuchet: it flies in a high arc (no air slows it), and where it falls it crushes men and
 * cracks walls around (see {@link SiegeDamage}).
 */
public class SiegeStone extends Projectile {
    public static final double GRAVITY = 0.05;
    private static final double RADIUS = 2.6;

    public SiegeStone(EntityType<? extends SiegeStone> type, Level level) {
        super(type, level);
    }

    public SiegeStone(Level level, LivingEntity thrower) {
        this(DruzhinaRegistry.SIEGE_STONE.get(), level);
        this.setOwner(thrower);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !this.level().isClientSide) {
            this.onHit(hit);
            return;
        }
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.setDeltaMovement(v.x, v.y - GRAVITY, v.z);
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        if (this.getY() < this.level().getMinBuildHeight() - 16 || this.tickCount > 600) {
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        this.impact(hit.getLocation(), null);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        this.impact(hit.getLocation(), hit.getBlockPos());
    }

    private void impact(Vec3 at, @Nullable BlockPos block) {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
            return;
        }
        Entity owner = this.getOwner();
        // men crushed or knocked down around
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().move(at.subtract(this.position())).inflate(RADIUS))) {
            double d = e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(at);
            if (d > RADIUS || owner != null && (e == owner || e.isAlliedTo(owner) || owner.isAlliedTo(e) || e == owner.getVehicle())) {
                continue;
            }
            float damage = (float) (6 + 18 * (1 - d / RADIUS));
            e.hurt(this.damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
            Vec3 push = e.position().subtract(at).normalize().scale(0.8 * (1 - d / RADIUS));
            e.push(push.x, 0.35, push.z);
        }
        // and the wall cracks
        SiegeDamage.blast(level, at, 1.7, 1.3f, owner != null ? owner : this);
        BlockState shown = block != null ? level.getBlockState(block) : Blocks.STONE.defaultBlockState();
        if (shown.isAir()) {
            shown = Blocks.COBBLESTONE.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), at.x, at.y, at.z, 40, 0.8, 0.6, 0.8, 0.2);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.3);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 8, 0.6, 0.3, 0.6, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.4f, 0.65f + this.random.nextFloat() * 0.1f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 2.0f, 0.5f);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
