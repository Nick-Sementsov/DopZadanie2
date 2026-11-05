package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import io.github.nicksementsov.druzhina.server.Corpses;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How the slain fall: instead of Minecraft's stiff tip onto the side, a man drops on his face or on his back, or
 * twists down onto his side, falling faster as he goes and settling with a little bounce; a beast rolls onto its side.
 * The body then lies still (see {@link Corpses}: on the field of battle it lies for minutes), no longer tinted red, and
 * sinks into the ground in its last moments.
 */
@Mod.EventBusSubscriber(modid = Druzhina.MODID, value = Dist.CLIENT)
public final class DeathPoses {
    /** Ticks of the fall. */
    public static final float FALL = 16;
    /** Ticks of the first moment, when the knees give way and he sinks. */
    public static final float KNEEL = 5;
    /** Ticks of the settling after it. */
    private static final float SETTLE = 8;
    /** Ticks of the sinking into the ground at the end. */
    private static final float SINK = 40;

    /** How a body falls. */
    public enum Fall { FACE, BACK, LEFT, RIGHT }

    /** What was changed on an entity for its drawing, to be put back after. */
    private record Saved(LivingEntity entity, int deathTime, int hurtTime, float since) {
    }

    private static final Deque<Saved> SAVED = new ArrayDeque<>();

    private DeathPoses() {
    }

    /** Whether the fall of {@code e} is drawn here. */
    private static boolean applies(LivingEntity e) {
        return !(e instanceof Player) && !(e instanceof SiegeEngine) && !(e instanceof WitherBoss);
    }

    /** How {@code e} falls: a man (taller than wide) on his face, his back or a side; a beast on a side. */
    public static Fall fall(LivingEntity e) {
        EntityDimensions size = e.getType().getDimensions();
        int pick = Math.floorMod(e.getUUID().hashCode() * 31 + 7, 100);
        if (size.height > size.width * 1.4f) {
            return pick < 38 ? Fall.FACE : pick < 76 ? Fall.BACK : pick < 88 ? Fall.LEFT : Fall.RIGHT;
        }
        return pick < 50 ? Fall.LEFT : Fall.RIGHT;
    }

    /** Ticks since {@code e} fell (its count of the dead, while it is being drawn here), or -1 when it lives. */
    public static float since(LivingEntity e, float partialTick) {
        Saved top = SAVED.peek();
        if (top != null && top.entity() == e) {
            return top.since();
        }
        return e.deathTime > 0 && e.isDeadOrDying() ? e.deathTime + partialTick - 1 : -1;
    }

    /** 0 standing, 1 lying (with a little bounce past it as the body settles). */
    public static float lying(float since) {
        if (since < KNEEL) {
            // the knees give: he leans a little as he sinks
            return 0.1f * since / KNEEL;
        }
        if (since < FALL) {
            float k = (since - KNEEL) / (FALL - KNEEL);
            return 0.1f + 0.9f * k * k;
        }
        float k = (since - FALL) / SETTLE;
        return k >= 1 ? 1 : 1 - 0.08f * Mth.sin(k * Mth.PI) * (1 - k);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (event.isCanceled() || e.deathTime <= 0 || !e.isDeadOrDying() || !applies(e)) {
            return;
        }
        float partial = event.getPartialTick();
        float since = e.deathTime + partial - 1;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float yaw = 180 - Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        EntityDimensions size = e.getType().getDimensions();
        float lie = lying(since);
        float angle = 90 * lie;
        boolean man = size.height > size.width * 1.4f;
        // he sinks as his knees give, and is down on the ground once he lies
        float kneel = Mth.clamp(since / KNEEL, 0, 1);
        float sink = (man ? 0.35f : size.height * 0.12f) * kneel * Mth.clamp(1 - lie * 1.1f, 0, 1);
        // and the body twists as it goes over
        float twist = ((e.getId() & 1) == 0 ? 1 : -1) * (man ? 22 : 8) * Mth.clamp(since / FALL, 0, 1);
        // its last moments: it sinks into the ground
        float left = Corpses.ticks(e) - since;
        if (left < SINK && since > FALL + SETTLE) {
            pose.translate(0, -Math.min(size.height, size.width) * 0.6f * (1 - Math.max(0, left) / SINK), 0);
        }
        // turned into its own frame (forward is -z there), it falls about the edge it goes over
        pose.translate(0, -sink, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw + twist));
        switch (fall(e)) {
            case FACE -> {
                float edge = Math.min(0.2f, size.width * 0.35f);
                pose.translate(0, 0, -edge);
                pose.mulPose(Axis.XP.rotationDegrees(-angle));
                pose.translate(0, 0, edge);
            }
            case BACK -> {
                float edge = Math.min(0.2f, size.width * 0.35f);
                pose.translate(0, 0, edge);
                pose.mulPose(Axis.XP.rotationDegrees(angle));
                pose.translate(0, 0, -edge);
            }
            case LEFT, RIGHT -> {
                float side = fall(e) == Fall.LEFT ? 1 : -1;
                float edge = size.width * 0.45f;
                pose.translate(-side * edge, 0, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(side * angle));
                pose.translate(side * edge, 0, 0);
            }
        }
        pose.mulPose(Axis.YP.rotationDegrees(-yaw - twist));
        // Minecraft must not tip it over again, nor keep it red once it lies
        SAVED.push(new Saved(e, e.deathTime, e.hurtTime, since));
        e.deathTime = 0;
        e.hurtTime = since < 6 ? Math.max(1, e.hurtTime) : 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        Saved top = SAVED.peek();
        if (top != null && top.entity() == event.getEntity()) {
            SAVED.pop();
            top.entity().deathTime = top.deathTime();
            top.entity().hurtTime = top.hurtTime();
            event.getPoseStack().popPose();
        }
    }

    /** The dead bear no names over them. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof LivingEntity e && !(e instanceof Player) && e.isDeadOrDying()) {
            event.setResult(Event.Result.DENY);
        }
    }
}
