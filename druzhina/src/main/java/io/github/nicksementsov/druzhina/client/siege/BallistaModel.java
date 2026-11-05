package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Ballista;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** The ballista: the head tilts to aim, the arms spring forward at the shot and are bent back, the string follows. */
public class BallistaModel extends EngineModel<Ballista> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("ballista"), "main");
    private static final float ARM = 14;
    private static final float ARM_X = 5.5f;
    private static final float ARM_Z = -12;
    private static final float NOCK_SHOT = -9;
    private static final float NOCK_DRAWN = 11;

    private final ModelPart head;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart stringLeft;
    private final ModelPart stringRight;
    private final ModelPart slider;
    private final ModelPart bolt;

    public BallistaModel(ModelPart root) {
        super(root, 4, 6);
        this.head = root.getChild("head");
        this.armLeft = this.head.getChild("arm_left");
        this.armRight = this.head.getChild("arm_right");
        this.stringLeft = this.head.getChild("string_left");
        this.stringRight = this.head.getChild("string_right");
        this.slider = this.head.getChild("slider");
        this.bolt = this.slider.getChild("bolt");
    }

    @Override
    protected void work(Ballista e, float partial, float age) {
        this.head.xRot = -e.aimPitch() * Mth.DEG_TO_RAD;
        float drawn = e.drawn(partial);
        float bend = (12 + 26 * drawn) * Mth.DEG_TO_RAD;
        this.armLeft.yRot = bend;
        this.armRight.yRot = -bend;
        float nock = Mth.lerp(drawn, NOCK_SHOT, NOCK_DRAWN);
        this.slider.z = nock;
        this.bolt.visible = e.boltShown();
        float tipX = ARM_X + ARM * Mth.cos(bend);
        float tipZ = ARM_Z + ARM * Mth.sin(bend);
        string(this.stringLeft, -tipX, tipZ, -0.5f, nock);
        string(this.stringRight, tipX, tipZ, 0.5f, nock);
    }

    /** Stretches a string from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}). */
    private static void string(ModelPart part, float x0, float z0, float x1, float z1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        part.x = x0;
        part.z = z0;
        part.yRot = (float) Mth.atan2(-dz, dx);
        part.xScale = Mth.sqrt(dx * dx + dz * dz);
    }
}
