package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.Formation;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
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
 * The commander's view: the camera leaves the player and rises over the field, the mouse picks out soldiers and
 * points where they are to go or whom they are to strike (see {@link CommandScreen}). The player stays where he
 * stood and is drawn there.
 */
@Mod.EventBusSubscriber(modid = Druzhina.MODID, value = Dist.CLIENT)
public final class CommandView {
    /** How far the camera may go from the player, and how high above him. */
    static final double REACH = 96;
    static final double HEIGHT = 64;
    private static final double SPEED = 14;

    private static boolean active;
    @Nullable
    private static Marker camera;
    @Nullable
    private static CameraType savedType;
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    private static float pitch;
    private static long lastNanos;

    /** The men picked out, by entity id; kept between visits to the view. */
    static final Set<Integer> SELECTED = new LinkedHashSet<>();
    static Formation formation = Formation.LINE;

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
        yaw = player.getYRot();
        pitch = 52;
        // behind the player and well above him, looking down at where he looks
        double back = 8;
        x = player.getX() + Mth.sin(yaw * Mth.DEG_TO_RAD) * back;
        z = player.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * back;
        y = player.getY() + 11;
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
    }

    /** One of the player's own men whom he may lead (not one kept by a realm's garrison). */
    static boolean mine(Entity e) {
        LocalPlayer player = Minecraft.getInstance().player;
        return e instanceof Soldier s && s.isAlive() && player != null && player.getUUID().equals(s.getOwnerUUID()) && !s.managed();
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
        if (mc.screen instanceof CommandScreen) {
            long window = mc.getWindow().getWindow();
            double forward = (down(window, mc.options.keyUp) ? 1 : 0) - (down(window, mc.options.keyDown) ? 1 : 0);
            double strafe = (down(window, mc.options.keyRight) ? 1 : 0) - (down(window, mc.options.keyLeft) ? 1 : 0);
            double up = (down(window, mc.options.keyJump) ? 1 : 0) - (down(window, mc.options.keyShift) ? 1 : 0);
            double turn = (InputConstants.isKeyDown(window, InputConstants.KEY_E) ? 1 : 0) - (InputConstants.isKeyDown(window, InputConstants.KEY_Q) ? 1 : 0);
            double speed = SPEED * (down(window, mc.options.keySprint) ? 2.5 : 1) * (0.6 + 0.4 * Math.max(0, y - player.getY()) / 20);
            double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
            double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
            x += (fx * forward - fz * strafe) * speed * dt;
            z += (fz * forward + fx * strafe) * speed * dt;
            y += up * speed * 0.8 * dt;
            yaw += (float) (turn * 90 * dt);
        }
        clamp(player);
        camera.moveTo(x, y, z, yaw, pitch);
    }

    private static boolean down(long window, KeyMapping key) {
        InputConstants.Key k = key.getKey();
        return k.getType() == InputConstants.Type.KEYSYM && k.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, k.getValue());
    }

    private static void clamp(LocalPlayer player) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > REACH) {
            x = player.getX() + dx / d * REACH;
            z = player.getZ() + dz / d * REACH;
        }
        y = Mth.clamp(y, player.getY() - 8, player.getY() + HEIGHT);
        // never into the ground
        if (player.level().hasChunkAt(BlockPos.containing(x, y, z))) {
            y = Math.max(y, player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 1.5);
        }
        pitch = Mth.clamp(pitch, 10, 89);
    }

    /** Turns the camera (the middle mouse button dragged). */
    static void turn(double dyaw, double dpitch) {
        yaw += (float) dyaw;
        pitch += (float) dpitch;
    }

    /** Closer to the ground or further from it, along the way the camera looks (the mouse wheel). */
    static void zoom(double amount) {
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        x += look.x * amount * 3;
        y += look.y * amount * 3;
        z += look.z * amount * 3;
    }

    // ---------------------------------------------------------------- mouse to world, world to mouse

    /** The point under the mouse and the entity there, if any: what the commander points at. */
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
        Vec3 end = a.add(dir.scale(REACH * 2));
        Entity viewer = camera != null ? camera : mc.player;
        BlockHitResult block = mc.level.clip(new ClipContext(a, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, viewer));
        double reach = block.getType() == HitResult.Type.MISS ? REACH * 2 : block.getLocation().distanceTo(a);
        LivingEntity best = null;
        double bestDist = reach;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && living.isAlive() && e != mc.player && !e.isSpectator()) {
                AABB box = e.getBoundingBox().inflate(0.2);
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
        Vec3 point = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        if (best != null) {
            point = best.position();
        }
        Entity vessel = null;
        if (best == null) {
            double nearest = reach;
            for (Entity e : mc.level.entitiesForRendering()) {
                Entity ship = io.github.nicksementsov.druzhina.compat.Vessels.resolve(e);
                if (io.github.nicksementsov.druzhina.compat.Vessels.isVessel(ship)) {
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
        for (Soldier s : selected()) {
            Vec3 at = s.getPosition(partial);
            ring(pose, lines, at.x, at.y + 0.05, at.z, 0.55, 0.35f, 1.0f, 0.35f, 1.0f);
        }
        if (hovered != null && hovered.isAlive()) {
            Vec3 at = hovered.getPosition(partial);
            boolean friend = mine(hovered) || hovered.isAlliedTo(player);
            ring(pose, lines, at.x, at.y + 0.07, at.z, 0.7, friend ? 1.0f : 1.0f, friend ? 1.0f : 0.25f, friend ? 1.0f : 0.2f, 1.0f);
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
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = Mth.TWO_PI * i / n;
            double a1 = Mth.TWO_PI * (i + 1) / n;
            line(pose, vc, new Vec3(cx + Math.cos(a0) * r, cy, cz + Math.sin(a0) * r), new Vec3(cx + Math.cos(a1) * r, cy, cz + Math.sin(a1) * r),
                    red, green, blue, alpha);
        }
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float red, float green, float blue, float alpha) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        Vec3 d = b.subtract(a).normalize();
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
    }
}
