package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.compat.Vessels;
import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.entity.Soldier;
import io.github.nicksementsov.druzhina.network.OrdersEchoPacket;
import io.github.nicksementsov.druzhina.server.Army;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The commander's view: the camera leaves the player and rises over the field like a free camera, the mouse picks
 * out soldiers and points where they are to go or whom they are to strike (see {@link CommandScreen}). The player
 * stays where he stood and is drawn there. On the ground the view shows where every man picked out is going and the
 * stages of his march.
 */
@Mod.EventBusSubscriber(modid = Druzhina.MODID, value = Dist.CLIENT)
public final class CommandView {
    /** How far the camera may go from the player, and how high above the ground. */
    static final double REACH = 112;
    static final double HEIGHT = 72;
    private static final double SPEED = 16;
    /** Pixels from the edge of the window where the mouse pushes the camera along. */
    private static final int EDGE = 3;

    private static boolean active;
    @Nullable
    private static Marker camera;
    @Nullable
    private static CameraType savedType;
    // where the camera is, and where it is going (it glides there)
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    private static float pitch;
    private static double tx;
    private static double ty;
    private static double tz;
    private static float tyaw;
    private static float tpitch;
    private static long lastNanos;

    /** The men picked out, by entity id; kept between visits to the view. */
    static final Set<Integer> SELECTED = new LinkedHashSet<>();
    /** The groups of Ctrl+1..9, by entity id. */
    static final Map<Integer, Set<Integer>> GROUPS = new HashMap<>();
    static Formation formation = Formation.LINE;

    /** Where each man ordered from the view is going (from the server), and the stages of his march. */
    private static final Map<Integer, Vec3> DESTS = new HashMap<>();
    private static final Map<Integer, Route> ROUTES = new HashMap<>();
    /** The enemies around, as the server's scouts name them (bandits, and all who fight for a side at war with his). */
    static final Set<Integer> HOSTILE = new java.util.HashSet<>();

    record Route(List<Vec3> points, long at) {
    }

    // what the screen shows on the ground: the places of an order being drawn, and of one just given
    static List<Vec3> preview = List.of();
    @Nullable
    static Vec3 previewFrom;
    @Nullable
    static Vec3 previewTo;
    private static List<Vec3> flash = List.of();
    private static long flashUntil;
    @Nullable
    static LivingEntity hovered;
    @Nullable
    static Entity hoveredVessel;

    // the matrices of the last frame, to turn the mouse into a ray and the men into points on the screen
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static boolean haveMatrices;

    private CommandView() {
    }

    public static boolean active() {
        return active;
    }

    // ---------------------------------------------------------------- in and out

    static void enter() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (active || player == null || mc.level == null) {
            return;
        }
        active = true;
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        yaw = tyaw = player.getYRot();
        pitch = tpitch = 55;
        // behind the player and well above him, looking down at where he stands
        double up = 14;
        double back = up / Math.tan(pitch * Mth.DEG_TO_RAD);
        x = tx = player.getX() + Mth.sin(yaw * Mth.DEG_TO_RAD) * back;
        z = tz = player.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * back;
        y = ty = player.getY() + up;
        camera = new Marker(EntityType.MARKER, mc.level);
        camera.moveTo(x, y, z, yaw, pitch);
        mc.setCameraEntity(camera);
        lastNanos = System.nanoTime();
        haveMatrices = false;
        SELECTED.removeIf(id -> !(mc.level.getEntity(id) instanceof Soldier s) || !mine(s));
        mc.setScreen(new CommandScreen());
    }

    /** Back to the player's own eyes (called when the command screen closes). */
    static void leave() {
        if (!active) {
            return;
        }
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedType != null) {
            mc.options.setCameraType(savedType);
        }
        camera = null;
        preview = List.of();
        previewFrom = null;
        previewTo = null;
        hovered = null;
        hoveredVessel = null;
    }

    /** One of the player's own men whom he may lead (not one kept by a realm's garrison). */
    static boolean mine(Entity e) {
        LocalPlayer player = Minecraft.getInstance().player;
        return e instanceof Soldier s && s.isAlive() && player != null && player.getUUID().equals(s.getOwnerUUID()) && !s.managed();
    }

    /** All the player's men the view can see and lead, by role. */
    static List<Soldier> army() {
        Minecraft mc = Minecraft.getInstance();
        List<Soldier> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return out;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (mine(e) && e instanceof Soldier s && s.isAlive() && e.distanceToSqr(mc.player) < Army.FIELD * Army.FIELD) {
                out.add(s);
            }
        }
        out.sort(java.util.Comparator.comparingInt((Soldier s) -> s.role().ordinal()).thenComparingInt(Entity::getId));
        return out;
    }

    static List<Soldier> selected() {
        Minecraft mc = Minecraft.getInstance();
        List<Soldier> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        SELECTED.removeIf(id -> !(mc.level.getEntity(id) instanceof Soldier s) || !mine(s));
        for (int id : SELECTED) {
            if (mc.level.getEntity(id) instanceof Soldier s) {
                out.add(s);
            }
        }
        return out;
    }

    static void showOrder(List<Vec3> places) {
        flash = places;
        flashUntil = System.currentTimeMillis() + 1500;
    }

    /** What the server says the men just ordered are doing: where each goes, and the stages of the march. */
    /** The scouts' word: who around is an enemy. */
    public static void hostiles(int[] ids) {
        HOSTILE.clear();
        for (int id : ids) {
            HOSTILE.add(id);
        }
    }

    /** One of those the server named an enemy, or a bandit. */
    static boolean hostile(Entity e) {
        return HOSTILE.contains(e.getId()) || e instanceof Soldier s && s.isBandit();
    }

    public static void echo(OrdersEchoPacket p) {
        Route route = p.route().isEmpty() ? null : new Route(List.copyOf(p.route()), System.currentTimeMillis());
        for (int i = 0; i < p.ids().length; i++) {
            int id = p.ids()[i];
            Vec3 post = p.post(i);
            if (post != null) {
                DESTS.put(id, post);
            } else {
                DESTS.remove(id);
            }
            if (route != null) {
                ROUTES.put(id, route);
            } else {
                ROUTES.remove(id);
            }
        }
    }

    // ---------------------------------------------------------------- the camera

    @SubscribeEvent
    public static void onKey(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (DruzhinaKeys.COMMAND.consumeClick()) {
            if (mc.screen == null && mc.player != null && !active) {
                enter();
            }
        }
        if (active && (mc.player == null || !mc.player.isAlive() || camera == null || camera.level() != mc.level)) {
            // died, or changed worlds: the view closes
            if (mc.screen instanceof CommandScreen) {
                mc.setScreen(null);
            }
            leave();
        }
        if (mc.level != null && mc.level.getGameTime() % 10 == 0) {
            // the men who got to their places, and those who are gone
            DESTS.entrySet().removeIf(e -> !(mc.level.getEntity(e.getKey()) instanceof Soldier s) || !s.isAlive()
                    || s.distanceToSqr(e.getValue().x, s.getY(), e.getValue().z) < 1.3 * 1.3);
            long now = System.currentTimeMillis();
            ROUTES.entrySet().removeIf(e -> !(mc.level.getEntity(e.getKey()) instanceof Soldier s) || !s.isAlive()
                    || !DESTS.containsKey(e.getKey()) && now - e.getValue().at() > 4000);
        }
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active || camera == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1.0E9);
        lastNanos = now;
        if (mc.screen instanceof CommandScreen screen && !Screen.hasControlDown() && !Screen.hasAltDown()) {
            long window = mc.getWindow().getWindow();
            double forward = (down(window, mc.options.keyUp) || InputConstants.isKeyDown(window, InputConstants.KEY_UP) ? 1 : 0)
                    - (down(window, mc.options.keyDown) || InputConstants.isKeyDown(window, InputConstants.KEY_DOWN) ? 1 : 0);
            double strafe = (down(window, mc.options.keyRight) || InputConstants.isKeyDown(window, InputConstants.KEY_RIGHT) ? 1 : 0)
                    - (down(window, mc.options.keyLeft) || InputConstants.isKeyDown(window, InputConstants.KEY_LEFT) ? 1 : 0);
            // the mouse at the edge of the window pushes the camera that way
            if (mc.isWindowActive() && !screen.dragging()) {
                double mx = mc.mouseHandler.xpos();
                double my = mc.mouseHandler.ypos();
                int w = mc.getWindow().getScreenWidth();
                int h = mc.getWindow().getScreenHeight();
                if (mx >= 0 && my >= 0 && mx < w && my < h) {
                    strafe += mx <= EDGE ? -1 : mx >= w - 1 - EDGE ? 1 : 0;
                    forward += my <= EDGE ? 1 : my >= h - 1 - EDGE ? -1 : 0;
                }
            }
            double turn = (InputConstants.isKeyDown(window, InputConstants.KEY_E) ? 1 : 0) - (InputConstants.isKeyDown(window, InputConstants.KEY_Q) ? 1 : 0);
            double above = Math.max(2, ty - groundAt(player, tx, tz));
            double speed = SPEED * (Screen.hasShiftDown() || down(window, mc.options.keySprint) ? 2.5 : 1) * Mth.clamp(above / 18, 0.5, 3.5);
            double fx = -Mth.sin(tyaw * Mth.DEG_TO_RAD);
            double fz = Mth.cos(tyaw * Mth.DEG_TO_RAD);
            double len = Math.sqrt(forward * forward + strafe * strafe);
            if (len > 1) {
                forward /= len;
                strafe /= len;
            }
            tx += (fx * forward - fz * strafe) * speed * dt;
            tz += (fz * forward + fx * strafe) * speed * dt;
            tyaw += (float) (turn * 100 * dt);
        }
        clamp(player);
        // glide to where the camera is going
        double k = 1 - Math.exp(-dt * 14);
        x += (tx - x) * k;
        y += (ty - y) * k;
        z += (tz - z) * k;
        yaw += Mth.wrapDegrees(tyaw - yaw) * (float) k;
        pitch += (tpitch - pitch) * (float) k;
        camera.moveTo(x, y, z, yaw, pitch);
    }

    private static boolean down(long window, KeyMapping key) {
        InputConstants.Key k = key.getKey();
        return k.getType() == InputConstants.Type.KEYSYM && k.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, k.getValue());
    }

    private static double groundAt(LocalPlayer player, double gx, double gz) {
        if (player.level().hasChunkAt(BlockPos.containing(gx, player.getY(), gz))) {
            return player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(gx), Mth.floor(gz));
        }
        return player.getY();
    }

    private static void clamp(LocalPlayer player) {
        double dx = tx - player.getX();
        double dz = tz - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > REACH) {
            tx = player.getX() + dx / d * REACH;
            tz = player.getZ() + dz / d * REACH;
        }
        double ground = groundAt(player, tx, tz);
        // never into the ground, nor out of sight of it
        ty = Mth.clamp(ty, ground + 2.5, Math.max(ground, player.getY()) + HEIGHT);
        tpitch = Mth.clamp(tpitch, 15, 89);
    }

    /** Turns the camera (the middle mouse button dragged). */
    static void turn(double dyaw, double dpitch) {
        tyaw += (float) dyaw;
        tpitch += (float) dpitch;
    }

    /** Down to the ground or up from it, along the way the camera looks (the mouse wheel). */
    static void zoom(double amount) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        // the higher up, the bigger the step
        Vec3 look = Vec3.directionFromRotation(tpitch, tyaw);
        double step = Mth.clamp((ty - groundAt(player, tx, tz)) * 0.15, 1.5, 10);
        tx += look.x * amount * step;
        ty += look.y * amount * step;
        tz += look.z * amount * step;
    }

    /** Takes the camera over {@code at}, looking down at it as it looks now. */
    static void focus(Vec3 at) {
        double up = Mth.clamp(ty - at.y, 8, 40);
        double back = up / Math.tan(Math.max(15, tpitch) * Mth.DEG_TO_RAD);
        tx = at.x + Mth.sin(tyaw * Mth.DEG_TO_RAD) * back;
        tz = at.z - Mth.cos(tyaw * Mth.DEG_TO_RAD) * back;
        ty = at.y + up;
    }

    static Vec3 middle(List<? extends Entity> men) {
        Vec3 sum = Vec3.ZERO;
        for (Entity e : men) {
            sum = sum.add(e.position());
        }
        return men.isEmpty() ? sum : sum.scale(1.0 / men.size());
    }

    // ---------------------------------------------------------------- mouse to world, world to mouse

    /** What is under the mouse: the place, a creature, or else a boat or ship. */
    record Pick(@Nullable Vec3 point, @Nullable LivingEntity entity, @Nullable Entity vessel) {
    }

    @Nullable
    static Pick pick(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!haveMatrices || mc.level == null) {
            return null;
        }
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        float nx = (float) (2 * mouseX / w - 1);
        float ny = (float) (1 - 2 * mouseY / h);
        Vector4f near = new Vector4f(nx, ny, -1, 1).mul(INVERSE);
        Vector4f far = new Vector4f(nx, ny, 1, 1).mul(INVERSE);
        Vec3 a = cameraPos.add(near.x / near.w, near.y / near.w, near.z / near.w);
        Vec3 b = cameraPos.add(far.x / far.w, far.y / far.w, far.z / far.w);
        Vec3 dir = b.subtract(a).normalize();
        Vec3 end = a.add(dir.scale(REACH * 2.5));
        Entity viewer = camera != null ? camera : mc.player;
        BlockHitResult block = mc.level.clip(new ClipContext(a, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, viewer));
        double reach = block.getType() == HitResult.Type.MISS ? REACH * 2.5 : block.getLocation().distanceTo(a);
        LivingEntity best = null;
        double bestDist = reach;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && living.isAlive() && e != mc.player && !e.isSpectator()) {
                // men are small from above: a little more room around them to hit
                AABB box = e.getBoundingBox().inflate(0.35, 0.2, 0.35);
                var hit = box.clip(a, end);
                if (hit.isPresent()) {
                    double d = hit.get().distanceTo(a);
                    if (d < bestDist) {
                        bestDist = d;
                        best = living;
                    }
                }
            }
        }
        LivingEntity rider = best == null ? null : best.getControllingPassenger();
        if (rider != null && mine(rider)) {
            // a horse or an elephant of his: its rider is meant
            best = rider;
        }
        Vec3 point = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        if (best != null) {
            point = best.getVehicle() != null ? best.getVehicle().position() : best.position();
        }
        Entity vessel = null;
        if (best == null) {
            double nearest = reach;
            for (Entity e : mc.level.entitiesForRendering()) {
                Entity ship = Vessels.resolve(e);
                if (Vessels.isVessel(ship)) {
                    var hit = e.getBoundingBox().inflate(0.2).clip(a, end);
                    if (hit.isPresent() && hit.get().distanceTo(a) < nearest) {
                        nearest = hit.get().distanceTo(a);
                        vessel = ship;
                    }
                }
            }
        }
        return new Pick(point, best, vessel);
    }

    /** Where {@code world} is on the screen, in GUI pixels, or null when it is behind the camera. */
    @Nullable
    static double[] project(Vec3 world) {
        Minecraft mc = Minecraft.getInstance();
        if (!haveMatrices) {
            return null;
        }
        Vec3 rel = world.subtract(cameraPos);
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1).mul(VIEW_PROJ);
        if (clip.w <= 0.05f) {
            return null;
        }
        double w = mc.getWindow().getGuiScaledWidth();
        double h = mc.getWindow().getGuiScaledHeight();
        return new double[]{(clip.x / clip.w + 1) / 2 * w, (1 - clip.y / clip.w) / 2 * h};
    }

    /** The way the camera looks, flat on the ground (Minecraft's yaw). */
    static float cameraYaw() {
        return yaw;
    }

    /** The marches of the men picked out: each route once, with the men on it. */
    static Map<Route, List<Soldier>> routes() {
        Map<Route, List<Soldier>> out = new IdentityHashMap<>();
        for (Soldier s : selected()) {
            Route r = ROUTES.get(s.getId());
            if (r != null) {
                out.computeIfAbsent(r, k -> new ArrayList<>()).add(s);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
            VIEW_PROJ.set(event.getProjectionMatrix()).mul(view);
            INVERSE.set(VIEW_PROJ).invert();
            cameraPos = event.getCamera().getPosition();
            haveMatrices = true;
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.player == null || mc.level == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick();

        // the commander himself, where he stands
        LocalPlayer player = mc.player;
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());
        mc.getEntityRenderDispatcher().render(player, px - cam.x, py - cam.y, pz - cam.z, Mth.lerp(partial, player.yRotO, player.getYRot()), partial,
                pose, buffers, mc.getEntityRenderDispatcher().getPackedLightCoords(player, partial));
        buffers.endBatch();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        // the commander's own ring
        ring(pose, lines, px, py + 0.05, pz, 0.8, 1.0f, 0.8f, 0.3f, 0.9f);
        List<Soldier> picked = selected();
        for (Soldier s : picked) {
            Vec3 at = s.getPosition(partial);
            double r = s.steed() != null ? Math.max(1.0, s.steed().getBbWidth() * 0.7) : 0.55;
            if (s.operating() != null) {
                // the engine he works is what is ringed, and the place it batters
                at = s.operating().getPosition(partial);
                net.minecraft.core.BlockPos point = s.operating().siegePoint();
                if (point != null) {
                    Vec3 p = Vec3.atBottomCenterOf(point);
                    cross(pose, lines, p.x, p.y + 1.02, p.z, 0.6, 1.0f, 0.5f, 0.15f, 1.0f);
                    ring(pose, lines, p.x, p.y + 1.02, p.z, 0.9, 1.0f, 0.5f, 0.15f, 1.0f);
                    line(pose, lines, at.add(0, 0.2, 0), p.add(0, 1.1, 0), 1.0f, 0.5f, 0.15f, 0.5f);
                }
            }
            ring(pose, lines, at.x, at.y + 0.05, at.z, r, 0.35f, 1.0f, 0.35f, 1.0f);
            Vec3 dest = DESTS.get(s.getId());
            if (dest != null) {
                // where he is going
                line(pose, lines, at.add(0, 0.1, 0), dest.add(0, 0.1, 0), 0.55f, 0.95f, 0.55f, 0.45f);
                cross(pose, lines, dest.x, dest.y + 0.08, dest.z, 0.25, 0.55f, 1.0f, 0.55f, 0.9f);
            }
        }
        // the stages of the marches: from the men through every place, a ring at each
        for (Map.Entry<Route, List<Soldier>> e : routes().entrySet()) {
            Vec3 from = middle(e.getValue()).add(0, 0.15, 0);
            int i = 0;
            for (Vec3 p : e.getKey().points()) {
                Vec3 to = p.add(0, 0.15, 0);
                float fade = i == 0 ? 1.0f : 0.7f;
                line(pose, lines, from, to, 1.0f, 0.8f * fade, 0.25f, 0.8f);
                arrow(pose, lines, from, to, 1.0f, 0.8f * fade, 0.25f, 0.9f);
                ring(pose, lines, to.x, to.y, to.z, 0.9, 1.0f, 0.8f * fade, 0.25f, 0.9f);
                from = to;
                i++;
            }
        }
        // the enemies in sight: red rings under them
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && living.isAlive() && hostile(e) && e.distanceToSqr(cam) < 128 * 128) {
                Vec3 at = e.getPosition(partial);
                double r = e.getVehicle() != null ? 1.0 : 0.6;
                ring(pose, lines, at.x, (e.getVehicle() != null ? e.getVehicle().getY() : at.y) + 0.06, at.z, r, 1.0f, 0.2f, 0.15f, 0.85f);
            }
        }
        if (hovered != null && hovered.isAlive()) {
            Vec3 at = hovered.getPosition(partial);
            boolean friend = mine(hovered) || hovered.isAlliedTo(player);
            ring(pose, lines, at.x, at.y + 0.07, at.z, 0.75, 1.0f, friend ? 1.0f : 0.25f, friend ? 1.0f : 0.2f, 1.0f);
        }
        if (hoveredVessel != null && hoveredVessel.isAlive()) {
            AABB box = hoveredVessel.getBoundingBox();
            ring(pose, lines, box.getCenter().x, box.minY + 0.6, box.getCenter().z, Math.max(box.getXsize(), box.getZsize()) * 0.6,
                    0.4f, 0.75f, 1.0f, 1.0f);
        }
        for (Vec3 v : preview) {
            ring(pose, lines, v.x, v.y + 0.1, v.z, 0.3, 1.0f, 0.85f, 0.3f, 1.0f);
        }
        if (previewFrom != null && previewTo != null) {
            line(pose, lines, previewFrom.add(0, 0.12, 0), previewTo.add(0, 0.12, 0), 1.0f, 0.85f, 0.3f, 1.0f);
        }
        long now = System.currentTimeMillis();
        if (now < flashUntil) {
            float a = Math.min(1, (flashUntil - now) / 600f);
            for (Vec3 v : flash) {
                ring(pose, lines, v.x, v.y + 0.1, v.z, 0.35, 0.35f, 1.0f, 0.35f, a);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void ring(PoseStack pose, VertexConsumer vc, double cx, double cy, double cz, double r, float red, float green, float blue, float alpha) {
        int n = r > 0.8 ? 28 : 18;
        for (int i = 0; i < n; i++) {
            double a0 = Mth.TWO_PI * i / n;
            double a1 = Mth.TWO_PI * (i + 1) / n;
            line(pose, vc, new Vec3(cx + Math.cos(a0) * r, cy, cz + Math.sin(a0) * r), new Vec3(cx + Math.cos(a1) * r, cy, cz + Math.sin(a1) * r),
                    red, green, blue, alpha);
        }
    }

    private static void cross(PoseStack pose, VertexConsumer vc, double cx, double cy, double cz, double r, float red, float green, float blue, float alpha) {
        line(pose, vc, new Vec3(cx - r, cy, cz - r), new Vec3(cx + r, cy, cz + r), red, green, blue, alpha);
        line(pose, vc, new Vec3(cx - r, cy, cz + r), new Vec3(cx + r, cy, cz - r), red, green, blue, alpha);
    }

    /** A chevron halfway along a stage, pointing the way. */
    private static void arrow(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float red, float green, float blue, float alpha) {
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        if (d.lengthSqr() < 4) {
            return;
        }
        d = d.normalize();
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        Vec3 tail = mid.subtract(d.scale(0.8));
        line(pose, vc, tail.add(side.scale(0.6)), mid, red, green, blue, alpha);
        line(pose, vc, tail.subtract(side.scale(0.6)), mid, red, green, blue, alpha);
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float red, float green, float blue, float alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 d = b.subtract(a);
        d = d.lengthSqr() < 1.0E-8 ? new Vec3(0, 1, 0) : d.normalize();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(red, green, blue, alpha).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(red, green, blue, alpha).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
    }

    /** No hand in front of the commander's view. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHand(RenderHandEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    /** No hotbar, crosshair, hearts or stamina bar over the field: only the chat and the lines over the hotbar. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!active) {
            return;
        }
        var id = event.getOverlay().id();
        if (!id.equals(VanillaGuiOverlay.CHAT_PANEL.id()) && !id.equals(VanillaGuiOverlay.RECORD_OVERLAY.id())
                && !id.equals(VanillaGuiOverlay.TITLE_TEXT.id()) && !id.equals(VanillaGuiOverlay.SUBTITLES.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        leave();
        SELECTED.clear();
        GROUPS.clear();
        DESTS.clear();
        ROUTES.clear();
        HOSTILE.clear();
    }
}
