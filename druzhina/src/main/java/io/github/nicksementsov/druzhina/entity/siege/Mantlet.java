package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A mobile wall (a gulyay-gorod's shield): thick boards with loopholes on two wheels, pushed along by one man. It
 * shoots nothing: it stops the arrows and bolts flying at the men behind it, who shoot through its slits.
 */
public class Mantlet extends SiegeEngine {
    public static final float SCALE = 1.25f;

    public Mantlet(EntityType<? extends Mantlet> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(110, 8, 0.22);
    }

    @Override
    public int reloadTicks() {
        return 20;
    }

    @Override
    public double minRange() {
        return 0;
    }

    @Override
    public double maxRange() {
        return 0;
    }

    @Override
    protected boolean needsSight() {
        return false;
    }

    @Override
    public boolean batters() {
        return false;
    }

    @Override
    protected double crewBack() {
        return 1.6;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            // the arrows stick in the boards
            amount *= 0.4f;
        }
        return super.hurt(source, amount);
    }

    @Nullable
    @Override
    protected Vec3 aim() {
        return null;
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        return false;
    }
}
