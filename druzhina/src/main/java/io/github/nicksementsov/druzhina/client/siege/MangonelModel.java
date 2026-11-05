package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Mangonel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The mangonel: the arm lies wound back, flies up against the crossbar at the throw, and is wound down again. */
public class MangonelModel extends EngineModel<Mangonel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("mangonel"), "main");

    private final ModelPart arm;
    private final ModelPart pot;

    public MangonelModel(ModelPart root) {
        super(root, 4, 5);
        this.arm = root.getChild("arm");
        this.pot = this.arm.getChild("pot");
    }

    @Override
    protected void work(Mangonel e, float partial, float age) {
        this.arm.xRot = e.armAngle(partial) * Mth.DEG_TO_RAD;
        this.pot.visible = e.potShown();
    }
}
