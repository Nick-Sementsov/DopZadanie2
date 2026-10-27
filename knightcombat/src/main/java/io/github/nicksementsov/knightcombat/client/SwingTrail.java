package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A short fading ribbon behind the blade of a slash or an overhead. The ribbon is not recorded from the frames
 * drawn so far but built from the swing itself: the poses of the last part of the release are evaluated again,
 * so it is just as smooth at 20 frames per second as at 200.
 */
final class SwingTrail {
    /** Number of segments of the ribbon. */
    static final int STEPS = 12;
    /** How much of the release the ribbon reaches back. */
    private static final float WINDOW = 0.4f;
    /** How long the ribbon fades after the release ends, in ticks. */
    private static final float FADE_TICKS = 3;

    /**
     * Range of release progress the ribbon covers and how visible it is, as {from, to, visibility},
     * or {@code null} when the fighter is not swinging.
     */
    static float[] range(FighterAnim anim, float partialTick) {
        if (anim == null || anim.attack == AttackType.STAB || anim.attack == AttackType.KICK) {
            return null;
        }
        if (anim.phase == Phase.RELEASE) {
            float p = anim.progress(partialTick);
            return p <= 0.02f ? null : new float[]{Math.max(0, p - WINDOW), p, 1};
        }
        if (anim.phase == Phase.RECOVERY && !anim.has(CombatState.REBOUND) && !anim.has(CombatState.FEINT)) {
            // The swing went all the way through: its end fades out.
            float elapsed = anim.elapsed(partialTick);
            if (elapsed < FADE_TICKS) {
                return new float[]{1 - WINDOW, 1, 1 - elapsed / FADE_TICKS};
            }
        }
        return null;
    }

    /** Progress of step {@code k} (0 the oldest, {@link #STEPS} the newest) in a range from {@link #range}. */
    static float progress(float[] range, int k) {
        return range[0] + (range[1] - range[0]) * k / STEPS;
    }

    /**
     * Draws the ribbon between the hilt side ({@code bases}) and the tip side ({@code tips}) of the blade at each step.
     * It fades towards the hilt and towards the oldest step.
     */
    static void draw(PoseStack poseStack, MultiBufferSource buffers, Vector3f[] bases, Vector3f[] tips, int rgb, float opacity) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        VertexConsumer consumer = buffers.getBuffer(TrailRenderType.TRAIL);
        Matrix4f pose = poseStack.last().pose();
        for (int k = 0; k < bases.length - 1; k++) {
            int alphaOld = alpha(k, bases.length, opacity);
            int alphaNew = alpha(k + 1, bases.length, opacity);
            consumer.vertex(pose, bases[k].x, bases[k].y, bases[k].z).color(r, g, b, 0).endVertex();
            consumer.vertex(pose, tips[k].x, tips[k].y, tips[k].z).color(r, g, b, alphaOld).endVertex();
            consumer.vertex(pose, tips[k + 1].x, tips[k + 1].y, tips[k + 1].z).color(r, g, b, alphaNew).endVertex();
            consumer.vertex(pose, bases[k + 1].x, bases[k + 1].y, bases[k + 1].z).color(r, g, b, 0).endVertex();
        }
    }

    private static int alpha(int k, int count, float opacity) {
        float t = k / (float) (count - 1);
        return (int) (255 * opacity * t * t);
    }

    /** Untextured, see-through, seen from both sides, and not hiding what is drawn behind it later. */
    private static final class TrailRenderType extends RenderType {
        static final RenderType TRAIL = create("knightcombat_trail", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1024, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private TrailRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
                                boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }
}
