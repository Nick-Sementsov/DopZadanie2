package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Trebuchet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The trebuchet: the arm swings over when it throws and is wound back; the weight and the sling hang down. */
public class TrebuchetModel extends EngineModel<Trebuchet> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("trebuchet"), "main");

    private final ModelPart arm;
    private final ModelPart weight;
    private final ModelPart sling;
    private final ModelPart stone;

    public TrebuchetModel(ModelPart root) {
        super(root, 4, 8);
        this.arm = root.getChild("arm");
        this.weight = this.arm.getChild("weight");
        this.sling = this.arm.getChild("sling");
        this.stone = this.sling.getChild("stone");
    }

    @Override
    protected void work(Trebuchet e, float partial, float age) {
        float a = e.armAngle(partial) * Mth.DEG_TO_RAD;
        this.arm.xRot = a;
        // the box of stones hangs straight down from its hinge, swinging a little after the throw
        float since = e.loaded() ? 99 : e.sinceShot(partial);
        float sway = since < 40 ? Mth.sin(since * 0.35f) * 0.25f * (1 - since / 40) : 0;
        this.weight.xRot = -a + sway;
        // the sling hangs too, and trails behind the arm as it whips over
        float whip = Mth.clamp(1 - Math.abs(since - Trebuchet.RELEASE) / 6f, 0, 1);
        this.sling.xRot = -a + whip * 1.6f;
        this.stone.visible = e.stoneShown();
    }
}
