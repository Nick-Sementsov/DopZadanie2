package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.nicksementsov.druzhina.entity.Soldier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The parts a killing blow cut off a soldier: they fly off from where they were, turning, and come to lie on the
 * ground about his body, the blood spraying from the wounds as they go.
 */
final class Severed {
    /** Ticks of their flight. */
    private static final float FLIGHT = 9;
    private static final Map<Soldier, Boolean> BLED = Collections.synchronizedMap(new WeakHashMap<>());

    private Severed() {
    }

    static void render(Soldier s, PlayerModel<Soldier> model, ResourceLocation texture, int wounds, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float since = Math.max(0, DeathPoses.since(s, partial));
        RandomSource rnd = RandomSource.create(s.getUUID().getLeastSignificantBits());
        // the way the body falls: its head goes that way, its right side is across
        DeathPoses.Fall fall = DeathPoses.fall(s);
        float ax = 0;
        float az = 0;
        switch (fall) {
            case FACE -> az = -1;
            case BACK -> az = 1;
            case LEFT -> ax = -1;
            case RIGHT -> ax = 1;
        }
        float lx = -az;
        float lz = ax;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        int overlay = OverlayTexture.pack(0, since < 6);
        float yaw = 180 - Mth.rotLerp(partial, s.yBodyRotO, s.yBodyRot);
        float k = Mth.clamp(since / FLIGHT, 0, 1);
        bleed(s, wounds, since, yaw);
        // each part: where it was on the standing man, where it comes to lie (along the body, across it), how it lies
        if ((wounds & Soldier.HEAD) != 0) {
            Vec3 from = new Vec3(0, 1.55, 0);
            Vec3 to = new Vec3(ax * (1.9f + rnd.nextFloat() * 0.5f) + lx * (rnd.nextFloat() - 0.5f), 0.23, az * (1.9f + rnd.nextFloat() * 0.5f) + lz * (rnd.nextFloat() - 0.5f));
            part(pose, vc, light, overlay, yaw, k, from, to, rnd, new ModelPart[]{model.head, model.hat}, -4, 90);
        }
        if ((wounds & Soldier.RIGHT_ARM) != 0) {
            Vec3 from = new Vec3(-0.35, 1.35, 0);
            Vec3 to = new Vec3(ax * 1.1f + lx * (0.75f + rnd.nextFloat() * 0.4f), 0.12, az * 1.1f + lz * (0.75f + rnd.nextFloat() * 0.4f));
            part(pose, vc, light, overlay, yaw, k, from, to, rnd, new ModelPart[]{model.rightArm, model.rightSleeve}, 4, 90);
        }
        if ((wounds & Soldier.LEFT_ARM) != 0) {
            Vec3 from = new Vec3(0.35, 1.35, 0);
            Vec3 to = new Vec3(ax * 1.0f - lx * (0.75f + rnd.nextFloat() * 0.4f), 0.12, az * 1.0f - lz * (0.75f + rnd.nextFloat() * 0.4f));
            part(pose, vc, light, overlay, yaw, k, from, to, rnd, new ModelPart[]{model.leftArm, model.leftSleeve}, 4, 90);
        }
        if ((wounds & Soldier.RIGHT_LEG) != 0) {
            Vec3 from = new Vec3(-0.12, 0.7, 0);
            Vec3 to = new Vec3(-ax * 0.5f + lx * (0.5f + rnd.nextFloat() * 0.3f), 0.12, -az * 0.5f + lz * (0.5f + rnd.nextFloat() * 0.3f));
            part(pose, vc, light, overlay, yaw, k, from, to, rnd, new ModelPart[]{model.rightLeg, model.rightPants}, 6, 90);
        }
        if ((wounds & Soldier.LEFT_LEG) != 0) {
            Vec3 from = new Vec3(0.12, 0.7, 0);
            Vec3 to = new Vec3(-ax * 0.5f - lx * (0.5f + rnd.nextFloat() * 0.3f), 0.12, -az * 0.5f - lz * (0.5f + rnd.nextFloat() * 0.3f));
            part(pose, vc, light, overlay, yaw, k, from, to, rnd, new ModelPart[]{model.leftLeg, model.leftPants}, 6, 90);
        }
    }

    /**
     * Draws one cut-off part flying from {@code from} to {@code to} (in the body's own frame, blocks), turning over as
     * it flies, and lying flat at the end; {@code mid} is its middle along its length in model pixels (from its
     * joint), to turn it about.
     */
    private static void part(PoseStack pose, VertexConsumer vc, int light, int overlay, float yaw, float k, Vec3 from, Vec3 to,
                             RandomSource rnd, ModelPart[] parts, float mid, float lie) {
        float spin = rnd.nextFloat() * 360;
        float tumble = (rnd.nextBoolean() ? 1 : -1) * (360 + rnd.nextFloat() * 180);
        double x = Mth.lerp(k, from.x, to.x);
        double z = Mth.lerp(k, from.z, to.z);
        double y = Mth.lerp(k, from.y, to.y) + Math.sin(k * Math.PI) * 0.7;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        // tumbling in the air, flat on the ground
        pose.mulPose(Axis.XP.rotationDegrees(lie + tumble * (1 - k)));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0, -mid / 16f, 0);
        for (ModelPart p : parts) {
            if (p == null) {
                continue;
            }
            float px = p.x;
            float py = p.y;
            float pz = p.z;
            float rx = p.xRot;
            float ry = p.yRot;
            float rz = p.zRot;
            boolean shown = p.visible;
            p.x = 0;
            p.y = 0;
            p.z = 0;
            p.xRot = 0;
            p.yRot = 0;
            p.zRot = 0;
            p.visible = true;
            p.render(pose, vc, light, overlay);
            p.x = px;
            p.y = py;
            p.z = pz;
            p.xRot = rx;
            p.yRot = ry;
            p.zRot = rz;
            p.visible = shown;
        }
        pose.popPose();
    }

    /** Blood from the wounds, once, as he falls. */
    private static void bleed(Soldier s, int wounds, float since, float yaw) {
        if (since > 4 || BLED.containsKey(s)) {
            return;
        }
        BLED.put(s, Boolean.TRUE);
        RandomSource rnd = s.getRandom();
        BlockParticleOption gore = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        DustParticleOptions spray = new DustParticleOptions(new Vector3f(0.45f, 0.02f, 0.02f), 1.4f);
        int n = Integer.bitCount(wounds);
        for (int i = 0; i < 18 * n; i++) {
            double y = s.getY() + 0.6 + rnd.nextDouble() * 1.0;
            s.level().addParticle(i % 3 == 0 ? gore : spray, s.getX() + rnd.nextGaussian() * 0.25, y, s.getZ() + rnd.nextGaussian() * 0.25,
                    rnd.nextGaussian() * 0.12, 0.08 + rnd.nextDouble() * 0.15, rnd.nextGaussian() * 0.12);
        }
    }
}
