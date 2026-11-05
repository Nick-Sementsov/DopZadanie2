package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Mantlet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;

/** The mobile wall: only its wheels move. */
public class MantletModel extends EngineModel<Mantlet> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("mantlet"), "main");

    public MantletModel(ModelPart root) {
        super(root, 2, 6);
    }

    @Override
    protected void work(Mantlet e, float partial, float age) {
    }
}
