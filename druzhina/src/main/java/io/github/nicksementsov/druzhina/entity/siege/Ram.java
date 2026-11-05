package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A battering ram under a roof of wet hides: a log with an iron head, hung on ropes, swung against a gate or a wall in
 * front of it. Arrows do little against the roof, fire nothing. It strikes the blocks before it (the commander's place)
 * and whoever stands in front.
 */
public class Ram extends SiegeEngine {
    public static final int RELOAD = 50;
    /** Ticks of the swing forward, from the moment it is let go to the blow. */
    public static final int SWING = 4;
    private static final float POWER = 0.7f;
    private static final float SIDE_POWER = 0.3f;

    private int strikeIn;
    @Nullable
    private Vec3 strikeAt;

    public Ram(EntityType<? extends Ram> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return engineAttributes(160, 14, 0.16);
    }

    @Override
    public int reloadTicks() {
        return RELOAD;
    }

    @Override
    public double minRange() {
        return 0;
    }

    /** It reaches what is just before its head. */
    @Override
    public double maxRange() {
        return this.getBbWidth() / 2 + 2.4;
    }

    @Override
    protected boolean needsSight() {
        return false;
    }

    @Override
    protected float turnSpeed() {
        return 2.0f;
    }

    @Override
    protected double crewBack() {
        return 0.9;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            // arrows stick in the hides
            amount *= 0.25f;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew) {
        this.strikeAt = at;
        this.strikeIn = SWING;
        this.playSound(SoundEvents.WOODEN_TRAPDOOR_OPEN, 0.9f, 0.5f);
        return true;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.strikeIn > 0 && --this.strikeIn == 0 && this.strikeAt != null) {
            this.strike(this.strikeAt);
            this.strikeAt = null;
        }
    }

    /** The blow: on the blocks before its head, and on whoever stands there. */
    private void strike(Vec3 at) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Soldier crew = this.crew();
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double fx = dx / d;
        double fz = dz / d;
        boolean hit = false;
        // the head, then one block to either side at the height of the log
        for (double h : new double[]{1.4, 0.5}) {
            hit |= this.strikeColumn(level, fx, fz, 0, h, POWER);
        }
        hit |= this.strikeColumn(level, fx, fz, 1, 1.4, SIDE_POWER);
        hit |= this.strikeColumn(level, fx, fz, -1, 1.4, SIDE_POWER);
        Vec3 head = this.position().add(fx * (this.getBbWidth() / 2 + 1.3), 1.4, fz * (this.getBbWidth() / 2 + 1.3));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(head, head).inflate(0.9, 1.0, 0.9),
                e -> e != this && e != crew && !e.isAlliedTo(this) && !this.isAlliedTo(e))) {
            e.hurt(crew != null ? this.damageSources().mobAttack(crew) : this.damageSources().mobAttack(this), 14);
            e.knockback(1.6, -fx, -fz);
            hit = true;
        }
        level.playSound(null, head.x, head.y, head.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.6f, hit ? 0.55f : 0.8f);
        if (hit) {
            level.playSound(null, head.x, head.y, head.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.35f, 0.5f);
            level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 6, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Strikes the first solid block straight ahead ({@code side}: a block to the left or the right). */
    private boolean strikeColumn(ServerLevel level, double fx, double fz, int side, double h, float power) {
        double half = this.getBbWidth() / 2;
        for (double reach = half + 0.6; reach <= half + 2.2; reach += 0.5) {
            double x = this.getX() + fx * reach - fz * side;
            double z = this.getZ() + fz * reach + fx * side;
            BlockPos pos = BlockPos.containing(x, this.getY() + h, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                if (SiegeDamage.allowed(level, this.crew() != null ? this.crew() : this)) {
                    SiegeDamage.hit(level, pos, power, this.crew() != null ? this.crew() : this);
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ for the model

    /** The swing of the log on its ropes, degrees: back (positive) to struck forward (negative). */
    public float swing(float partialTick) {
        if (this.loaded()) {
            return 24;
        }
        float t = this.sinceShot(partialTick);
        if (t < SWING) {
            float k = t / SWING;
            return 24 - 46 * k * k;
        }
        if (t < SWING + 16) {
            // it swings back and forth on the ropes and comes to rest
            float k = (t - SWING) / 16f;
            return -22 * Mth.cos(k * Mth.PI * 1.5f) * (1 - k);
        }
        // and is drawn back for the next blow
        return 24 * Mth.clamp((t - SWING - 16) / (RELOAD * 0.9f - SWING - 16), 0, 1);
    }
}
