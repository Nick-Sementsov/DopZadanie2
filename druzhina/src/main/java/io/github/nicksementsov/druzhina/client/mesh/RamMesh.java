package io.github.nicksementsov.druzhina.client.mesh;

import io.github.nicksementsov.druzhina.Druzhina;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A covered battering ram: a frame on four wheels with six posts, a ridged roof of boards under wet hides with skirts of hide down the sides, and under it a log with an iron ram's head hung on two chains from the ridge.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class RamMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/ram.png");

    private RamMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 120).addBox(-15, 12, -26, 3, 4, 52)
                .texOffs(0, 120).addBox(12, 12, -26, 3, 4, 52)
                .texOffs(74, 286).addBox(-12, 12, -26, 24, 3, 3)
                .texOffs(74, 286).addBox(-12, 12, 23, 24, 3, 3)
                .texOffs(150, 286).addBox(-12, 13, -1.5f, 24, 2, 3)
                .texOffs(0, 294).addBox(-17, 17, -17, 34, 2, 2)
                .texOffs(0, 294).addBox(-17, 17, 15, 34, 2, 2)
                .texOffs(202, 231).addBox(-14.5f, -8, -24, 3, 20, 3)
                .texOffs(202, 231).addBox(-14.5f, -8, -1.5f, 3, 20, 3)
                .texOffs(202, 231).addBox(-14.5f, -8, 21, 3, 20, 3)
                .texOffs(202, 231).addBox(11.5f, -8, -24, 3, 20, 3)
                .texOffs(202, 231).addBox(11.5f, -8, -1.5f, 3, 20, 3)
                .texOffs(202, 231).addBox(11.5f, -8, 21, 3, 20, 3)
                .texOffs(0, 231).addBox(-16, -11, -26, 3, 3, 52)
                .texOffs(0, 231).addBox(13, -11, -26, 3, 3, 52)
                .texOffs(102, 0).addBox(-1.5f, -24.5f, -27, 3, 3, 54)
                .texOffs(204, 286).addBox(-1, -21.5f, -15, 2, 3, 2)
                .texOffs(204, 286).addBox(-1, -21.5f, 6, 2, 3, 2)
                .texOffs(0, 0).addBox(-16.6f, -9, -25, 1, 14, 50)
                .texOffs(0, 0).addBox(15.6f, -9, -25, 1, 14, 50),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = root.addOrReplaceChild("roof_left", CubeListBuilder.create()
                .texOffs(0, 176).addBox(-22, 0, -27, 22, 1, 54)
                .texOffs(0, 64).addBox(-22, -1, -27.5f, 22, 1, 55),
            PartPose.offsetAndRotation(0, -23, 0, 0, 0, -0.7188f));
        PartDefinition p2 = root.addOrReplaceChild("roof_right", CubeListBuilder.create()
                .texOffs(0, 176).addBox(0, 0, -27, 22, 1, 54)
                .texOffs(0, 64).addBox(0, -1, -27.5f, 22, 1, 55),
            PartPose.offsetAndRotation(0, -23, 0, 0, 0, 0.7188f));
        PartDefinition p3 = root.addOrReplaceChild("hanger", CubeListBuilder.create()
                .texOffs(214, 231).addBox(-0.5f, 0, -14.5f, 1, 15, 1)
                .texOffs(214, 231).addBox(-0.5f, 0, 6.5f, 1, 15, 1),
            PartPose.offset(0, -19, 0));
        PartDefinition p4 = p3.addOrReplaceChild("log", CubeListBuilder.create()
                .texOffs(110, 231).addBox(-3, -3, -30, 6, 6, 40)
                .texOffs(0, 286).addBox(-3, -3, -24, 6, 6, 2, new CubeDeformation(0.3f))
                .texOffs(0, 286).addBox(-3, -3, -10, 6, 6, 2, new CubeDeformation(0.3f))
                .texOffs(0, 286).addBox(-3, -3, 4, 6, 6, 2, new CubeDeformation(0.3f))
                .texOffs(218, 231).addBox(-4, -4, -36, 8, 8, 6)
                .texOffs(60, 286).addBox(-2.5f, -2.5f, -38, 5, 5, 2)
                .texOffs(32, 286).addBox(-5.5f, -4.5f, -35, 1.5f, 4, 4)
                .texOffs(140, 286).addBox(-6, -1, -37, 1.5f, 3, 3)
                .texOffs(32, 286).addBox(4, -4.5f, -35, 1.5f, 4, 4)
                .texOffs(140, 286).addBox(4.5f, -1, -37, 1.5f, 3, 3),
            PartPose.offset(0, 15, 0));
        PartDefinition p5 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(128, 286).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-18, 18, -16));
        PartDefinition p6 = p5.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p7 = p5.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p8 = p5.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p9 = p5.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p10 = p5.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p11 = p5.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p12 = p5.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p13 = p5.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p14 = p5.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p15 = p5.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p16 = p5.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p17 = p5.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p18 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(128, 286).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(18, 18, -16));
        PartDefinition p19 = p18.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p20 = p18.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p21 = p18.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p22 = p18.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p23 = p18.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p24 = p18.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p25 = p18.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p26 = p18.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p27 = p18.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p28 = p18.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p29 = p18.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p30 = p18.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p31 = root.addOrReplaceChild("wheel2", CubeListBuilder.create()
                .texOffs(128, 286).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-18, 18, 16));
        PartDefinition p32 = p31.addOrReplaceChild("wheel2_felloe0", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p33 = p31.addOrReplaceChild("wheel2_felloe1", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p34 = p31.addOrReplaceChild("wheel2_felloe2", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p35 = p31.addOrReplaceChild("wheel2_felloe3", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p36 = p31.addOrReplaceChild("wheel2_felloe4", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p37 = p31.addOrReplaceChild("wheel2_felloe5", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p38 = p31.addOrReplaceChild("wheel2_felloe6", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p39 = p31.addOrReplaceChild("wheel2_felloe7", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p40 = p31.addOrReplaceChild("wheel2_spoke0", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p41 = p31.addOrReplaceChild("wheel2_spoke1", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p42 = p31.addOrReplaceChild("wheel2_spoke2", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p43 = p31.addOrReplaceChild("wheel2_spoke3", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p44 = root.addOrReplaceChild("wheel3", CubeListBuilder.create()
                .texOffs(128, 286).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(18, 18, 16));
        PartDefinition p45 = p44.addOrReplaceChild("wheel3_felloe0", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p46 = p44.addOrReplaceChild("wheel3_felloe1", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p47 = p44.addOrReplaceChild("wheel3_felloe2", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p48 = p44.addOrReplaceChild("wheel3_felloe3", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p49 = p44.addOrReplaceChild("wheel3_felloe4", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p50 = p44.addOrReplaceChild("wheel3_felloe5", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p51 = p44.addOrReplaceChild("wheel3_felloe6", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p52 = p44.addOrReplaceChild("wheel3_felloe7", CubeListBuilder.create()
                .texOffs(16, 286).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(44, 286).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p53 = p44.addOrReplaceChild("wheel3_spoke0", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p54 = p44.addOrReplaceChild("wheel3_spoke1", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p55 = p44.addOrReplaceChild("wheel3_spoke2", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p56 = p44.addOrReplaceChild("wheel3_spoke3", CubeListBuilder.create()
                .texOffs(246, 231).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 512);
    }
}
