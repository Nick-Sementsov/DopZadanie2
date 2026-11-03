package io.github.nicksementsov.knightcombat.entity;

import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.CombatManager;
import io.github.nicksementsov.knightcombat.combat.WeaponKind;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.network.Feedback;
import io.github.nicksementsov.knightcombat.network.Impact;
import io.github.nicksementsov.knightcombat.registry.ModEntities;
import io.github.nicksementsov.knightcombat.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/**
 * A weapon thrown by a fighter. Spears and thrusting swords fly point first, everything else spins end over end.
 * It hurts what it hits (a raised weapon or shield blocks it like any projectile) and drops; it sticks into what it
 * hits fast, lies where it fell slowly, and blunt weapons bounce off walls. Anyone can pick it up by walking over it,
 * straight into an empty hand. A weapon nobody picks up turns into a normal item after five minutes.
 */
public class ThrownWeapon extends AbstractArrow {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, KnightCombat.id("thrown_weapon"));
    /** How the weapon rests: still flying, stuck into a block, or lying flat. */
    public static final byte FLYING = 0;
    public static final byte STUCK = 1;
    public static final byte LYING = 2;

    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.ITEM_STACK);
    /** Degrees the weapon turns per tick in flight; 0 for weapons that fly point first. */
    private static final EntityDataAccessor<Byte> SPIN = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> REST = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BYTE);
    /** Maces and hammers bounce off walls instead of sticking (clients simulate the flight too, so they need to know). */
    private static final EntityDataAccessor<Boolean> BLUNT = SynchedEntityData.defineId(ThrownWeapon.class, EntityDataSerializers.BOOLEAN);
    /** Below this speed (blocks per tick) a weapon does not stick into what it meets. */
    private static final double STICK_SPEED = 0.45;
    /** Ticks a weapon lies on the ground before it becomes a normal item. */
    private static final int ITEM_AFTER = 6000;

    private float damage = 4;
    private float weight = 0.4f;
    private float launchSpeed = 2;
    private boolean dealtDamage;
    private int bounces;
    private int restTicks;

    public ThrownWeapon(EntityType<? extends ThrownWeapon> type, Level level) {
        super(type, level);
    }

    public ThrownWeapon(Level level, LivingEntity owner, ItemStack stack, WeaponStats weapon, float damage) {
        super(ModEntities.THROWN_WEAPON.get(), owner, level);
        entityData.set(ITEM, stack);
        entityData.set(SPIN, (byte) spin(weapon));
        this.damage = damage;
        this.weight = weapon.weight();
        entityData.set(BLUNT, weapon.kind() == WeaponKind.BLUNT);
    }

    /** Damage of a throw compared to the weapon's attack damage: daggers, spears and axes are made to be thrown. */
    public static float damageFactor(WeaponStats weapon) {
        return switch (weapon.kind()) {
            case DAGGER -> 1.5f;
            case SPEAR -> 1.4f;
            case AXE -> 1.25f;
            case BLUNT -> 1.1f;
            case SWORD, THRUSTING_SWORD -> 1.0f;
            case POLEAXE -> 0.9f;
        };
    }

    /** Weapons made to be thrown (javelins, throwing axes) strike this much harder again. */
    public static final float THROWING_WEAPON_DAMAGE = 1.3f;

    /** Speed of a throw in blocks per tick: light weapons fly faster, and those made to be thrown faster still. */
    public static float speed(WeaponStats weapon, boolean madeForThrowing) {
        return 2.4f - 0.9f * weapon.weight() + (weapon.kind() == WeaponKind.SPEAR ? 0.3f : 0) + (madeForThrowing ? 0.4f : 0);
    }

    private static int spin(WeaponStats weapon) {
        return switch (weapon.kind()) {
            case SPEAR, THRUSTING_SWORD -> 0;
            case DAGGER -> 50;
            default -> weapon.twoHanded() ? 24 : 36;
        };
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ITEM, ItemStack.EMPTY);
        entityData.define(SPIN, (byte) 0);
        entityData.define(REST, FLYING);
        entityData.define(BLUNT, false);
    }

    public ItemStack getWeapon() {
        return entityData.get(ITEM);
    }

    public int getSpin() {
        return entityData.get(SPIN);
    }

    public byte getRest() {
        return entityData.get(REST);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, velocity, inaccuracy);
        launchSpeed = Math.max(0.1f, velocity);
    }

    @Override
    public void tick() {
        if (inGroundTime > 4) {
            dealtDamage = true;
        }
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!inGround && getRest() != FLYING) {
            // The block it was in is gone: it falls again.
            entityData.set(REST, FLYING);
        }
        if (!inGround && !dealtDamage && getSpin() > 0 && tickCount % 4 == 1) {
            playSound(ModSounds.SPIN.get(), 0.5f, 0.9f + getSpin() / 100f + random.nextFloat() * 0.1f);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        ItemStack stack = getWeapon();
        Vec3 motion = getDeltaMovement();
        LivingEntity living = target instanceof LivingEntity le ? le
                : target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
        // A weapon that has slowed down (falling, or thrown far) hits softer.
        float amount = damage * Mth.clamp((float) motion.length() / launchSpeed, 0.35f, 1);
        if (living != null) {
            amount += EnchantmentHelper.getDamageBonus(stack, living.getMobType());
        }
        dealtDamage = true;
        int fire = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, stack);
        if (CombatManager.thrownHit(target, damageSource(owner), amount)) {
            if (target.getType() == EntityType.ENDERMAN) {
                return;
            }
            if (living != null) {
                double horizontal = motion.horizontalDistance();
                if (horizontal > 1.0E-4) {
                    living.knockback(0.15f + 0.35f * weight, -motion.x / horizontal, -motion.z / horizontal);
                }
                if (fire > 0) {
                    living.setSecondsOnFire(4 * fire);
                }
                if (owner instanceof LivingEntity thrower) {
                    EnchantmentHelper.doPostHurtEffects(living, thrower);
                    EnchantmentHelper.doPostDamageEffects(thrower, living);
                    thrower.setLastHurtMob(living);
                }
            }
            ServerLevel level = (ServerLevel) level();
            Vec3 point = position();
            boolean killed = living != null && living.isDeadOrDying();
            level.playSound(null, point.x, point.y, point.z, weight >= 0.6f || killed ? ModSounds.HIT_HEAVY.get() : ModSounds.HIT.get(),
                    SoundSource.PLAYERS, 1.0f, 0.95f + random.nextFloat() * 0.15f);
            CombatManager.hitSounds(level, living, point, killed, random.nextFloat());
            if (owner instanceof ServerPlayer thrower) {
                CombatManager.feedback(thrower, Feedback.HIT);
            }
            if (!(target instanceof Player)) {
                // Players hit by it feel it through the usual damage events.
                CombatManager.impact(target, killed ? Impact.KILL : Impact.HIT, this, target, point, weight);
            }
            if (stack.hurt(1, random, owner instanceof ServerPlayer thrower ? thrower : null)) {
                playSound(SoundEvents.ITEM_BREAK, 0.8f, 0.8f + random.nextFloat() * 0.4f);
                discard();
                return;
            }
        }
        // Whether it hurt or was blocked, it glances off and drops at the feet of what it hit.
        setDeltaMovement(motion.multiply(-0.04, -0.1, -0.04).add(0, 0.08, 0));
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        double speed = getDeltaMovement().length();
        boolean fast = speed > STICK_SPEED && !dealtDamage;
        if (entityData.get(BLUNT) && fast && bounces < 2) {
            // Maces and hammers do not stick: they ring off the wall.
            bounce(hit, 0.35);
            return;
        }
        if (!fast && hit.getDirection() != Direction.UP && bounces < 4) {
            // Too slow to stick into a wall: it drops off.
            bounce(hit, 0.2);
            return;
        }
        if (!level().isClientSide) {
            // Clients run the same flight, but how the weapon rests is the server's to say.
            entityData.set(REST, fast ? STUCK : LYING);
        }
        setSoundEvent(fast ? ModSounds.STICK.get() : ModSounds.CLATTER.get());
        super.onHitBlock(hit);
        if (!fast) {
            shakeTime = 0;
        }
    }

    private void bounce(BlockHitResult hit, double keep) {
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 motion = getDeltaMovement();
        Vec3 reflected = motion.subtract(normal.scale(2 * motion.dot(normal))).scale(keep);
        Vec3 at = hit.getLocation().add(normal.scale(0.05));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(reflected);
        bounces++;
        dealtDamage = true;
        playSound(ModSounds.CLATTER.get(), Math.min(1, 0.4f + (float) motion.length() * 0.4f), 0.9f + random.nextFloat() * 0.2f);
    }

    private DamageSource damageSource(Entity owner) {
        Entity cause = owner != null ? owner : this;
        return level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DAMAGE_TYPE)
                .<DamageSource>map(type -> new DamageSource(type, this, cause))
                .orElseGet(() -> damageSources().trident(this, cause));
    }

    @Override
    protected EntityHitResult findHitEntity(Vec3 from, Vec3 to) {
        return dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected ItemStack getPickupItem() {
        return getWeapon().copy();
    }

    /** Picked up straight into an empty hand, ready to fight. */
    @Override
    protected boolean tryPickup(Player player) {
        if (pickup == Pickup.ALLOWED && player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, getPickupItem());
            return true;
        }
        return super.tryPickup(player);
    }

    @Override
    protected void tickDespawn() {
        if (pickup != Pickup.ALLOWED) {
            super.tickDespawn();
        } else if (++restTicks >= ITEM_AFTER) {
            spawnAtLocation(getPickupItem(), 0.1f);
            discard();
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return ModSounds.STICK.get();
    }

    @Override
    protected float getWaterInertia() {
        return 0.8f;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Weapon", getWeapon().save(new CompoundTag()));
        tag.putByte("Spin", (byte) getSpin());
        tag.putByte("Rest", getRest());
        tag.putFloat("Damage", damage);
        tag.putFloat("Weight", weight);
        tag.putFloat("LaunchSpeed", launchSpeed);
        tag.putBoolean("Blunt", entityData.get(BLUNT));
        tag.putBoolean("DealtDamage", dealtDamage);
        tag.putInt("RestTicks", restTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, ItemStack.of(tag.getCompound("Weapon")));
        entityData.set(SPIN, tag.getByte("Spin"));
        entityData.set(REST, tag.getByte("Rest"));
        damage = tag.getFloat("Damage");
        weight = tag.getFloat("Weight");
        launchSpeed = Math.max(0.1f, tag.getFloat("LaunchSpeed"));
        entityData.set(BLUNT, tag.getBoolean("Blunt"));
        dealtDamage = tag.getBoolean("DealtDamage");
        restTicks = tag.getInt("RestTicks");
    }
}
