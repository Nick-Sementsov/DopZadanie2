package io.github.nicksementsov.druzhina.entity.siege;

import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A siege engine: a machine of wood, rope and iron that its crewman, riding on it, drives where he is told and works
 * against the enemy. It shoots (or strikes) at its crewman's foe, at the nearest foe in its reach, or, when the
 * commander has named a place, at that place again and again till told otherwise. Planks mend it.
 */
public abstract class SiegeEngine extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(SiegeEngine.class, EntityDataSerializers.OPTIONAL_UUID);
    /** The place the commander told it to batter, if any. */
    private static final EntityDataAccessor<Optional<BlockPos>> POINT = SynchedEntityData.defineId(SiegeEngine.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    /** The entity event of a shot: the client starts its count of the reload (the models show the shot and the winding back by it). */
    private static final byte EVENT_SHOT = 60;

    @Nullable
    private LivingEntity foe;
    private int lookDelay;
    /** Ticks till it is ready again, counted down on both sides. */
    private int reload;
    /** It has something to aim at: it turns itself (its crewman does not turn it to face the front). */
    private boolean aiming;

    protected SiegeEngine(EntityType<? extends SiegeEngine> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0f);
        this.setPersistenceRequired();
    }

    protected static AttributeSupplier.Builder engineAttributes(double health, double armour, double speed) {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, health)
            .add(Attributes.ARMOR, armour)
            .add(Attributes.ARMOR_TOUGHNESS, 2.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, speed)
            .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(POINT, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    // ------------------------------------------------------------------ what it is

    /** Ticks between two shots (or strikes). */
    public abstract int reloadTicks();

    /** The nearest and the farthest it reaches, in blocks. */
    public abstract double minRange();

    public abstract double maxRange();

    /** Whether it shoots only at what it sees (the ballista) or over walls (the trebuchet). */
    protected abstract boolean needsSight();

    /** Shoots at {@code at} (a place, or the middle of a foe). Returns whether it shot. */
    protected abstract boolean fire(Vec3 at, @Nullable LivingEntity target, Soldier crew);

    /** Raises or lowers what it aims with toward {@code at} (the ballista's head). */
    protected void aimAt(Vec3 at) {
    }

    /** How fast it turns to aim, degrees a tick. */
    protected float turnSpeed() {
        return 3.0f;
    }

    // ------------------------------------------------------------------ whose it is

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        this.entityData.set(OWNER, Optional.ofNullable(owner));
    }

    /** The man who works it: its rider. */
    @Nullable
    public Soldier crew() {
        return this.getFirstPassenger() instanceof Soldier s && s.isAlive() ? s : null;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == this || this.hasPassenger(other)) {
            return true;
        }
        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            if (owner.equals(other.getUUID())) {
                return true;
            }
            if (other instanceof Soldier s && owner.equals(s.getOwnerUUID())) {
                return true;
            }
        }
        return super.isAlliedTo(other);
    }

    public int reload() {
        return this.reload;
    }

    /** Whether it has something to shoot at now (then it turns itself to aim). */
    public boolean aiming() {
        return this.aiming;
    }

    /** Ticks since its last shot (counted while it reloads; after that, as long as the reload). */
    public float sinceShot(float partialTick) {
        return this.reload <= 0 ? this.reloadTicks() : this.reloadTicks() - this.reload + partialTick;
    }

    /** Whether it has shot at anything yet since it came in sight (a model shows it loaded and ready till then). */
    public boolean loaded() {
        return this.reload <= 0;
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == EVENT_SHOT) {
            this.reload = this.reloadTicks();
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.reload > 0) {
            this.reload--;
        }
    }

    @Nullable
    public BlockPos siegePoint() {
        return this.entityData.get(POINT).orElse(null);
    }

    /** The place to batter (null: none: it shoots at foes it finds). */
    public void setSiegePoint(@Nullable BlockPos pos) {
        this.entityData.set(POINT, Optional.ofNullable(pos));
    }

    // ------------------------------------------------------------------ riding

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Soldier s && !s.isBandit()
                && java.util.Objects.equals(s.getOwnerUUID(), this.getOwnerUUID());
    }

    /** Its crewman stands at it, working it: he does not sit. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.crew();
    }

    /** Where its crewman stands (behind it, working it): blocks back and up. */
    protected abstract double crewBack();

    protected double crewUp() {
        return 0.0;
    }

    /** And to the side, blocks (the trebuchet's man stands clear of the arm). */
    protected double crewSide() {
        return 0.0;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double back = this.crewBack();
        double side = this.crewSide();
        move.accept(passenger, this.getX() + Mth.sin(yaw) * back + Mth.cos(yaw) * side, this.getY() + this.crewUp(),
                this.getZ() - Mth.cos(yaw) * back + Mth.sin(yaw) * side);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double back = this.crewBack() + 0.6;
        double x = this.getX() + Mth.sin(yaw) * back + Mth.cos(yaw) * this.crewSide();
        double z = this.getZ() - Mth.cos(yaw) * back + Mth.sin(yaw) * this.crewSide();
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
            Vec3 at = new Vec3(x, pos.getY(), z);
            if (this.level().getBlockState(pos.below()).isFaceSturdy(this.level(), pos.below(), net.minecraft.core.Direction.UP)
                    && this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(at))) {
                return at;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        // its own side's arrows and blades do not hurt it
        if (by != null && by != this && this.isAlliedTo(by) && !(by instanceof Player p && p.getUUID().equals(this.getOwnerUUID()))) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ItemTags.PLANKS) && this.getHealth() < this.getMaxHealth() && player.getUUID().equals(this.getOwnerUUID())) {
            // planks mend it
            if (!this.level().isClientSide) {
                this.heal(10);
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                this.playSound(SoundEvents.WOOD_PLACE, 1.0f, 0.8f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown() && this.crew() == null && player.getUUID().equals(this.getOwnerUUID())) {
            // left without its crewman: the nearest of the owner's men on foot is sent to work it
            if (!this.level().isClientSide) {
                Soldier man = null;
                double best = 24 * 24;
                for (Soldier s : this.level().getEntitiesOfClass(Soldier.class, this.getBoundingBox().inflate(24),
                        s -> s.isAlive() && player.getUUID().equals(s.getOwnerUUID()) && !s.isPassenger() && s.mountEntity() == null)) {
                    double d = s.distanceToSqr(this);
                    if (d < best) {
                        best = d;
                        man = s;
                    }
                }
                if (man != null) {
                    man.setMount(this.getUUID());
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("druzhina.msg.engine_crewed", man.getDisplayName()), true);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("druzhina.msg.engine_no_crew"), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hit) {
        super.dropCustomDeathLoot(source, looting, hit);
        // what is left of it
        this.spawnAtLocation(new ItemStack(Items.OAK_PLANKS, 4 + this.random.nextInt(5)));
        this.spawnAtLocation(new ItemStack(Items.STRING, 1 + this.random.nextInt(3)));
        this.spawnAtLocation(new ItemStack(Items.IRON_NUGGET, 2 + this.random.nextInt(4)));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    // ------------------------------------------------------------------ work

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Soldier crew = this.crew();
        this.aiming = false;
        if (crew == null || crew.routed()) {
            this.foe = null;
            return;
        }
        if (--this.lookDelay <= 0) {
            this.lookDelay = 10;
            this.foe = this.findFoe(crew);
            BlockPos point = this.siegePoint();
            if (point != null && this.broken(point)) {
                // the place is battered down: it goes back to shooting at foes
                this.setSiegePoint(null);
            }
        }
        Vec3 aim = this.aim();
        if (aim == null) {
            return;
        }
        this.aiming = true;
        // it aims when it stands; on the move it only moves
        boolean still = this.getDeltaMovement().horizontalDistanceSqr() < 0.0009 || crew.getNavigation().isDone();
        if (!still) {
            return;
        }
        float toward = (float) (Mth.atan2(aim.z - this.getZ(), aim.x - this.getX()) * Mth.RAD_TO_DEG) - 90;
        float yaw = Mth.approachDegrees(this.getYRot(), toward, this.turnSpeed());
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.setYHeadRot(yaw);
        this.aimAt(aim);
        if (this.reload <= 0 && Math.abs(Mth.wrapDegrees(toward - yaw)) < 3 && this.fire(aim, this.foe, crew)) {
            this.reload = this.reloadTicks();
            this.level().broadcastEntityEvent(this, EVENT_SHOT);
        }
    }

    /** Nothing solid is left at {@code point} or right around it. */
    private boolean broken(BlockPos point) {
        for (BlockPos p : BlockPos.betweenClosed(point.offset(-1, -1, -1), point.offset(1, 1, 1))) {
            if (!this.level().getBlockState(p).getCollisionShape(this.level(), p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** What it aims at now: the commander's place, else its foe; null for nothing. */
    @Nullable
    protected Vec3 aim() {
        BlockPos point = this.siegePoint();
        if (point != null) {
            Vec3 at = Vec3.atCenterOf(point);
            double d = this.horizontal(at);
            return d >= this.minRange() && d <= this.maxRange() ? at : null;
        }
        LivingEntity t = this.foe;
        return t != null && t.isAlive() ? t.position().add(0, t.getBbHeight() * 0.5, 0) : null;
    }

    /** Its crewman's foe if it reaches him, else the nearest foe it reaches. */
    @Nullable
    private LivingEntity findFoe(Soldier crew) {
        LivingEntity t = crew.getTarget();
        if (this.reaches(t, crew)) {
            return t;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(this.maxRange(), 16, this.maxRange()),
                e -> e != crew && e != this && crew.isFoe(e))) {
            double d = this.horizontal(e.position());
            if (d < bestD && this.reaches(e, crew)) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private boolean reaches(@Nullable LivingEntity t, Soldier crew) {
        if (t == null || !t.isAlive() || t.isAlliedTo(crew) || crew.isAlliedTo(t)) {
            return false;
        }
        double d = this.horizontal(t.position());
        if (d < this.minRange() || d > this.maxRange()) {
            return false;
        }
        return !this.needsSight() || this.sees(t.position().add(0, t.getBbHeight() * 0.5, 0));
    }

    /** Nothing solid between its muzzle and {@code at}. */
    protected boolean sees(Vec3 at) {
        Vec3 from = this.position().add(0, this.getBbHeight() * 0.8, 0);
        return this.level().clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    protected double horizontal(Vec3 at) {
        double dx = at.x - this.getX();
        double dz = at.z - this.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * The angle (degrees up) to throw something at {@code speed} with {@code gravity} to hit a place {@code x} away
     * and {@code y} up: the high arc or the low one; NaN when it cannot reach.
     */
    protected static double launchAngle(double x, double y, double speed, double gravity, boolean high) {
        double v2 = speed * speed;
        double root = v2 * v2 - gravity * (gravity * x * x + 2 * y * v2);
        if (root < 0) {
            return Double.NaN;
        }
        double s = Math.sqrt(root);
        return Math.atan((v2 + (high ? s : -s)) / (gravity * x)) * Mth.RAD_TO_DEG;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = this.getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        BlockPos point = this.siegePoint();
        if (point != null) {
            tag.put("SiegePoint", NbtUtils.writeBlockPos(point));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        this.setSiegePoint(tag.contains("SiegePoint") ? NbtUtils.readBlockPos(tag.getCompound("SiegePoint")) : null);
    }
}
