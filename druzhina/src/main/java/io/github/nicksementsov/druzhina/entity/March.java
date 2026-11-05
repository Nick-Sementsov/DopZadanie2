package io.github.nicksementsov.druzhina.entity;

import io.github.nicksementsov.druzhina.network.DruzhinaNetwork;
import io.github.nicksementsov.druzhina.network.OrdersEchoPacket;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Men sent somewhere together from the commander's view. They form up where they are, then march in formation at the
 * pace of the slowest of them, each keeping his place in the ranks (the march waits for those who fall behind, and
 * goes on without the few who cannot get through), and halt at the end in the formation they were told. Orders given
 * with Shift wait their turn: when the men stand at one place, the march goes on to the next. A march lives only in
 * memory: after a reload every man goes straight to the place he was going to.
 */
public final class March {
    /**
     * One stage of a march: where the middle of the front stands at the end of it, which way the men face there, how
     * wide the front is (0: the usual), the formation, and whether they fall upon the enemies they meet on the way.
     */
    public record Leg(Vec3 center, float yaw, double width, Formation formation, boolean aggressive) {
    }

    private enum Phase { FORMING, MARCHING, HALTING }

    /** Nearer than this, the men need not march: each goes to his place. */
    private static final double SHORT = 8;
    /** Ticks they take at most to form up before they set off. */
    private static final int FORM_UP = 80;
    /** Ticks without getting on before the march breaks up and every man goes his own way. */
    private static final int STUCK = 160;
    /** Ticks they are given at most to take their places at a halt before going on to the next one. */
    private static final int SETTLE = 160;
    /** Formation and travel ways closer than this: the men march as they will stand, they do not wheel at the end. */
    private static final float WHEEL = 60;

    private final ServerLevel level;
    @Nullable
    private final UUID commander;
    private final List<Soldier> men;
    private final Deque<Leg> legs = new ArrayDeque<>();
    private Leg leg;
    private Phase phase = Phase.HALTING;
    /** Where the middle of the front is when the march starts, the way it goes, how far, and how far it has come. */
    private Vec3 from = Vec3.ZERO;
    private double dirX;
    private double dirZ;
    private double length;
    private double progress;
    /** Each man's place in the ranks on the march, from the middle of the front. */
    private final Map<Soldier, Vec3> offsets = new HashMap<>();
    /** The pace of the slowest man (blocks a tick), and how much of it the march goes now. */
    private double pace;
    private double throttle = 1;
    private int timer;
    private double mark;
    private int markTimer;
    private long tickedAt = -1;
    private boolean over;

    private March(ServerLevel level, @Nullable UUID commander, List<Soldier> men) {
        this.level = level;
        this.commander = commander;
        this.men = new ArrayList<>(men);
    }

    /** Sends {@code men} on a march through {@code legs}, one after another. */
    public static March start(ServerLevel level, @Nullable ServerPlayer commander, List<Soldier> men, List<Leg> legs) {
        March march = new March(level, commander == null ? null : commander.getUUID(), men);
        for (Soldier s : men) {
            s.order(Orders.HOLD, s.position(), s.getYRot());
            s.joinMarch(march);
        }
        march.legs.addAll(legs);
        march.next();
        return march;
    }

    /** One more place to go to after the others. */
    public void queue(Leg next) {
        if (this.over) {
            return;
        }
        this.legs.addLast(next);
        this.echo();
    }

    /** The stages still ahead, the present one first. */
    public List<Leg> legsLeft() {
        List<Leg> out = new ArrayList<>();
        if (!this.over && this.leg != null) {
            out.add(this.leg);
        }
        out.addAll(this.legs);
        return out;
    }

    public List<Soldier> members() {
        List<Soldier> out = new ArrayList<>(this.men.size());
        for (Soldier s : this.men) {
            if (s.isAlive() && !s.isRemoved() && s.march() == this && s.level() == this.level) {
                out.add(s);
            }
        }
        return out;
    }

    public boolean aggressive() {
        return this.leg != null && this.leg.aggressive();
    }

    /** On the way (forming up or marching), not yet at the halt. */
    public boolean moving() {
        return !this.over && this.phase != Phase.HALTING;
    }

    // ---------------------------------------------------------------- the stages

    private void next() {
        Leg l = this.legs.pollFirst();
        if (l == null) {
            this.finish();
            return;
        }
        this.leg = l;
        List<Soldier> all = this.members();
        if (all.isEmpty()) {
            this.finish();
            return;
        }
        List<Soldier> walking = new ArrayList<>();
        for (Soldier s : all) {
            if (!Army.sendCrew(s, l.center(), l.yaw())) {
                walking.add(s);
            }
        }
        // where each man is to stand at the end of this stage
        Map<Soldier, Vec3> posts = Army.assign(this.level, walking, l.center(), l.yaw(), l.width(), l.formation());
        posts.forEach((s, p) -> s.setPost(p, l.yaw()));

        Vec3 middle = centroid(walking);
        double dx = l.center().x - middle.x;
        double dz = l.center().z - middle.z;
        double far = Math.sqrt(dx * dx + dz * dz);
        this.offsets.clear();
        this.progress = 0;
        this.timer = 0;
        this.throttle = 1;
        this.mark = 0;
        this.markTimer = 0;
        if (walking.size() < 2 || far < SHORT) {
            this.phase = Phase.HALTING;
        } else {
            // on the march they face the way they go, unless it is near enough the way they will stand
            float travel = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
            float yaw = Math.abs(Mth.wrapDegrees(travel - l.yaw())) < WHEEL ? l.yaw() : travel;
            Map<Soldier, Vec3> ranks = yaw == l.yaw() ? posts : Army.assign(null, walking, l.center(), yaw, l.width(), l.formation());
            Vec3 shift = Vec3.ZERO;
            for (Map.Entry<Soldier, Vec3> e : ranks.entrySet()) {
                Vec3 off = e.getValue().subtract(l.center());
                this.offsets.put(e.getKey(), new Vec3(off.x, 0, off.z));
                shift = shift.add(off.x, 0, off.z);
            }
            shift = shift.scale(1.0 / ranks.size());
            // the formation first stands where the men are now, the middle of it on the middle of them
            this.from = new Vec3(middle.x - shift.x, middle.y, middle.z - shift.z);
            double ex = l.center().x - this.from.x;
            double ez = l.center().z - this.from.z;
            this.length = Math.sqrt(ex * ex + ez * ez);
            this.dirX = ex / Math.max(1.0E-4, this.length);
            this.dirZ = ez / Math.max(1.0E-4, this.length);
            this.pace = Double.MAX_VALUE;
            for (Soldier s : walking) {
                this.pace = Math.min(this.pace, walkSpeed(s));
            }
            this.pace *= 0.8;
            this.phase = Phase.FORMING;
        }
        this.echo();
    }

    private void finish() {
        if (this.over) {
            return;
        }
        this.over = true;
        this.phase = Phase.HALTING;
        for (Soldier s : this.men) {
            s.leaveMarch(this);
        }
    }

    /** Called by every man of the march each tick he keeps to it; the march moves on once a tick. */
    public void tick() {
        long now = this.level.getGameTime();
        if (this.over || now == this.tickedAt) {
            return;
        }
        this.tickedAt = now;
        List<Soldier> all = this.members();
        if (all.isEmpty()) {
            this.finish();
            return;
        }
        this.timer++;
        switch (this.phase) {
            case FORMING -> {
                if (this.lag(all) < 1.8 || this.timer > FORM_UP) {
                    this.phase = Phase.MARCHING;
                    this.timer = 0;
                }
            }
            case MARCHING -> {
                boolean fighting = this.aggressive() && all.stream().anyMatch(s -> s.getTarget() != null);
                double lag = this.lag(all);
                if (lag < 1.2) {
                    this.throttle = Math.min(1.3, this.throttle + 0.01);
                } else if (lag > 2.5) {
                    this.throttle = Math.max(0.1, this.throttle - 0.04);
                }
                if (!fighting && lag < 7) {
                    this.progress = Math.min(this.length, this.progress + this.pace * this.throttle);
                }
                if (this.progress >= this.length) {
                    this.phase = Phase.HALTING;
                    this.timer = 0;
                } else if (!fighting && ++this.markTimer > STUCK) {
                    // hardly got on for a long while: every man finds his own way to his place
                    if (this.progress - this.mark < 1.0) {
                        this.phase = Phase.HALTING;
                        this.timer = 0;
                    }
                    this.mark = this.progress;
                    this.markTimer = 0;
                }
            }
            case HALTING -> {
                boolean placed = true;
                for (Soldier s : all) {
                    Vec3 p = s.post();
                    if (s.getTarget() == null && !s.isCrew() && p != null && s.distanceToSqr(p.x, s.getY(), p.z) > 1.5 * 1.5) {
                        placed = false;
                        break;
                    }
                }
                if (placed && this.timer > 10 || this.timer > SETTLE) {
                    this.next();
                }
            }
        }
    }

    /** How far the men are from their places in the moving ranks (most of them: a few stuck men do not hold it up). */
    private double lag(List<Soldier> all) {
        List<Double> lags = new ArrayList<>(all.size());
        for (Soldier s : all) {
            Vec3 p = this.flat(s);
            if (p != null && s.getTarget() == null && !s.isCrew()) {
                double dx = p.x - s.getX();
                double dz = p.z - s.getZ();
                lags.add(Math.sqrt(dx * dx + dz * dz));
            }
        }
        if (lags.isEmpty()) {
            return 0;
        }
        lags.sort(null);
        return lags.get((int) ((lags.size() - 1) * 0.6));
    }

    @Nullable
    private Vec3 flat(Soldier s) {
        Vec3 off = this.offsets.get(s);
        if (off == null || this.phase == Phase.HALTING || this.over) {
            return null;
        }
        return new Vec3(this.from.x + this.dirX * this.progress + off.x, this.from.y, this.from.z + this.dirZ * this.progress + off.z);
    }

    /** Where {@code s} should be now, in the moving ranks; null at a halt (then he goes to his place). */
    @Nullable
    public Vec3 pointFor(Soldier s) {
        Vec3 p = this.flat(s);
        if (p == null) {
            return null;
        }
        BlockPos ground = Army.ground(this.level, BlockPos.containing(p.x, s.getY(), p.z));
        return new Vec3(p.x, ground != null ? ground.getY() : s.getY(), p.z);
    }

    /** The way the ranks go. */
    public float heading() {
        return (float) (Mth.atan2(-this.dirX, this.dirZ) * Mth.RAD_TO_DEG);
    }

    /** The speed (as a goal's speed modifier) at which {@code s} keeps step with the march now. */
    public double stepFor(Soldier s) {
        if (this.phase != Phase.MARCHING) {
            return 0;
        }
        double v = this.pace * this.throttle;
        return Math.sqrt(v / STEADY) / Math.max(0.05, speedAttribute(s));
    }

    /** A mob's steady pace on flat ground at speed modifier 1: its movement speed squared, times this. */
    private static final double STEADY = 2.15;

    private static double speedAttribute(Soldier s) {
        Mob steed = s.steed();
        double a = s.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return steed != null ? Math.max(a, steed.getAttributeValue(Attributes.MOVEMENT_SPEED)) : a;
    }

    /** Blocks a tick that {@code s} walks (or rides) at an ordinary pace. */
    private static double walkSpeed(Soldier s) {
        double a = speedAttribute(s);
        return STEADY * a * a;
    }

    private static Vec3 centroid(List<Soldier> men) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Soldier s : men) {
            x += s.getX();
            y += s.getY();
            z += s.getZ();
        }
        int n = Math.max(1, men.size());
        return new Vec3(x / n, y / n, z / n);
    }

    // ---------------------------------------------------------------- what the commander sees

    /** Tells the commander's view where the men are going: each man's place, and the stages of the march. */
    private void echo() {
        if (this.commander == null || !(this.level.getPlayerByUUID(this.commander) instanceof ServerPlayer player)) {
            return;
        }
        List<Soldier> all = this.members();
        List<Vec3> route = new ArrayList<>();
        for (Leg l : this.legsLeft()) {
            route.add(l.center());
        }
        echo(player, all, route);
    }

    /** Tells {@code player}'s view where {@code men} are going (their posts) and through which places. */
    public static void echo(ServerPlayer player, List<Soldier> men, List<Vec3> route) {
        echo(player, men, route, true);
    }

    /** Tells {@code player}'s view that {@code men} go to no place of theirs (they fight, follow or patrol). */
    public static void echoNone(ServerPlayer player, List<Soldier> men, List<Vec3> route) {
        echo(player, men, route, false);
    }

    private static void echo(ServerPlayer player, List<Soldier> men, List<Vec3> route, boolean withPosts) {
        int[] ids = new int[men.size()];
        List<Vec3> posts = new ArrayList<>(men.size());
        for (int i = 0; i < ids.length; i++) {
            Soldier s = men.get(i);
            ids[i] = s.getId();
            posts.add(withPosts && s.orders() == Orders.HOLD && s.post() != null && !s.isCrew() ? s.post() : null);
        }
        DruzhinaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OrdersEchoPacket(ids, posts, route));
    }
}
