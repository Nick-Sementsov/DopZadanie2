package io.github.nicksementsov.druzhina.client.siege;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.druzhina.entity.siege.BallistaBolt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** A ballista's bolt: an arrow twice the size. */
public class BoltRenderer extends ArrowRenderer<BallistaBolt> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/projectiles/arrow.png");

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BallistaBolt bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(2.0f, 2.0f, 2.0f);
        super.render(bolt, yaw, partialTick, pose, buffers, light);
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(BallistaBolt bolt) {
        return TEXTURE;
    }
}
