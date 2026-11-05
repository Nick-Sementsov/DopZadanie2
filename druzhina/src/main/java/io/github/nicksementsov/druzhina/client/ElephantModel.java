package io.github.nicksementsov.druzhina.client;

import io.github.nicksementsov.druzhina.Druzhina;
import io.github.nicksementsov.druzhina.entity.WarElephant;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A war elephant (the mesh and the texture are made by {@code tools/models.py}, see client/mesh/ElephantMesh): it
 * walks with a heavy stride, the body rolling and rising, fans its ears, swings its tail, and moves its trunk of five
 * pieces like a living thing; when it tosses a man it throws its head and curls its trunk up.
 */
public class ElephantModel extends HierarchicalModel<WarElephant> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Druzhina.id("war_elephant"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart earLeft;
    private final ModelPart earRight;
    private final ModelPart[] trunk = new ModelPart[5];
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
        ModelPart t = this.head.getChild("trunk1");
        for (int i = 0; i < 5; i++) {
            this.trunk[i] = t;
            if (i < 4) {
                t = t.getChild("trunk" + (i + 2));
            }
        }
        this.tail = this.body.getChild("tail");
        this.legFrontLeft = root.getChild("leg_front_left");
        this.legFrontRight = root.getChild("leg_front_right");
        this.legBackLeft = root.getChild("leg_back_left");
        this.legBackRight = root.getChild("leg_back_right");
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

        // the heavy stride: a lateral walk, each leg in turn, the foot lifted at the knee
        float[] phase = {0, Mth.PI, Mth.HALF_PI, Mth.PI * 1.5f};
        ModelPart[] legs = {this.legFrontLeft, this.legFrontRight, this.legBackLeft, this.legBackRight};
        for (int i = 0; i < 4; i++) {
            float s = Mth.cos(walk + phase[i]);
            legs[i].xRot = s * 0.38f * amount;
            legs[i].y -= Math.max(0, Mth.sin(walk + phase[i])) * 0.8f * amount;
        }
        // the body rolls from side to side and rises at each step
        this.body.y += Mth.cos(walk * 2) * 0.5f * amount;
        this.body.zRot = Mth.sin(walk) * 0.03f * amount;
        this.body.xRot = Mth.sin(walk * 2 + 0.6f) * 0.012f * amount;

        this.head.yRot = Mth.clamp(headYaw, -35, 35) * Mth.DEG_TO_RAD * 0.6f;
        this.head.xRot = Mth.clamp(headPitch, -20, 20) * Mth.DEG_TO_RAD * 0.5f + Mth.sin(walk * 2) * 0.04f * amount
                + Mth.sin(age * 0.05f) * 0.02f;

        // ears fan slowly, faster when it walks
        float flap = Mth.sin(age * 0.11f) * 0.16f + Mth.sin(walk) * 0.1f * amount;
        this.earLeft.yRot += flap;
        this.earRight.yRot -= flap;
        this.earLeft.zRot = Mth.sin(age * 0.11f + 0.5f) * 0.05f;
        this.earRight.zRot = -this.earLeft.zRot;

        // the trunk: a wave running down it, swinging with the steps and wandering when it stands
        for (int i = 0; i < 5; i++) {
            float k = (i + 1) / 5f;
            this.trunk[i].xRot += Mth.sin(age * 0.06f - i * 0.7f) * 0.05f * (0.5f + k) + Mth.sin(walk * 2 - i * 0.5f) * 0.04f * amount;
            this.trunk[i].zRot = Mth.sin(age * 0.045f - i * 0.6f) * 0.05f * k + Mth.sin(walk - i * 0.4f) * 0.07f * amount;
        }

        this.tail.zRot = Mth.sin(age * 0.1f) * 0.15f + Mth.sin(walk) * 0.2f * amount;
        this.tail.xRot += Mth.sin(age * 0.07f) * 0.05f;

        // the toss: head up, trunk up and curled, then down again
        float toss = e.toss(partial);
        if (toss > 0) {
            float f = Mth.sin(toss * Mth.PI);
            this.head.xRot -= 0.55f * f;
            float[] curl = {1.1f, 0.8f, 0.6f, 0.6f, 0.7f};
            for (int i = 0; i < 5; i++) {
                this.trunk[i].xRot -= curl[i] * f;
            }
            this.legFrontLeft.xRot -= 0.25f * f;
            this.legFrontRight.xRot -= 0.25f * f;
        }
    }
}
