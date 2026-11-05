package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws a siege engine with its model; they all share one texture. */
public class SiegeRenderer<T extends SiegeEngine, M extends EntityModel<T>> extends MobRenderer<T, M> {
    public SiegeRenderer(EntityRendererProvider.Context context, M model, float shadow) {
        super(context, model, shadow);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return SiegeTexture.LOCATION;
    }

    @Override
    protected boolean shouldShowName(T entity) {
        return entity.hasCustomName() && super.shouldShowName(entity);
    }

    @Override
    protected float getFlipDegrees(T entity) {
        // it does not fall on its side like a beast: it sinks and breaks up
        return 0;
    }

    public static SiegeRenderer<io.github.nicksementsov.druzhina.entity.siege.Trebuchet, TrebuchetModel> trebuchet(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new TrebuchetModel(c.bakeLayer(TrebuchetModel.LAYER)), 1.6f);
    }

    public static SiegeRenderer<io.github.nicksementsov.druzhina.entity.siege.Ballista, BallistaModel> ballista(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new BallistaModel(c.bakeLayer(BallistaModel.LAYER)), 1.0f);
    }

    public static SiegeRenderer<io.github.nicksementsov.druzhina.entity.siege.Ram, RamModel> ram(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new RamModel(c.bakeLayer(RamModel.LAYER)), 1.4f);
    }
}
