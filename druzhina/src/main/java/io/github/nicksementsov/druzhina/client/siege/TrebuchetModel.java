package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Trebuchet;
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
 * A trebuchet on four wheels: a frame of beams, two uprights with braces, an axle on top, and on it the long arm with
 * a box of stones for a counterweight at the front end and a sling at the back. The arm swings over when it shoots and
 * is wound back down after. The texture is made by {@code tools/siege_texture.py} (see {@link SiegeTexture}).
 */
public class TrebuchetModel extends HierarchicalModel<Trebuchet> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("trebuchet"), "main");
    /** The height of the arm's axle, model pixels (the ground is at 24). */
    private static final float PIVOT_Y = -23;
    private static final float ARM_LENGTH = 40;

    private final ModelPart root;
    private final ModelPart arm;
    private final ModelPart weight;
    private final ModelPart sling;
    private final ModelPart stone;
    private final ModelPart[] wheels = new ModelPart[4];

    public TrebuchetModel(ModelPart root) {
        this.root = root;
        this.arm = root.getChild("arm");
        this.weight = this.arm.getChild("weight");
        this.sling = this.arm.getChild("sling");
        this.stone = this.sling.getChild("stone");
        for (int i = 0; i < 4; i++) {
            this.wheels[i] = root.getChild("wheel" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("frame", CubeListBuilder.create()
                // the two long beams and the cross beams
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V).addBox(-14, 13, -22, 3, 3, 44)
                .texOffs(SiegeTexture.WOOD_U + 100, SiegeTexture.WOOD_V).addBox(11, 13, -22, 3, 3, 44)
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V + 50).addBox(-11, 13, -21, 22, 3, 3)
                .texOffs(SiegeTexture.WOOD_U + 60, SiegeTexture.WOOD_V + 50).addBox(-11, 13, 18, 22, 3, 3)
                .texOffs(SiegeTexture.WOOD_U + 120, SiegeTexture.WOOD_V + 50).addBox(-11, 14, 4, 22, 2, 3)
                // the axles of the wheels
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V).addBox(-17, 17, -16, 34, 2, 2)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 5).addBox(-17, 17, 14, 34, 2, 2)
                // the uprights and the axle on them
                .texOffs(SiegeTexture.POST_U, SiegeTexture.POST_V).addBox(-14, -25, -1.5f, 3, 38, 3)
                .texOffs(SiegeTexture.POST_U + 14, SiegeTexture.POST_V).addBox(11, -25, -1.5f, 3, 38, 3)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 10).addBox(-15.5f, -24.5f, -1.5f, 31, 3, 3)
                // iron caps on the uprights
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V).addBox(-14.5f, -26, -2, 4, 2, 4)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V).addBox(10.5f, -26, -2, 4, 2, 4)
                // the winch at the back, to wind the arm down
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 16).addBox(-11, 8, 15, 22, 3, 3)
                .texOffs(SiegeTexture.POST_U + 28, SiegeTexture.POST_V).addBox(-12, 8, 14, 2, 5, 5)
                .texOffs(SiegeTexture.POST_U + 28, SiegeTexture.POST_V).addBox(10, 8, 14, 2, 5, 5),
            PartPose.ZERO);
        // the braces from the ends of the frame to the top of the uprights
        float brace = (float) Math.atan2(16.5, 33);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -12.5f : 12.5f;
            boolean front = i < 2;
            root.addOrReplaceChild("brace" + i, CubeListBuilder.create()
                    .texOffs(SiegeTexture.POST_U + 44 + i * 9, SiegeTexture.POST_V).addBox(-1, -37, -1, 2, 37, 2),
                PartPose.offsetAndRotation(x, 13, front ? -18 : 18, front ? -brace : brace, 0, 0));
        }
        // the arm: the long end at the back (the sling), the short end at the front (the counterweight)
        PartDefinition arm = root.addOrReplaceChild("arm", CubeListBuilder.create()
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V).addBox(-1.5f, -1.5f, -12, 3, 3, 52)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V + 8).addBox(-2, -2, -2, 4, 4, 4)
                .texOffs(SiegeTexture.IRON_U + 16, SiegeTexture.IRON_V + 8).addBox(-2, -2, ARM_LENGTH - 1, 4, 4, 2),
            PartPose.offset(0, PIVOT_Y, 0));
        arm.addOrReplaceChild("weight", CubeListBuilder.create()
                .texOffs(SiegeTexture.IRON_U + 30, SiegeTexture.IRON_V).addBox(-1, -1, -1, 2, 4, 2)
                .texOffs(SiegeTexture.WEIGHT_U, SiegeTexture.WEIGHT_V).addBox(-6, 2.5f, -5, 12, 11, 10),
            PartPose.offset(0, 0, -10.5f));
        PartDefinition sling = arm.addOrReplaceChild("sling", CubeListBuilder.create()
                .texOffs(SiegeTexture.ROPE_U, SiegeTexture.ROPE_V).addBox(-1.5f, 0, -0.5f, 1, 9, 1)
                .texOffs(SiegeTexture.ROPE_U + 6, SiegeTexture.ROPE_V).addBox(0.5f, 0, -0.5f, 1, 9, 1),
            PartPose.offset(0, 0, ARM_LENGTH));
        sling.addOrReplaceChild("stone", CubeListBuilder.create()
                .texOffs(SiegeTexture.STONE_U, SiegeTexture.STONE_V).addBox(-2.5f, 0, -2.5f, 5, 5, 5),
            PartPose.offset(0, 8.5f, 0));
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -18.5f : 18.5f;
            float z = i < 2 ? -15 : 15;
            root.addOrReplaceChild("wheel" + i, CubeListBuilder.create()
                    .texOffs(SiegeTexture.WHEEL_U, SiegeTexture.WHEEL_V).addBox(-1, -5, -5, 2, 10, 10),
                PartPose.offset(x, 19, z));
        }
        return LayerDefinition.create(mesh, SiegeTexture.W, SiegeTexture.H);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(Trebuchet e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float partial = Minecraft.getInstance().getFrameTime();
        float a = e.armAngle(partial) * Mth.DEG_TO_RAD;
        this.arm.xRot = a;
        // the box of stones hangs straight down from its hinge
        this.weight.xRot = -a;
        // the sling hangs too, and trails behind the arm as it whips over
        float whip = e.loaded() ? 0 : Mth.clamp(1 - Math.abs(e.sinceShot(partial) - Trebuchet.RELEASE) / 6f, 0, 1);
        this.sling.xRot = -a + whip * 1.6f;
        this.stone.visible = e.stoneShown();
        float roll = limbSwing * 0.6f;
        for (ModelPart w : this.wheels) {
            w.xRot = roll;
        }
    }
}
