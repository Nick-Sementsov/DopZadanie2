package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A war elephant: a great grey body under a red caparison with a gold border, a howdah with a canopy on its back, a
 * steel plate on its brow, long tusks, a trunk that sways, ears that flap. The texture is made by
 * {@code tools/elephant_texture.py}; the boxes and their texture places here must match it.
 */
public class ElephantModel extends HierarchicalModel<WarElephant> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("war_elephant"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart trunk1;
    private final ModelPart trunk2;
    private final ModelPart trunk3;
    private final ModelPart tail;
    private final ModelPart legFrontLeft;
    private final ModelPart legFrontRight;
    private final ModelPart legBackLeft;
    private final ModelPart legBackRight;

    public ElephantModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.earLeft = this.head.getChild("ear_left");
        this.earRight = this.head.getChild("ear_right");
        this.trunk1 = this.head.getChild("trunk1");
        this.trunk2 = this.trunk1.getChild("trunk2");
        this.trunk3 = this.trunk2.getChild("trunk3");
        this.tail = this.body.getChild("tail");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.legBackRight = root.getChild("leg_back_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10, -8, -15, 20, 18, 30)
                .texOffs(0, 48).addBox(-10, -8, -11, 20, 11, 22, new CubeDeformation(0.6f)),
            PartPose.ZERO);

        // the howdah: a floor, low walls, four posts and a canopy
        PartDefinition howdah = body.addOrReplaceChild("howdah", CubeListBuilder.create()
                .texOffs(84, 48).addBox(-9, -2, -8, 18, 2, 16)
                .texOffs(152, 48).addBox(-9, -8, -8, 18, 6, 1)
                .texOffs(152, 48).addBox(-9, -8, 7, 18, 6, 1)
                .texOffs(190, 48).addBox(8, -8, -7, 1, 6, 14)
                .texOffs(190, 48).mirror().addBox(-9, -8, -7, 1, 6, 14).mirror(false)
                .texOffs(220, 48).addBox(-9, -20, -8, 1, 12, 1)
                .texOffs(220, 48).addBox(8, -20, -8, 1, 12, 1)
                .texOffs(220, 48).addBox(-9, -20, 7, 1, 12, 1)
                .texOffs(220, 48).addBox(8, -20, 7, 1, 12, 1)
                .texOffs(0, 81).addBox(-10, -21, -9, 20, 1, 18)
                .texOffs(76, 81).addBox(-7, -23, -6, 14, 2, 12)
                .texOffs(128, 81).addBox(-4, -25, -3, 8, 2, 6),
            PartPose.offset(0, -8.6f, 2));

        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(244, 0).addBox(-0.5f, 0, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, -5, 15, 0.25f, 0, 0));
        tail.addOrReplaceChild("tuft", CubeListBuilder.create()
                .texOffs(244, 16).addBox(-1, 0, -1, 2, 3, 2),
            PartPose.offset(0, 12, 0));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(100, 0).addBox(-6.5f, -6, -9, 13, 14, 9)
                .texOffs(144, 0).addBox(-5.5f, -9, -8, 11, 4, 7)
                .texOffs(152, 23).addBox(-5, -8, -9.8f, 10, 10, 1),
            PartPose.offset(0, -3, -15));
        head.addOrReplaceChild("ear_left", CubeListBuilder.create()
                .texOffs(100, 23).addBox(0, 0, 0, 1, 12, 10),
            PartPose.offsetAndRotation(6.5f, -4, -5, 0, 0.5f, 0));
        head.addOrReplaceChild("ear_right", CubeListBuilder.create()
                .texOffs(100, 23).mirror().addBox(-1, 0, 0, 1, 12, 10),
            PartPose.offsetAndRotation(-6.5f, -4, -5, 0, -0.5f, 0));

        PartDefinition trunk1 = head.addOrReplaceChild("trunk1", CubeListBuilder.create()
                .texOffs(180, 0).addBox(-2.5f, 0, -2.5f, 5, 8, 5),
            PartPose.offsetAndRotation(0, 5, -7.5f, 0.12f, 0, 0));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create()
                .texOffs(200, 0).addBox(-2, 0, -2, 4, 7, 4),
            PartPose.offsetAndRotation(0, 7.5f, 0, 0.1f, 0, 0));
        trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create()
                .texOffs(216, 0).addBox(-1.5f, 0, -1.5f, 3, 6, 3),
            PartPose.offsetAndRotation(0, 6.5f, 0, -0.35f, 0, 0));

        for (int side : new int[]{1, -1}) {
            String name = side > 0 ? "tusk_left" : "tusk_right";
            PartDefinition tusk = head.addOrReplaceChild(name, CubeListBuilder.create()
                    .texOffs(228, 0).addBox(-1, -1, -6, 2, 2, 6),
                PartPose.offsetAndRotation(3.8f * side, 5.5f, -7, 0.55f, -0.12f * side, 0));
            tusk.addOrReplaceChild("tip", CubeListBuilder.create()
                    .texOffs(228, 8).addBox(-1, -1, -6, 2, 2, 6, new CubeDeformation(-0.25f)),
                PartPose.offsetAndRotation(0, 0, -5.6f, -0.9f, 0, 0));
        }

        CubeListBuilder leg = CubeListBuilder.create().texOffs(124, 23).addBox(-3.5f, 0, -3.5f, 7, 14, 7);
        CubeListBuilder legMirror = CubeListBuilder.create().texOffs(124, 23).mirror().addBox(-3.5f, 0, -3.5f, 7, 14, 7);
        root.addOrReplaceChild("leg_front_left", leg, PartPose.offset(6, 10, -9.5f));
        root.addOrReplaceChild("leg_front_right", legMirror, PartPose.offset(-6, 10, -9.5f));
        root.addOrReplaceChild("leg_back_left", leg, PartPose.offset(6, 10, 9.5f));
        root.addOrReplaceChild("leg_back_right", legMirror, PartPose.offset(-6, 10, 9.5f));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(WarElephant e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        float amount = Math.min(1, limbSwingAmount);
        float walk = limbSwing * 0.45f;
        float partial = age - e.tickCount;

        // the heavy stride: diagonal legs together
        float swing = Mth.cos(walk) * 0.42f * amount;
        this.legFrontLeft.xRot = swing;
        this.legBackRight.xRot = swing;
        this.legFrontRight.xRot = -swing;
        this.legBackLeft.xRot = -swing;
        // the body sways and rises with every step
        this.body.y += Mth.cos(walk * 2) * 0.6f * amount;
        this.body.zRot = Mth.sin(walk) * 0.025f * amount;

        this.head.yRot = Mth.clamp(headYaw, -35, 35) * Mth.DEG_TO_RAD * 0.6f;
        this.head.xRot = Mth.clamp(headPitch, -20, 20) * Mth.DEG_TO_RAD * 0.5f + Mth.sin(walk * 2) * 0.05f * amount;

        // ears fan slowly, faster when it walks
        float flap = Mth.sin(age * 0.12f) * 0.14f + Mth.sin(walk) * 0.12f * amount;
        this.earLeft.yRot = 0.5f + flap;
        this.earRight.yRot = -0.5f - flap;

        // the trunk swings with the steps and wanders when it stands
        this.trunk1.zRot = Mth.sin(age * 0.07f) * 0.08f + Mth.sin(walk) * 0.12f * amount;
        this.trunk1.xRot += Mth.sin(age * 0.05f) * 0.04f;
        this.trunk2.xRot += Mth.sin(age * 0.09f + 1) * 0.07f;
        this.trunk3.xRot += Mth.sin(age * 0.11f + 2) * 0.14f;

        this.tail.zRot = Mth.sin(age * 0.1f) * 0.15f + Mth.sin(walk) * 0.2f * amount;

        // the toss: head up, trunk up and curled, then down again
        float toss = e.toss(partial);
        if (toss > 0) {
            float f = Mth.sin(toss * Mth.PI);
            this.head.xRot -= 0.55f * f;
            this.trunk1.xRot -= 1.3f * f;
            this.trunk2.xRot -= 0.9f * f;
            this.trunk3.xRot -= 0.7f * f;
            this.legFrontLeft.xRot -= 0.25f * f;
            this.legFrontRight.xRot -= 0.25f * f;
        }
    }
}
