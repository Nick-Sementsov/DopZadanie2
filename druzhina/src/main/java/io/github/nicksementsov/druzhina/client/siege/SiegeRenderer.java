package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.client.mesh.BallistaMesh;
import io.github.nicksementsov.druzhina.client.mesh.BombardMesh;
import io.github.nicksementsov.druzhina.client.mesh.MangonelMesh;
import io.github.nicksementsov.druzhina.client.mesh.MantletMesh;
import io.github.nicksementsov.druzhina.client.mesh.RamMesh;
import io.github.nicksementsov.druzhina.client.mesh.SiegeTowerMesh;
import io.github.nicksementsov.druzhina.client.mesh.TrebuchetMesh;
import io.github.nicksementsov.druzhina.entity.siege.Ballista;
import io.github.nicksementsov.druzhina.entity.siege.Bombard;
import io.github.nicksementsov.druzhina.entity.siege.Mangonel;
import io.github.nicksementsov.druzhina.entity.siege.Mantlet;
import io.github.nicksementsov.druzhina.entity.siege.Ram;
import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import io.github.nicksementsov.druzhina.entity.siege.SiegeTower;
import io.github.nicksementsov.druzhina.entity.siege.Trebuchet;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws a siege engine with its model and its own texture (both made by {@code tools/models.py}). */
public class SiegeRenderer<T extends SiegeEngine, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation texture;
    private final float size;

    public SiegeRenderer(EntityRendererProvider.Context context, M model, ResourceLocation texture, float size, float shadow) {
        super(context, model, shadow * size);
        this.texture = texture;
        this.size = size;
    }

    /** Engines are drawn larger than their models' pixels: they are machines of many men's height. */
    @Override
    protected void scale(T entity, com.mojang.blaze3d.vertex.PoseStack pose, float partialTick) {
        pose.scale(this.size, this.size, this.size);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
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

    public static SiegeRenderer<Trebuchet, TrebuchetModel> trebuchet(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new TrebuchetModel(c.bakeLayer(TrebuchetModel.LAYER)), TrebuchetMesh.TEXTURE, Trebuchet.SCALE, 1.7f);
    }

    public static SiegeRenderer<Ballista, BallistaModel> ballista(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new BallistaModel(c.bakeLayer(BallistaModel.LAYER)), BallistaMesh.TEXTURE, Ballista.SCALE, 1.1f);
    }

    public static SiegeRenderer<Ram, RamModel> ram(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new RamModel(c.bakeLayer(RamModel.LAYER)), RamMesh.TEXTURE, Ram.SCALE, 1.5f);
    }

    public static SiegeRenderer<Mangonel, MangonelModel> mangonel(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new MangonelModel(c.bakeLayer(MangonelModel.LAYER)), MangonelMesh.TEXTURE, Mangonel.SCALE, 1.2f);
    }

    public static SiegeRenderer<Bombard, BombardModel> bombard(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new BombardModel(c.bakeLayer(BombardModel.LAYER)), BombardMesh.TEXTURE, Bombard.SCALE, 1.2f);
    }

    public static SiegeRenderer<SiegeTower, SiegeTowerModel> tower(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new SiegeTowerModel(c.bakeLayer(SiegeTowerModel.LAYER)), SiegeTowerMesh.TEXTURE, SiegeTower.SCALE, 1.8f);
    }

    public static SiegeRenderer<Mantlet, MantletModel> mantlet(EntityRendererProvider.Context c) {
        return new SiegeRenderer<>(c, new MantletModel(c.bakeLayer(MantletModel.LAYER)), MantletMesh.TEXTURE, Mantlet.SCALE, 1.2f);
    }
}
