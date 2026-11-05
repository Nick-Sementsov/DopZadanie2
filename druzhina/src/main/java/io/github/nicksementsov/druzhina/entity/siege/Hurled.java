package io.github.nicksementsov.druzhina.entity.siege;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Something an engine hurls (a stone, an iron ball, a pot of fire): it flies with no air to slow it, passes through
 * its own side, and does its work where it strikes.
 */
public abstract class Hurled extends Projectile {
    public static final double GRAVITY = 0.05;

    protected Hurled(EntityType<? extends Hurled> type, Level level) {
        super(type, level);
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
            this.trail();
        }
        if (this.getY() < this.level().getMinBuildHeight() - 16 || this.tickCount > 600) {
            this.discard();
        }
    }

    /** What it leaves in the air behind it (on the client). */
    protected abstract void trail();

    /** What it does where it strikes, on the server; it is gone after. */
    protected abstract void impact(Vec3 at, @Nullable BlockPos block);

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = this.getOwner();
        if (owner != null && (e == owner.getVehicle() || e.isAlliedTo(owner) || owner.isAlliedTo(e))) {
            return false;
        }
        return super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        this.strike(hit.getLocation(), null);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        this.strike(hit.getLocation(), hit.getBlockPos());
    }

    private void strike(Vec3 at, @Nullable BlockPos block) {
        if (this.level().isClientSide || this.isRemoved()) {
            return;
        }
        this.impact(at, block);
        this.discard();
    }

    /** Whether {@code e} is spared: the thrower and his side. */
    protected boolean spared(LivingEntity e) {
        Entity owner = this.getOwner();
        return owner != null && (e == owner || e == owner.getVehicle() || e.isAlliedTo(owner) || owner.isAlliedTo(e));
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
