package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Ballista;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A ballista on a cart: a bed of planks on four small wheels, a post, and on it the head that tilts to aim: the stock
 * with its groove, the frame of twisted sinew at the front with the two arms in it, the string, the slider that draws
 * it back, and the bolt in the groove. The arms spring forward when it shoots and are wound back after.
 */
public class BallistaModel extends HierarchicalModel<Ballista> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("ballista"), "main");
    private static final float ARM = 13;
    /** Where the arms sit in the frame, and the head's height of the string. */
    private static final float ARM_X = 6;
    private static final float ARM_Z = -11.5f;
    private static final float STRING_Y = -3;
    /** The slider: forward when shot, back when drawn. */
    private static final float NOCK_SHOT = -8.5f;
    private static final float NOCK_DRAWN = 8;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart armLeft;
    private final ModelPart armRight;
    private final ModelPart stringLeft;
    private final ModelPart stringRight;
    private final ModelPart slider;
    private final ModelPart bolt;
    private final ModelPart[] wheels = new ModelPart[4];

    public BallistaModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.armLeft = this.head.getChild("arm_left");
        this.armRight = this.head.getChild("arm_right");
        this.stringLeft = this.head.getChild("string_left");
        this.stringRight = this.head.getChild("string_right");
        this.slider = this.head.getChild("slider");
        this.bolt = this.slider.getChild("bolt");
        for (int i = 0; i < 4; i++) {
            this.wheels[i] = root.getChild("wheel" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cart", CubeListBuilder.create()
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V).addBox(-8, 13, -11, 16, 3, 22)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V).addBox(-12, 16, -9, 24, 2, 2)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 5).addBox(-12, 16, 7, 24, 2, 2)
                .texOffs(SiegeTexture.POST_U, SiegeTexture.POST_V).addBox(-2, 3, -2, 4, 10, 4)
                .texOffs(SiegeTexture.POST_U + 18, SiegeTexture.POST_V).addBox(-4, 10, -4, 8, 3, 8)
                // the winch at the back
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 10).addBox(-6, 9, 9, 12, 2, 2)
                .texOffs(SiegeTexture.POST_U + 52, SiegeTexture.POST_V).addBox(-7, 8, 8, 1, 5, 4)
                .texOffs(SiegeTexture.POST_U + 52, SiegeTexture.POST_V).addBox(6, 8, 8, 1, 5, 4)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V).addBox(7, 9.5f, 9.5f, 3, 1, 1),
            PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                // the stock with the groove
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V + 30).addBox(-2, -2, -14, 4, 3, 26)
                .texOffs(SiegeTexture.WOOD_U + 62, SiegeTexture.WOOD_V + 30).addBox(-2.5f, -3, -14, 1, 1, 26)
                .texOffs(SiegeTexture.WOOD_U + 62, SiegeTexture.WOOD_V + 30).addBox(1.5f, -3, -14, 1, 1, 26)
                // the frame of the sinew springs, banded with iron
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 16).addBox(-6, -7, -13, 12, 9, 3)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V + 16).addBox(-6.5f, -7.5f, -13.5f, 13, 1, 4)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V + 16).addBox(-6.5f, 1.5f, -13.5f, 13, 1, 4)
                .texOffs(SiegeTexture.ROPE_U, SiegeTexture.ROPE_V + 10).addBox(-5.5f, -6, -12.5f, 2, 7, 2)
                .texOffs(SiegeTexture.ROPE_U, SiegeTexture.ROPE_V + 10).addBox(3.5f, -6, -12.5f, 2, 7, 2)
                // the trigger at the back
                .texOffs(SiegeTexture.IRON_U + 20, SiegeTexture.IRON_V).addBox(-1, 1, 9, 2, 3, 2),
            PartPose.offset(0, 3, 0));
        head.addOrReplaceChild("arm_left", CubeListBuilder.create()
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 30).addBox(-ARM, -1, -1, ARM, 2, 2)
                .texOffs(SiegeTexture.IRON_U + 30, SiegeTexture.IRON_V).addBox(-ARM - 0.5f, -1.5f, -1.5f, 2, 3, 3),
            PartPose.offset(-ARM_X, STRING_Y, ARM_Z));
        head.addOrReplaceChild("arm_right", CubeListBuilder.create()
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 35).addBox(0, -1, -1, ARM, 2, 2)
                .texOffs(SiegeTexture.IRON_U + 30, SiegeTexture.IRON_V).addBox(ARM - 1.5f, -1.5f, -1.5f, 2, 3, 3),
            PartPose.offset(ARM_X, STRING_Y, ARM_Z));
        head.addOrReplaceChild("string_left", CubeListBuilder.create()
                .texOffs(SiegeTexture.ROPE_U + 12, SiegeTexture.ROPE_V).addBox(0, -0.5f, -0.5f, 1, 1, 1),
            PartPose.offset(0, STRING_Y, 0));
        head.addOrReplaceChild("string_right", CubeListBuilder.create()
                .texOffs(SiegeTexture.ROPE_U + 12, SiegeTexture.ROPE_V).addBox(0, -0.5f, -0.5f, 1, 1, 1),
            PartPose.offset(0, STRING_Y, 0));
        PartDefinition slider = head.addOrReplaceChild("slider", CubeListBuilder.create()
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 60).addBox(-1.5f, -1, -0.5f, 3, 1, 3),
            PartPose.offset(0, STRING_Y, NOCK_DRAWN));
        slider.addOrReplaceChild("bolt", CubeListBuilder.create()
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 40).addBox(-0.5f, -1, -19, 1, 1, 19)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V + 24).addBox(-1, -1.5f, -22, 2, 2, 3)
                .texOffs(SiegeTexture.ROPE_U + 20, SiegeTexture.ROPE_V).addBox(-1.5f, -1, -2, 3, 1, 2),
            PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -13 : 13;
            float z = i < 2 ? -8 : 8;
            root.addOrReplaceChild("wheel" + i, CubeListBuilder.create()
                    .texOffs(SiegeTexture.SMALL_WHEEL_U, SiegeTexture.SMALL_WHEEL_V).addBox(-1, -4, -4, 2, 8, 8),
                PartPose.offset(x, 20, z));
        }
        return LayerDefinition.create(mesh, SiegeTexture.W, SiegeTexture.H);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(Ballista e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float partial = Minecraft.getInstance().getFrameTime();
        this.head.xRot = -e.aimPitch() * Mth.DEG_TO_RAD;
        float drawn = e.drawn(partial);
        // the arms spring forward at the shot and are bent back as it is wound
        float bend = (12 + 28 * drawn) * Mth.DEG_TO_RAD;
        this.armLeft.yRot = bend;
        this.armRight.yRot = -bend;
        float nock = Mth.lerp(drawn, NOCK_SHOT, NOCK_DRAWN);
        this.slider.z = nock;
        this.bolt.visible = e.boltShown();
        // the string from each arm's tip to the slider
        float tipX = ARM_X + ARM * Mth.cos(bend);
        float tipZ = ARM_Z + ARM * Mth.sin(bend);
        this.string(this.stringLeft, -tipX, tipZ, -0.5f, nock);
        this.string(this.stringRight, tipX, tipZ, 0.5f, nock);
        float roll = limbSwing * 0.75f;
        for (ModelPart w : this.wheels) {
            w.xRot = roll;
        }
    }

    /** Stretches a string from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}). */
    private void string(ModelPart part, float x0, float z0, float x1, float z1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        part.x = x0;
        part.z = z0;
        part.yRot = (float) Mth.atan2(-dz, dx);
        part.xScale = Mth.sqrt(dx * dx + dz * dz);
    }
}
