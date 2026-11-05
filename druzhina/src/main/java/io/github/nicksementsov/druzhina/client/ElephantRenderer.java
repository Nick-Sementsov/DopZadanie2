package io.github.nicksementsov.druzhina.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ElephantRenderer extends MobRenderer<WarElephant, ElephantModel> {
    private static final ResourceLocation TEXTURE = io.github.nicksementsov.druzhina.client.mesh.ElephantMesh.TEXTURE;

    public ElephantRenderer(EntityRendererProvider.Context context) {
        super(context, new ElephantModel(context.bakeLayer(ElephantModel.LAYER)), 1.6f);
    }

    @Override
    protected void scale(WarElephant entity, PoseStack pose, float partialTick) {
        pose.scale(WarElephant.SCALE, WarElephant.SCALE, WarElephant.SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(WarElephant entity) {
        return TEXTURE;
    }
}
