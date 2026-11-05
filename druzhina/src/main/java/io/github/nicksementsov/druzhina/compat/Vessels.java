package io.github.nicksementsov.druzhina.compat;

import com.mojang.logging.LogUtils;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Boats and ships: Minecraft's boats and rafts and the ships of Small Ships (which are boats too, of any version).
 * Soldiers board the vessel their commander sails in, sit where it has seats, shoot from it, and with Small Ships
 * 2.0 or newer man its cannons, through Small Ships' own {@code ShipBridge} (looked up by name: Small Ships is not
 * needed to build or to play).
 */
public final class Vessels {
    private static final Logger LOG = LogUtils.getLogger();
    /** Small Ships leads mobs off a still ship's deck to land, unless they carry this tag. */
    public static final String STAY_ON_DECK = "smallships.stay_on_deck";
    private static final String SMALL_SHIPS = "com.talhanation.smallships.";
    private static final double CANNON_SPEED = 2.6;
    private static final double CANNON_GRAVITY = 0.06;
    /** Within this many degrees of a cannon's side the gunner fires. */
    private static final float BROADSIDE_SECTOR = 35;

    @Nullable
    private static final Bridge BRIDGE = Bridge.load();
    private static final String SOLDIER_ID = "druzhina:soldier";
    private static volatile boolean helmAllowed;
    /** Ticks between two mends of one ship: one man works at a time, as a player mends with a nugget and a plank. */
    private static final int MEND_PERIOD = 40;
    /** Ticks between two reminders that the hold has nothing to mend her with. */
    private static final int WARN_PERIOD = 20 * 60;
    /** Sails are patched by hand while they are torn below this part of their strength (Small Ships' rule). */
    private static final float SAIL_PATCH_LIMIT = 0.33f;
    /** How far from a dockyard a ship may lie to be repaired there (Small Ships' rule). */
    private static final int DOCKYARD_RANGE = 16;
    /** For each ship being mended: when the next mend may be, when the commander was last told of the empty hold. */
    private static final Map<Entity, long[]> MENDING = new WeakHashMap<>();

    private Vessels() {
    }

    /** A boat, a raft or a ship. */
    public static boolean isVessel(@Nullable Entity e) {
        return e instanceof Boat;
    }

    /** The ship a part of its hull belongs to (Small Ships' hull pieces), or the entity itself. */
    @Nullable
    public static Entity resolve(@Nullable Entity e) {
        if (e == null || isVessel(e) || BRIDGE == null) {
            return e;
        }
        Entity ship = BRIDGE.shipOf(e);
        return ship != null ? ship : e;
    }

    /** Whether ships can be sailed by soldiers here (Small Ships 2.0 or newer). */
    public static boolean canSail() {
        return BRIDGE != null;
    }

    /** Whether {@code e} stands at the ship's helm. */
    public static boolean isHelmsman(@Nullable Entity ship, Entity e) {
        return BRIDGE != null && isShip(ship) && BRIDGE.isHelmsman(ship, e);
    }

    /** The soldier at the ship's helm, or null. */
    @Nullable
    public static Soldier captainOf(@Nullable Entity ship) {
        return BRIDGE != null && isShip(ship) && BRIDGE.helmsman(ship) instanceof Soldier s ? s : null;
    }

    /** Holds (or lets go of) the helmsman's keys: forward and back work the sails or the oars, left and right the rudder. */
    public static void keys(Entity ship, boolean forward, boolean backward, boolean left, boolean right) {
        if (BRIDGE != null) {
            BRIDGE.keys(ship, forward, backward, left, right);
        }
    }

    /** Sets the sail, or takes it in. */
    public static void toggleSail(Entity ship) {
        if (BRIDGE != null) {
            BRIDGE.sail(ship);
        }
    }

    /** 0: no sail; 1 to 4: how much sail is set. */
    public static int sailState(Entity ship) {
        return BRIDGE == null ? 0 : BRIDGE.sailState(ship);
    }

    /** Speed through the water, blocks a tick (negative going astern). */
    public static float speed(Entity ship) {
        return BRIDGE == null ? 0 : BRIDGE.speed(ship);
    }

    /** How fast she turns, degrees a tick (positive to starboard). */
    public static float rotSpeed(Entity ship) {
        return BRIDGE == null ? 0 : BRIDGE.rotSpeed(ship);
    }

    /** Afloat, not leashed, locked, sinking or in a dockyard. */
    public static boolean canMakeWay(Entity ship) {
        return BRIDGE != null && BRIDGE.canMakeWay(ship);
    }

    /** Her size, speed, sails, oars and cannons. */
    public static CompoundTag profile(Entity ship) {
        return BRIDGE == null ? new CompoundTag() : BRIDGE.profile(ship);
    }

    /**
     * Lets soldiers take the helm: Small Ships lets only players and the entities of its "driverEntities" setting
     * steer, so soldiers are added to that list (for the running server; the setting's file is left as it is).
     */
    public static void allowSoldiersAtHelm() {
        if (BRIDGE == null || helmAllowed) {
            return;
        }
        try {
            Class<?> server = Class.forName("com.talhanation.smallships.config.SmallShipsConfig$Server");
            Object value = server.getField("driverEntities").get(null);
            if (value instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> config) {
                @SuppressWarnings("unchecked")
                net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<String>> drivers =
                        (net.minecraftforge.common.ForgeConfigSpec.ConfigValue<List<String>>) config;
                List<String> now = drivers.get();
                if (!now.contains(SOLDIER_ID)) {
                    List<String> more = new java.util.ArrayList<>(now);
                    more.add(SOLDIER_ID);
                    drivers.set(more);
                }
                helmAllowed = true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Druzhina: could not let soldiers take the helm of Small Ships' ships", e);
            helmAllowed = true;
        }
    }

    /** Forgets that soldiers were let at the helm (a new server, a reloaded setting). */
    public static void resetHelm() {
        helmAllowed = false;
    }

    /** A ship of Small Ships. */
    public static boolean isShip(@Nullable Entity e) {
        return e instanceof Boat && e.getClass().getName().startsWith(SMALL_SHIPS);
    }

    /** The vessel {@code e} sits in, or null. */
    @Nullable
    public static Entity vesselOf(@Nullable Entity e) {
        Entity v = e == null ? null : e.getVehicle();
        return isVessel(v) ? v : null;
    }

    /** How many more can sit in it (not at the helm); -1 when it cannot be told (then one tries). */
    public static int freeSeats(Entity vessel) {
        if (isShip(vessel)) {
            ListTag seats = BRIDGE == null ? null : BRIDGE.seats(vessel);
            if (seats == null) {
                return -1;
            }
            int free = 0;
            for (Tag t : seats) {
                CompoundTag seat = (CompoundTag) t;
                if (!"DRIVER".equals(seat.getString("Type")) && !seat.hasUUID("Occupant") && !seat.getBoolean("Blocked")) {
                    free++;
                }
            }
            return free;
        }
        int max = vessel instanceof ChestBoat ? 1 : 2;
        return Math.max(0, max - vessel.getPassengers().size());
    }

    /** Whether {@code e} stands close enough to the vessel's side to climb in. */
    public static boolean within(Entity vessel, Entity e, double reach) {
        AABB me = e.getBoundingBox();
        if (vessel.getBoundingBox().inflate(reach, 1.5, reach).intersects(me)) {
            return true;
        }
        if (BRIDGE != null && isShip(vessel)) {
            for (AABB hull : BRIDGE.hull(vessel)) {
                if (hull.inflate(reach, 1.5, reach).intersects(me)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Just aboard: a man who does not shoot takes a free gunner's seat at a cannon, if the ship has one (an archer
     * stays at the rail).
     */
    public static void settle(Soldier s, Entity vessel) {
        if (BRIDGE == null || !isShip(vessel) || s.getMainHandItem().getItem() instanceof BowItem || s.getMainHandItem().getItem() instanceof CrossbowItem
                || BRIDGE.isHelmsman(vessel, s)) {
            return;
        }
        ListTag seats = BRIDGE.seats(vessel);
        if (seats == null || gunnerSlot(seats, s.getUUID()) >= 0) {
            return;
        }
        for (Tag t : seats) {
            CompoundTag seat = (CompoundTag) t;
            if ("GUNNER".equals(seat.getString("Type")) && !seat.hasUUID("Occupant") && !seat.getBoolean("Blocked")
                    && BRIDGE.takeSeat(vessel, s, seat.getInt("Id"))) {
                return;
            }
        }
    }

    private static int gunnerSlot(ListTag seats, UUID who) {
        for (Tag t : seats) {
            CompoundTag seat = (CompoundTag) t;
            if ("GUNNER".equals(seat.getString("Type")) && seat.hasUUID("Occupant") && who.equals(seat.getUUID("Occupant"))) {
                return seat.getInt("Slot");
            }
        }
        return -1;
    }

    /**
     * A soldier at a ship's cannon fires it at his enemy (or the nearest one) when the enemy is off that side of the
     * ship. When nobody aims the broadside from the helm, he lays the guns himself.
     */
    public static void tickGunner(Soldier s, Entity ship) {
        if (BRIDGE == null || !isShip(ship)) {
            return;
        }
        ListTag seats = BRIDGE.seats(ship);
        int slot = seats == null ? -1 : gunnerSlot(seats, s.getUUID());
        if (slot < 0) {
            return;
        }
        CompoundTag cannon = null;
        for (Tag t : BRIDGE.cannons(ship)) {
            if (((CompoundTag) t).getInt("Slot") == slot) {
                cannon = (CompoundTag) t;
            }
        }
        if (cannon == null || !cannon.getBoolean("Ready")) {
            return;
        }
        Vec3 muzzle = new Vec3(cannon.getDouble("X"), cannon.getDouble("Y"), cannon.getDouble("Z"));
        boolean starboard = cannon.getBoolean("Starboard");
        float sideYaw = ship.getYRot() + (starboard ? 90 : -90);
        Vec3 side = new Vec3(-Mth.sin(sideYaw * Mth.DEG_TO_RAD), 0, Mth.cos(sideYaw * Mth.DEG_TO_RAD));

        LivingEntity target = s.getTarget();
        if (!onBroadside(target, muzzle, side, s)) {
            target = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : s.level().getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(48),
                    e -> e.isAlive() && !ship.hasPassenger(e) && s.isFoe(e))) {
                double d = e.distanceToSqr(muzzle);
                if (d < best && onBroadside(e, muzzle, side, s)) {
                    best = d;
                    target = e;
                }
            }
        }
        if (target == null || !clear(s, muzzle, target)) {
            return;
        }
        if (!(BRIDGE.helmsman(ship) instanceof Player)) {
            // nobody aims from the helm: he lays the guns of his side for the distance
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.4, 0).subtract(muzzle);
            float pitch = elevation(Math.sqrt(to.x * to.x + to.z * to.z), to.y);
            BRIDGE.aim(ship, starboard, pitch);
        }
        BRIDGE.fire(ship, s, slot);
    }

    /** Off the side of the ship that the cannon looks out of, at a cannon's range. */
    private static boolean onBroadside(@Nullable LivingEntity t, Vec3 muzzle, Vec3 side, Soldier s) {
        if (t == null || !t.isAlive() || !s.canAttack(t)) {
            return false;
        }
        double dx = t.getX() - muzzle.x;
        double dz = t.getZ() - muzzle.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d > 6 && d < 56 && (dx * side.x + dz * side.z) / d > Mth.cos(BROADSIDE_SECTOR * Mth.DEG_TO_RAD);
    }

    /** No friend in the way of the shot, nor close to where it falls (a ball scatters what it strikes). */
    private static boolean clear(Soldier s, Vec3 from, LivingEntity t) {
        Vec3 to = t.position().add(0, t.getBbHeight() / 2, 0);
        if (!s.level().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(3), e -> e != t && e.isAlive() && s.isAlliedTo(e)).isEmpty()) {
            return false;
        }
        AABB box = new AABB(from, to).inflate(2);
        for (Entity e : s.level().getEntities(s, box, e -> e != t && (e instanceof LivingEntity && s.isAlliedTo(e)
                || isVessel(e) && e != s.getVehicle() && e.getPassengers().stream().anyMatch(s::isAlliedTo)))) {
            if (e.getBoundingBox().inflate(1.0).clip(from, to).isPresent()) {
                return false;
            }
        }
        return true;
    }

    /** The lower angle, in degrees, that puts a cannon ball {@code x} blocks away and {@code y} up. */
    static float elevation(double x, double y) {
        // the ball slows in the air: aim as if a little further
        x *= 1.12;
        double v2 = CANNON_SPEED * CANNON_SPEED;
        double root = v2 * v2 - CANNON_GRAVITY * (CANNON_GRAVITY * x * x + 2 * y * v2);
        if (root < 0) {
            return 30;
        }
        double angle = Math.atan((v2 - Math.sqrt(root)) / (CANNON_GRAVITY * x)) * Mth.RAD_TO_DEG;
        return (float) Mth.clamp(angle, -10, 30);
    }

    // ------------------------------------------------------------------ the ship's carpenter

    /**
     * The crew mend their ship (Small Ships 2.0.1 or newer). A man aboard patches the hull with a plank and an iron
     * nugget, and the torn sails with string and a nugget, as far as mending by hand goes (the same rules as for a
     * player), paying from her hold or, when the commander is aboard, from his pack. Lying still within reach of a
     * dockyard, they have her repaired there in full. One man works at a time on one ship, and not the helmsman while
     * another can.
     */
    public static void tickCarpenter(Soldier s, Entity ship) {
        if (BRIDGE == null || BRIDGE.yard == null || !isShip(ship) || !(s.level() instanceof ServerLevel level)) {
            return;
        }
        if (BRIDGE.isHelmsman(ship, s) && ship.getPassengers().stream().anyMatch(p -> p != s && p instanceof Soldier o && o.isAlliedTo(s))) {
            return;
        }
        long now = level.getGameTime();
        long[] times = MENDING.computeIfAbsent(ship, k -> new long[2]);
        if (now < times[0]) {
            return;
        }
        times[0] = now + MEND_PERIOD;
        Yard yard = BRIDGE.yard;
        CompoundTag c = yard.condition(ship);
        if (c.isEmpty() || c.getBoolean("Sinking") || c.getBoolean("Sunken") || c.getBoolean("DockyardWork")) {
            return;
        }
        float damage = c.getFloat("Damage");
        float sailMax = c.getFloat("SailMaxHealth");
        float sail = c.getFloat("SailHealth");
        boolean hullByHand = damage > c.getFloat("HandRepairFloor") + 0.5f;
        boolean sailsByHand = sailMax > 0 && sail < sailMax * SAIL_PATCH_LIMIT - 0.5f;
        boolean hullWorn = damage > 0.5f;
        boolean sailsWorn = sailMax > 0 && sail < sailMax - 0.5f;
        if (!hullWorn && !sailsWorn) {
            return;
        }
        List<Container> payers = payers(s, ship);
        if (payers.isEmpty()) {
            return;
        }
        if (Math.abs(speed(ship)) < 0.01f && !(BRIDGE.helmsman(ship) instanceof Player) && toDockyard(level, ship, payers, hullWorn, sailsWorn)) {
            s.swing(InteractionHand.MAIN_HAND);
            return;
        }
        if (hullByHand) {
            for (Container payer : payers) {
                if (yard.repairHull(ship, payer)) {
                    s.getLookControl().setLookAt(ship.getX(), s.getY() - 0.6, ship.getZ());
                    s.swing(InteractionHand.MAIN_HAND);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                            s.getX(), s.getY() + 0.2, s.getZ(), 12, 0.5, 0.2, 0.5, 0.15);
                    return;
                }
            }
        }
        if (sailsByHand) {
            for (Container payer : payers) {
                if (yard.patchSails(ship, payer)) {
                    s.getLookControl().setLookAt(ship.getX(), ship.getY() + 5, ship.getZ());
                    s.swing(InteractionHand.MAIN_HAND);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WHITE_WOOL.defaultBlockState()),
                            ship.getX(), ship.getY() + 4.5, ship.getZ(), 16, 0.6, 1.2, 0.6, 0.1);
                    return;
                }
            }
        }
        Player boss = s.commander();
        if ((hullByHand || sailsByHand) && now >= times[1] && boss != null && boss.distanceToSqr(ship) < 64 * 64) {
            // nothing to mend her with: the commander hears of it now and then
            times[1] = now + WARN_PERIOD;
            boss.displayClientMessage(Component.translatable(hullByHand ? "druzhina.msg.no_timber" : "druzhina.msg.no_cloth", ship.getDisplayName()), true);
        }
    }

    /** What the crew pay with: the ship's hold, then the pack of their commander when he is aboard. */
    private static List<Container> payers(Soldier s, Entity ship) {
        List<Container> payers = new java.util.ArrayList<>(2);
        if (ship instanceof Container hold) {
            payers.add(hold);
        }
        Player boss = s.commander();
        if (boss != null && boss.getVehicle() == ship && !boss.isCreative()) {
            payers.add(boss.getInventory());
        }
        return payers;
    }

    /** Has her repaired at a dockyard within reach, if one is free and the payer can afford it. */
    private static boolean toDockyard(ServerLevel level, Entity ship, List<Container> payers, boolean hull, boolean sails) {
        Yard yard = BRIDGE.yard;
        AABB reach = ship.getBoundingBox().inflate(DOCKYARD_RANGE + 1);
        int x0 = Mth.floor(reach.minX) >> 4, x1 = Mth.floor(reach.maxX) >> 4;
        int z0 = Mth.floor(reach.minZ) >> 4, z1 = Mth.floor(reach.maxZ) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet())) {
                    if (!reach.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) || !yard.isDockyard(level, pos)) {
                        continue;
                    }
                    for (Container payer : payers) {
                        // all of it, or what can be paid for
                        if (yard.dockyard(level, pos, ship, payer, hull, sails)
                                || hull && sails && (yard.dockyard(level, pos, ship, payer, true, false) || yard.dockyard(level, pos, ship, payer, false, true))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Small Ships' bridge for other mods, from version 2.0. */
    private record Bridge(MethodHandle seats, MethodHandle takeSeat, MethodHandle cannons, MethodHandle fire, MethodHandle aim,
                          MethodHandle helmsman, MethodHandle hull, MethodHandle shipOf, MethodHandle isHelmsman, MethodHandle keys,
                          MethodHandle sail, MethodHandle sailState, MethodHandle speed, MethodHandle rotSpeed, MethodHandle canMakeWay,
                          MethodHandle profile, @Nullable Yard yard) {
        @Nullable
        static Bridge load() {
            try {
                Class<?> c = Class.forName("com.talhanation.smallships.api.ShipBridge");
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                return new Bridge(
                    l.findStatic(c, "getSeats", MethodType.methodType(ListTag.class, Entity.class)),
                    l.findStatic(c, "takeSeat", MethodType.methodType(boolean.class, Entity.class, Entity.class, int.class)),
                    l.findStatic(c, "getCannons", MethodType.methodType(ListTag.class, Entity.class)),
                    l.findStatic(c, "fireCannon", MethodType.methodType(boolean.class, Entity.class, LivingEntity.class, int.class)),
                    l.findStatic(c, "setBroadsideAim", MethodType.methodType(void.class, Entity.class, boolean.class, float.class, float.class)),
                    l.findStatic(c, "getHelmsman", MethodType.methodType(Entity.class, Entity.class)),
                    l.findStatic(c, "getHullBoxes", MethodType.methodType(List.class, Entity.class)),
                    l.findStatic(c, "getShipOf", MethodType.methodType(Entity.class, Entity.class)),
                    l.findStatic(c, "isHelmsman", MethodType.methodType(boolean.class, Entity.class, Entity.class)),
                    l.findStatic(c, "pressKeys", MethodType.methodType(void.class, Entity.class, boolean.class, boolean.class, boolean.class, boolean.class)),
                    l.findStatic(c, "pressSailKey", MethodType.methodType(void.class, Entity.class)),
                    l.findStatic(c, "getSailState", MethodType.methodType(int.class, Entity.class)),
                    l.findStatic(c, "getSpeed", MethodType.methodType(float.class, Entity.class)),
                    l.findStatic(c, "getRotSpeed", MethodType.methodType(float.class, Entity.class)),
                    l.findStatic(c, "canMakeWay", MethodType.methodType(boolean.class, Entity.class)),
                    l.findStatic(c, "getProfile", MethodType.methodType(CompoundTag.class, Entity.class)),
                    Yard.load(c, l));
            } catch (ClassNotFoundException e) {
                return null;
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("Druzhina: Small Ships' ShipBridge is not as expected, ships are boats only", e);
                return null;
            }
        }

        @Nullable
        ListTag seats(Entity ship) {
            try {
                return (ListTag) this.seats.invokeExact(ship);
            } catch (Throwable t) {
                return null;
            }
        }

        boolean takeSeat(Entity ship, Entity who, int id) {
            try {
                return (boolean) this.takeSeat.invokeExact(ship, who, id);
            } catch (Throwable t) {
                return false;
            }
        }

        ListTag cannons(Entity ship) {
            try {
                return (ListTag) this.cannons.invokeExact(ship);
            } catch (Throwable t) {
                return new ListTag();
            }
        }

        boolean fire(Entity ship, LivingEntity gunner, int slot) {
            try {
                return (boolean) this.fire.invokeExact(ship, gunner, slot);
            } catch (Throwable t) {
                return false;
            }
        }

        void aim(Entity ship, boolean starboard, float pitch) {
            try {
                this.aim.invokeExact(ship, starboard, pitch, 0.0f);
            } catch (Throwable t) {
                // no aiming then
            }
        }

        @Nullable
        Entity helmsman(Entity ship) {
            try {
                return (Entity) this.helmsman.invokeExact(ship);
            } catch (Throwable t) {
                return null;
            }
        }

        @Nullable
        Entity shipOf(Entity e) {
            try {
                return (Entity) this.shipOf.invokeExact(e);
            } catch (Throwable t) {
                return null;
            }
        }

        boolean isHelmsman(Entity ship, Entity e) {
            try {
                return (boolean) this.isHelmsman.invokeExact(ship, e);
            } catch (Throwable t) {
                return false;
            }
        }

        void keys(Entity ship, boolean forward, boolean backward, boolean left, boolean right) {
            try {
                this.keys.invokeExact(ship, forward, backward, left, right);
            } catch (Throwable t) {
                // the ship stays as she is
            }
        }

        void sail(Entity ship) {
            try {
                this.sail.invokeExact(ship);
            } catch (Throwable t) {
                // no sail
            }
        }

        int sailState(Entity ship) {
            try {
                return (int) this.sailState.invokeExact(ship);
            } catch (Throwable t) {
                return 0;
            }
        }

        float speed(Entity ship) {
            try {
                return (float) this.speed.invokeExact(ship);
            } catch (Throwable t) {
                return 0;
            }
        }

        float rotSpeed(Entity ship) {
            try {
                return (float) this.rotSpeed.invokeExact(ship);
            } catch (Throwable t) {
                return 0;
            }
        }

        boolean canMakeWay(Entity ship) {
            try {
                return (boolean) this.canMakeWay.invokeExact(ship);
            } catch (Throwable t) {
                return false;
            }
        }

        CompoundTag profile(Entity ship) {
            try {
                return (CompoundTag) this.profile.invokeExact(ship);
            } catch (Throwable t) {
                return new CompoundTag();
            }
        }

        @SuppressWarnings("unchecked")
        List<AABB> hull(Entity ship) {
            try {
                return (List<AABB>) this.hull.invokeExact(ship);
            } catch (Throwable t) {
                return List.of();
            }
        }
    }

    /** Mending ships: by hand and at a dockyard (Small Ships 2.0.1 or newer). */
    private record Yard(MethodHandle condition, MethodHandle repairHull, MethodHandle patchSails, MethodHandle isDockyard, MethodHandle dockyard) {
        @Nullable
        static Yard load(Class<?> c, MethodHandles.Lookup l) {
            try {
                return new Yard(
                    l.findStatic(c, "getCondition", MethodType.methodType(CompoundTag.class, Entity.class)),
                    l.findStatic(c, "repairHullByHand", MethodType.methodType(boolean.class, Entity.class, Container.class)),
                    l.findStatic(c, "patchSails", MethodType.methodType(boolean.class, Entity.class, Container.class)),
                    l.findStatic(c, "isDockyard", MethodType.methodType(boolean.class, Level.class, BlockPos.class)),
                    l.findStatic(c, "startDockyardRepair",
                        MethodType.methodType(boolean.class, Level.class, BlockPos.class, Entity.class, Container.class, boolean.class, boolean.class)));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.info("Druzhina: this Small Ships has no mending for other mods, soldiers do not mend ships");
                return null;
            }
        }

        CompoundTag condition(Entity ship) {
            try {
                return (CompoundTag) this.condition.invokeExact(ship);
            } catch (Throwable t) {
                return new CompoundTag();
            }
        }

        boolean repairHull(Entity ship, Container payer) {
            try {
                return (boolean) this.repairHull.invokeExact(ship, payer);
            } catch (Throwable t) {
                return false;
            }
        }

        boolean patchSails(Entity ship, Container payer) {
            try {
                return (boolean) this.patchSails.invokeExact(ship, payer);
            } catch (Throwable t) {
                return false;
            }
        }

        boolean isDockyard(Level level, BlockPos pos) {
            try {
                return (boolean) this.isDockyard.invokeExact(level, pos);
            } catch (Throwable t) {
                return false;
            }
        }

        boolean dockyard(Level level, BlockPos pos, Entity ship, Container payer, boolean hull, boolean sails) {
            try {
                return (boolean) this.dockyard.invokeExact(level, pos, ship, payer, hull, sails);
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
