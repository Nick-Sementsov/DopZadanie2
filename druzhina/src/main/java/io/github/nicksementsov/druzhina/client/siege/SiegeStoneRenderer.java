package io.github.nicksementsov.druzhina.client.siege;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/** Something hurled by an engine, tumbling as it flies: a stone, an iron ball, a clay pot of fire. */
public class SiegeStoneRenderer<T extends Entity> extends EntityRenderer<T> {
    private final BlockRenderDispatcher blocks;
    private final BlockState look;
    private final float size;

    public SiegeStoneRenderer(EntityRendererProvider.Context context, BlockState look, float size) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.look = look;
        this.size = size;
        this.shadowRadius = size * 0.5f;
    }

    @Override
    public void render(T stone, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        float spin = (stone.tickCount + partialTick) * 9;
        pose.translate(0, this.size * 0.5, 0);
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.6f));
        pose.scale(this.size, this.size, this.size);
        pose.translate(-0.5, -0.5, -0.5);
        this.blocks.renderSingleBlock(this.look, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(stone, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(T stone) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
