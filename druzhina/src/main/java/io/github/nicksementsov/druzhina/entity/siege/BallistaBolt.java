package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.registry.DruzhinaRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The bolt of a ballista: a great iron-headed arrow that goes through two or three men in a row. */
public class BallistaBolt extends AbstractArrow {
    public BallistaBolt(EntityType<? extends BallistaBolt> type, Level level) {
        super(type, level);
    }

    public BallistaBolt(Level level, LivingEntity shooter) {
        super(DruzhinaRegistry.BALLISTA_BOLT.get(), shooter, level);
        this.setBaseDamage(4.5);
        this.setPierceLevel((byte) 3);
        this.pickup = Pickup.DISALLOWED;
    }

    /** A bolt chips at what it strikes: a door or a palisade gives way after a good many. */
    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
        super.onHitBlock(hit);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.world.entity.Entity owner = this.getOwner();
            if (SiegeDamage.allowed(level, owner != null ? owner : this)) {
                SiegeDamage.hit(level, hit.getBlockPos(), 0.2f, owner != null ? owner : this);
            }
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }
}
