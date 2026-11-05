package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.siege.Ram;
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
 * A covered battering ram: a frame on four wheels with six posts, a ridged roof of hides over it, and under the roof
 * a log with an iron head hung on two ropes, drawn back before each blow and swung forward into the wall.
 */
public class RamModel extends HierarchicalModel<Ram> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("battering_ram"), "main");
    private static final float RIDGE_Y = -19;
    private static final float HANG_Y = -17;
    private static final float ROPE = 15;

    private final ModelPart root;
    private final ModelPart hanger;
    private final ModelPart log;
    private final ModelPart[] wheels = new ModelPart[4];

    public RamModel(ModelPart root) {
        this.root = root;
        this.hanger = root.getChild("hanger");
        this.log = this.hanger.getChild("log");
        for (int i = 0; i < 4; i++) {
            this.wheels[i] = root.getChild("wheel" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder frame = CubeListBuilder.create()
                // the bed: two long beams and the cross beams
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V).addBox(-13, 12, -22, 3, 3, 44)
                .texOffs(SiegeTexture.WOOD_U + 100, SiegeTexture.WOOD_V).addBox(10, 12, -22, 3, 3, 44)
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V + 50).addBox(-10, 12, -22, 20, 3, 3)
                .texOffs(SiegeTexture.WOOD_U + 60, SiegeTexture.WOOD_V + 50).addBox(-10, 12, 19, 20, 3, 3)
                // the axles
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V).addBox(-16, 16, -15, 32, 2, 2)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 5).addBox(-16, 16, 13, 32, 2, 2)
                // the top rails and the ridge
                .texOffs(SiegeTexture.WOOD_U + 100, SiegeTexture.WOOD_V).addBox(-13, -11, -22, 3, 2, 44)
                .texOffs(SiegeTexture.WOOD_U + 100, SiegeTexture.WOOD_V).addBox(10, -11, -22, 3, 2, 44)
                .texOffs(SiegeTexture.DARK_U, SiegeTexture.DARK_V + 10).addBox(-1, RIDGE_Y - 1, -23, 2, 2, 46);
        // six posts
        int i = 0;
        for (float z : new float[]{-21.5f, -1.5f, 18.5f}) {
            for (float x : new float[]{-12.5f, 10.5f}) {
                frame.texOffs(SiegeTexture.POST_U + i * 9, SiegeTexture.POST_V).addBox(x, -9, z, 2, 21, 3);
                i++;
            }
        }
        root.addOrReplaceChild("frame", frame, PartPose.ZERO);
        float slope = (float) Math.atan2(RIDGE_Y + 10, 13.5);
        root.addOrReplaceChild("roof_left", CubeListBuilder.create()
                .texOffs(SiegeTexture.HIDE_U, SiegeTexture.HIDE_V).addBox(-17, 0, -23, 17, 1, 46),
            PartPose.offsetAndRotation(0, RIDGE_Y, 0, 0, 0, slope));
        root.addOrReplaceChild("roof_right", CubeListBuilder.create()
                .texOffs(SiegeTexture.HIDE_U, SiegeTexture.HIDE_V).mirror().addBox(0, 0, -23, 17, 1, 46),
            PartPose.offsetAndRotation(0, RIDGE_Y, 0, 0, 0, -slope));
        PartDefinition hanger = root.addOrReplaceChild("hanger", CubeListBuilder.create()
                .texOffs(SiegeTexture.ROPE_U, SiegeTexture.ROPE_V + 20).addBox(-0.5f, 0, -12.5f, 1, ROPE, 1)
                .texOffs(SiegeTexture.ROPE_U + 6, SiegeTexture.ROPE_V + 20).addBox(-0.5f, 0, 4.5f, 1, ROPE, 1),
            PartPose.offset(0, HANG_Y, 0));
        hanger.addOrReplaceChild("log", CubeListBuilder.create()
                .texOffs(SiegeTexture.WOOD_U, SiegeTexture.WOOD_V).addBox(-2.5f, -2.5f, -26, 5, 5, 36)
                .texOffs(SiegeTexture.IRON_U, SiegeTexture.IRON_V + 30).addBox(-3.5f, -3.5f, -30, 7, 7, 4)
                .texOffs(SiegeTexture.IRON_U + 24, SiegeTexture.IRON_V + 30).addBox(-3, -3, -21, 6, 6, 1)
                .texOffs(SiegeTexture.IRON_U + 24, SiegeTexture.IRON_V + 30).addBox(-3, -3, 4, 6, 6, 1),
            PartPose.offset(0, ROPE, 0));
        for (int w = 0; w < 4; w++) {
            float x = w % 2 == 0 ? -17.5f : 17.5f;
            float z = w < 2 ? -14 : 14;
            root.addOrReplaceChild("wheel" + w, CubeListBuilder.create()
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
    public void setupAnim(Ram e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        float partial = Minecraft.getInstance().getFrameTime();
        float swing = e.swing(partial) * Mth.DEG_TO_RAD;
        this.hanger.xRot = swing;
        // the log stays level as it swings on its two ropes
        this.log.xRot = -swing;
        float roll = limbSwing * 0.6f;
        for (ModelPart w : this.wheels) {
            w.xRot = roll;
        }
    }
}
