package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Bombard;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The bombard: the barrel is raised to aim and kicks back on its trunnions when it fires. */
public class BombardModel extends EngineModel<Bombard> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("bombard"), "main");

    private final ModelPart barrel;

    public BombardModel(ModelPart root) {
        super(root, 2, 9);
        this.barrel = root.getChild("barrel");
    }

    @Override
    protected void work(Bombard e, float partial, float age) {
        float kick = e.recoil(partial);
        this.barrel.xRot = -e.aimPitch() * Mth.DEG_TO_RAD - kick * 0.12f;
        this.barrel.z += kick * 4;
    }
}
