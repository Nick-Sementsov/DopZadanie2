package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.SiegeTower;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The siege tower: its bridge stands up before the top floor, and is let down onto the wall. */
public class SiegeTowerModel extends EngineModel<SiegeTower> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("siege_tower"), "main");

    private final ModelPart bridge;

    public SiegeTowerModel(ModelPart root) {
        super(root, 4, 7);
        this.bridge = root.getChild("bridge");
    }

    @Override
    protected void work(SiegeTower e, float partial, float age) {
        float down = e.bridge(partial);
        // it falls faster as it goes, and bounces a little on the wall
        float k = down * down;
        float bounce = down >= 1 ? 0 : Mth.sin(down * Mth.PI) * 0.03f;
        this.bridge.xRot = (k + bounce) * Mth.HALF_PI;
    }
}
