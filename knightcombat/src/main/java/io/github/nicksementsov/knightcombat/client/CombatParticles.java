package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.combat.Armor;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Lances;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.Shields;
import io.github.nicksementsov.knightcombat.network.Impact;
import io.github.nicksementsov.knightcombat.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The particles a fight throws up on this client. Steel on steel and on mail strikes sparks, blades meeting in a
 * parry flash, blows that bite draw blood (which lies on the ground for a while), shields and lances splinter, and
 * feet and hooves kick up dust: dodges, running attacks, kicks, hard landings, battle cries and galloping horses.
 * Mail and plate also clink at every step.
 */
final class CombatParticles {
    /** Colour of dust when the ground has none to give. */
    private static final int DUST = 0xA8987C;
    /** Metres walked between two steps, as Minecraft counts its footsteps. */
    private static final float STEP = 1.67f;
    /** Fighters further away than this make no dust and no steps. */
    private static final double FAR = 48;

    private static final Map<Entity, Track> TRACKS = new WeakHashMap<>();

    private CombatParticles() {
    }

    /** What is followed of a fighter from one tick to the next. */
    private static final class Track {
        double x;
        double z;
        double peak;
        boolean ground = true;
        float walked;
        Phase phase = Phase.IDLE;

        Track(Entity entity) {
            x = entity.getX();
            z = entity.getZ();
            peak = entity.getY();
        }
    }

    private static boolean enabled() {
        Minecraft mc = Minecraft.getInstance();
        return CombatConfig.PARTICLES.get() && mc.options.particles().get() != ParticleStatus.MINIMAL && FightParticles.ready();
    }

    /** How many particles to make of {@code n}: fewer with Minecraft's "decreased" particles. */
    private static int count(float n) {
        float scale = Minecraft.getInstance().options.particles().get() == ParticleStatus.DECREASED ? 0.5f : 1;
        return Math.max(1, Math.round(n * scale));
    }

    private static void add(Particle particle) {
        Minecraft.getInstance().particleEngine.add(particle);
    }

    // ---------------------------------------------------------------- blows

    static void onImpact(ClientLevel level, Impact impact, @Nullable Entity attacker, @Nullable Entity victim, Vec3 point, float weight) {
        if (!enabled()) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 away = away(attacker, point, random);
        LivingEntity living = victim instanceof LivingEntity l ? l : null;
        ItemStack shield = shieldOf(living);
        switch (impact) {
            case HIT, KILL -> {
                boolean kill = impact == Impact.KILL;
                if (living != null && CombatConfig.BLOOD.get() && bleeds(living)) {
                    blood(level, living, point, away, count(4 + 9 * weight + (kill ? 10 : 0)), kill ? 2 : 1, random);
                }
                if (living != null && (Armor.metalPieces(living) >= 2 || living.getArmorValue() >= 10)) {
                    // The edge skids over mail and plate.
                    sparks(level, point, away, count(3 + 5 * weight), 0.3f, random);
                }
                if (attacker instanceof LivingEntity rider && rider.isPassenger() && Lances.isLance(rider.getMainHandItem())) {
                    splinters(level, rider.getMainHandItem(), point, away, count(10), 0.25f, random);
                }
            }
            case BLOCK -> {
                if (!shield.isEmpty()) {
                    splinters(level, shield, point, away, count(3 + 5 * weight), 0.15f, random);
                    sparks(level, point, away, count(2), 0.25f, random);
                } else {
                    sparks(level, point, away, count(6 + 8 * weight), 0.35f, random);
                    add(new FightParticles.Flash(level, point.x, point.y, point.z, 0.25f + 0.2f * weight));
                }
                if (attacker instanceof LivingEntity rider && rider.isPassenger() && Lances.isLance(rider.getMainHandItem())) {
                    splinters(level, rider.getMainHandItem(), point, away, count(8), 0.25f, random);
                }
            }
            case PARRY -> {
                sparks(level, point, away.scale(-1), count(10 + 6 * weight), 0.4f, random);
                add(new FightParticles.Flash(level, point.x, point.y, point.z, 0.7f));
                if (!shield.isEmpty()) {
                    splinters(level, shield, point, away, count(3), 0.15f, random);
                }
            }
            case GUARD_BREAK -> {
                sparks(level, point, away, count(14), 0.45f, random);
                add(new FightParticles.Flash(level, point.x, point.y, point.z, 0.9f));
                if (!shield.isEmpty()) {
                    splinters(level, shield, point, away, count(14), 0.3f, random);
                }
                if (living != null && living.onGround()) {
                    // Driven back a step, the defender's feet scrape the ground.
                    feetDust(level, living, away, count(3), 0.08f, random);
                }
            }
            case WALL -> wall(level, attacker, point, weight, random);
            case KICK -> {
                if (living != null) {
                    if (living.onGround()) {
                        feetDust(level, living, away, count(3), 0.1f, random);
                    }
                    add(new FightParticles.Dust(level, point.x, point.y, point.z, away.x * 0.05, 0.01, away.z * 0.05, 0.35f,
                            groundColor(level, living), 0.35f));
                }
            }
            case CRY -> {
                if (attacker != null && attacker.onGround()) {
                    int color = groundColor(level, attacker);
                    add(new FightParticles.Ring(level, attacker.getX(), attacker.getY(), attacker.getZ(), 4.5f, color, 0.45f));
                    int puffs = count(14);
                    for (int i = 0; i < puffs; i++) {
                        float angle = Mth.TWO_PI * i / puffs + random.nextFloat() * 0.3f;
                        double dx = Mth.cos(angle);
                        double dz = Mth.sin(angle);
                        add(new FightParticles.Dust(level, attacker.getX() + dx * 0.5, attacker.getY() + 0.1, attacker.getZ() + dz * 0.5,
                                dx * 0.32, 0.02, dz * 0.32, 0.5f, color, 0.5f));
                    }
                }
            }
        }
    }

    /** The way a blow at {@code point} went: from the one who struck it, level with the ground. */
    private static Vec3 away(@Nullable Entity attacker, Vec3 point, RandomSource random) {
        Vec3 from = attacker != null ? attacker.position().add(0, attacker.getBbHeight() * 0.6, 0) : null;
        Vec3 dir = from != null ? point.subtract(from) : Vec3.ZERO;
        dir = new Vec3(dir.x, dir.y * 0.3, dir.z);
        if (dir.lengthSqr() < 1.0E-4) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            return new Vec3(Mth.cos(angle), 0, Mth.sin(angle));
        }
        return dir.normalize();
    }

    /** The shield {@code entity} defends with, or nothing. */
    private static ItemStack shieldOf(@Nullable LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        if (Shields.isShield(entity.getOffhandItem())) {
            return entity.getOffhandItem();
        }
        return Shields.isShield(entity.getMainHandItem()) ? entity.getMainHandItem() : ItemStack.EMPTY;
    }

    /** Skeletons, golems, blazes, slimes and the like have no blood to spill. */
    private static boolean bleeds(LivingEntity entity) {
        return !(entity instanceof AbstractSkeleton || entity instanceof AbstractGolem || entity instanceof Blaze
                || entity instanceof Slime || entity instanceof Vex || entity instanceof WitherBoss || entity instanceof ArmorStand);
    }

    private static void blood(ClientLevel level, LivingEntity living, Vec3 point, Vec3 away, int drops, int splashes, RandomSource random) {
        // Dark and thick in the walking dead, green-brown in spiders and insects.
        float r = 0.55f;
        float g = 0.03f;
        float b = 0.03f;
        if (living.getMobType() == MobType.UNDEAD) {
            r = 0.3f;
            g = 0.07f;
            b = 0.04f;
        } else if (living.getMobType() == MobType.ARTHROPOD) {
            r = 0.32f;
            g = 0.38f;
            b = 0.08f;
        }
        for (int i = 0; i < splashes; i++) {
            add(new FightParticles.Splash(level, point.x, point.y, point.z, 0.25f + random.nextFloat() * 0.15f, r, g, b));
        }
        for (int i = 0; i < drops; i++) {
            double speed = 0.08 + random.nextDouble() * 0.2;
            add(new FightParticles.Blood(level, point.x, point.y, point.z,
                    away.x * speed + random.nextGaussian() * 0.06,
                    0.04 + random.nextDouble() * 0.16,
                    away.z * speed + random.nextGaussian() * 0.06, r, g, b));
        }
    }

    /** Sparks flying from {@code point}, mostly along {@code dir}. */
    private static void sparks(ClientLevel level, Vec3 point, Vec3 dir, int count, float speed, RandomSource random) {
        for (int i = 0; i < count; i++) {
            Vec3 spread = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            double s = speed * (0.35 + random.nextDouble() * 0.65);
            Vec3 v = spread.scale(s).add(dir.scale(s * 0.6)).add(0, 0.06, 0);
            add(new FightParticles.Spark(level, point.x, point.y, point.z, v.x, v.y, v.z));
        }
    }

    /** Splinters of a shield or a lance: bits of the item itself. */
    private static void splinters(ClientLevel level, ItemStack stack, Vec3 point, Vec3 dir, int count, float speed, RandomSource random) {
        ItemParticleOption bits = new ItemParticleOption(ParticleTypes.ITEM, stack);
        for (int i = 0; i < count; i++) {
            double s = speed * (0.4 + random.nextDouble() * 0.6);
            level.addParticle(bits, point.x, point.y, point.z,
                    dir.x * s + random.nextGaussian() * 0.08, 0.05 + random.nextDouble() * 0.15, dir.z * s + random.nextGaussian() * 0.08);
        }
    }

    /** A blow on a wall: sparks off stone and metal, dust off everything. */
    private static void wall(ClientLevel level, @Nullable Entity attacker, Vec3 point, float weight, RandomSource random) {
        Vec3 look = attacker != null ? attacker.getLookAngle() : Vec3.ZERO;
        BlockPos pos = BlockPos.containing(point.add(look.scale(0.1)));
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        Vec3 back = look.scale(-1);
        SoundType sound = state.getSoundType();
        boolean hard = sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.DEEPSLATE_BRICKS
                || sound == SoundType.DEEPSLATE_TILES || sound == SoundType.POLISHED_DEEPSLATE || sound == SoundType.METAL
                || sound == SoundType.ANVIL || sound == SoundType.CHAIN || sound == SoundType.NETHER_BRICKS
                || sound == SoundType.BASALT || sound == SoundType.TUFF || sound == SoundType.COPPER || sound == SoundType.LANTERN;
        if (hard) {
            sparks(level, point, back, count(5 + 6 * weight), 0.35f, random);
            add(new FightParticles.Flash(level, point.x, point.y, point.z, 0.3f));
        }
        int color = blockColor(level, pos, state);
        for (int i = 0, n = count(2 + 2 * weight); i < n; i++) {
            add(new FightParticles.Dust(level, point.x, point.y, point.z, back.x * 0.06 + random.nextGaussian() * 0.02,
                    random.nextGaussian() * 0.02, back.z * 0.06 + random.nextGaussian() * 0.02, 0.3f, color, 0.45f));
        }
    }

    // ---------------------------------------------------------------- feet and hooves

    /**
     * Follows a player once a tick: dust when they dodge, run into an attack or land hard, dust under the hooves of
     * the horse they gallop on, and the clink of mail and plate at each step.
     */
    static void tick(Player player, @Nullable FighterAnim anim) {
        Minecraft mc = Minecraft.getInstance();
        if (!(player.level() instanceof ClientLevel level) || mc.cameraEntity == null
                || player.distanceToSqr(mc.cameraEntity) > FAR * FAR || player.isSpectator()) {
            TRACKS.remove(player);
            return;
        }
        Track track = TRACKS.computeIfAbsent(player, Track::new);
        double dx = player.getX() - track.x;
        double dz = player.getZ() - track.z;
        track.x = player.getX();
        track.z = player.getZ();
        double moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 3) {
            // Teleported.
            moved = 0;
        }
        boolean particles = enabled();
        RandomSource random = level.getRandom();

        boolean ground = player.onGround();
        boolean wasGround = track.ground;
        if (!ground && !player.isPassenger()) {
            track.peak = Math.max(track.peak, player.getY());
        } else {
            double drop = track.peak - player.getY();
            if (!track.ground && drop >= 2 && particles && !player.isInWater() && !player.getAbilities().flying) {
                landing(level, player, (float) drop, random);
            }
            track.peak = player.getY();
        }
        track.ground = ground;

        boolean walking = ground && !player.isPassenger() && !player.isShiftKeyDown() && !player.isInWater();
        track.walked = walking ? track.walked + (float) moved : 0;
        if (track.walked >= STEP) {
            track.walked -= STEP;
            armorStep(level, player, random);
        }

        if (particles && player.getVehicle() instanceof LivingEntity mount && mount.onGround() && moved > 0.2) {
            for (int i = 0, n = moved > 0.3 ? 2 : 1; i < n; i++) {
                hooves(level, mount, dx / moved, dz / moved, (float) moved, random);
            }
        }

        // A dodge hops off the ground before the server tells of it, so the ground of the last tick counts too.
        Phase phase = anim != null ? anim.phase : Phase.IDLE;
        if (phase != track.phase && particles && (ground || wasGround) && !player.isPassenger()) {
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            if (phase == Phase.DODGE) {
                feetDust(level, player, forward.scale(-1), count(6), 0.09f, random);
            } else if (phase == Phase.RELEASE && (anim.has(CombatState.SPRINT) || anim.has(CombatState.HEAVY))) {
                feetDust(level, player, forward.scale(-1), count(4), 0.06f, random);
            }
        }
        track.phase = phase;
    }

    /** Mail and plate clink at each step; leather and cloth stay quiet. */
    private static void armorStep(ClientLevel level, Player player, RandomSource random) {
        int metal = Armor.metalPieces(player);
        if (metal < 2) {
            return;
        }
        float volume = (0.12f + 0.04f * metal) * (player.isSprinting() ? 1.3f : 1);
        level.playLocalSound(player.getX(), player.getY() + 0.4, player.getZ(), ModSounds.ARMOR_STEP.get(), SoundSource.PLAYERS,
                volume, 0.9f + random.nextFloat() * 0.2f, false);
    }

    /** A hard landing throws up a ring of dust. */
    private static void landing(ClientLevel level, Player player, float drop, RandomSource random) {
        int color = groundColor(level, player);
        float strength = Math.min(1, (drop - 2) / 6);
        add(new FightParticles.Ring(level, player.getX(), player.getY(), player.getZ(), 1.2f + 1.6f * strength, color, 0.35f));
        int puffs = count(6 + 6 * strength);
        for (int i = 0; i < puffs; i++) {
            float angle = Mth.TWO_PI * i / puffs + random.nextFloat() * 0.4f;
            double speed = 0.1 + 0.12 * strength;
            add(new FightParticles.Dust(level, player.getX() + Mth.cos(angle) * 0.3, player.getY() + 0.05, player.getZ() + Mth.sin(angle) * 0.3,
                    Mth.cos(angle) * speed, 0.02, Mth.sin(angle) * speed, 0.4f, color, 0.5f));
        }
    }

    /** Dust under the hooves of a galloping mount, thrown back. */
    private static void hooves(ClientLevel level, LivingEntity mount, double fx, double fz, float speed, RandomSource random) {
        int color = groundColor(level, mount);
        double back = mount.getBbWidth() * (0.2 + 0.4 * random.nextFloat());
        double side = (random.nextFloat() - 0.5f) * mount.getBbWidth() * 0.9;
        double x = mount.getX() - fx * back - fz * side;
        double z = mount.getZ() - fz * back + fx * side;
        add(new FightParticles.Dust(level, x, mount.getY() + 0.15, z, -fx * speed * 0.35, 0.04 + random.nextFloat() * 0.05,
                -fz * speed * 0.35, 0.6f + speed * 0.8f, color, 0.6f));
    }

    /** Dust scuffed up at the feet of {@code entity}, thrown along {@code dir}. */
    private static void feetDust(ClientLevel level, LivingEntity entity, Vec3 dir, int count, float speed, RandomSource random) {
        int color = groundColor(level, entity);
        for (int i = 0; i < count; i++) {
            double s = speed * (0.5 + random.nextDouble());
            add(new FightParticles.Dust(level, entity.getX() + random.nextGaussian() * 0.15, entity.getY() + 0.05,
                    entity.getZ() + random.nextGaussian() * 0.15, dir.x * s + random.nextGaussian() * 0.02, 0.015,
                    dir.z * s + random.nextGaussian() * 0.02, 0.35f, color, 0.45f));
        }
    }

    /** The colour of the dust of the ground under {@code entity}. */
    private static int groundColor(ClientLevel level, Entity entity) {
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ());
        return blockColor(level, pos, level.getBlockState(pos));
    }

    /** The colour of the dust a block gives: grass and plants give the earth under them, and every dust is pale. */
    private static int blockColor(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return DUST;
        }
        MapColor map = state.getMapColor(level, pos);
        int rgb = map == MapColor.GRASS || map == MapColor.PLANT || state.is(BlockTags.DIRT) ? MapColor.DIRT.col : map.col;
        if (map == MapColor.NONE || rgb == 0) {
            return DUST;
        }
        return mix(rgb, 0xD8D2C4, 0.35f);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 255, b >> 16 & 255));
        int g = Math.round(Mth.lerp(t, a >> 8 & 255, b >> 8 & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return r << 16 | g << 8 | bl;
    }
}
