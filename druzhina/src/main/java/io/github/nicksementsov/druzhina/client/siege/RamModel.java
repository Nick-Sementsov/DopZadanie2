package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Ram;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The ram: the log swings back on its chains and forward into the wall, staying level. */
public class RamModel extends EngineModel<Ram> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("battering_ram"), "main");

    private final ModelPart hanger;
    private final ModelPart log;

    public RamModel(ModelPart root) {
        super(root, 4, 6);
        this.hanger = root.getChild("hanger");
        this.log = this.hanger.getChild("log");
    }

    @Override
    protected void work(Ram e, float partial, float age) {
        float swing = e.swing(partial) * Mth.DEG_TO_RAD;
        this.hanger.xRot = swing;
        this.log.xRot = -swing;
    }
}
