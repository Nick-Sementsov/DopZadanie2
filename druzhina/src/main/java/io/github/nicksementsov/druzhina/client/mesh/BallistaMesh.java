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
 * A ballista on a four-wheeled carriage: a planked bed between rails, a post and a turntable, and on it the head that tilts to aim: the stock with its groove and rails, the frame of the two sinew springs with iron washers, the arms in the springs, the string, the slider with its claw, the bolt, and the winch at the back.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class BallistaMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/ballista.png");

    private BallistaMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("cart", CubeListBuilder.create()
                .texOffs(0, 35).addBox(-9, 12, -12, 18, 2, 24)
                .texOffs(140, 0).addBox(-11, 11, -13, 2, 4, 26)
                .texOffs(140, 0).addBox(9, 11, -13, 2, 4, 26)
                .texOffs(0, 69).addBox(-14, 17, -11, 28, 2, 2)
                .texOffs(0, 69).addBox(-14, 17, 9, 28, 2, 2)
                .texOffs(158, 35).addBox(-2, 1, -2, 4, 11, 4)
                .texOffs(196, 35).addBox(-5, 0, -5, 10, 1, 10),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("strut0", CubeListBuilder.create()
                .texOffs(188, 35).addBox(-1, -12.7475f, -1, 2, 12.7475f, 2),
            PartPose.offsetAndRotation(-9, 12, -8, 0.8924f, -2.2849f, 0));
        PartDefinition p2 = p0.addOrReplaceChild("strut1", CubeListBuilder.create()
                .texOffs(188, 35).addBox(-1, -12.7475f, -1, 2, 12.7475f, 2),
            PartPose.offsetAndRotation(9, 12, -8, 0.8924f, 2.2849f, 0));
        PartDefinition p3 = p0.addOrReplaceChild("strut2", CubeListBuilder.create()
                .texOffs(188, 35).addBox(-1, -12.7475f, -1, 2, 12.7475f, 2),
            PartPose.offsetAndRotation(-9, 12, 8, 0.8924f, -0.8567f, 0));
        PartDefinition p4 = p0.addOrReplaceChild("strut3", CubeListBuilder.create()
                .texOffs(188, 35).addBox(-1, -12.7475f, -1, 2, 12.7475f, 2),
            PartPose.offsetAndRotation(9, 12, 8, 0.8924f, 0.8567f, 0));
        PartDefinition p5 = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.5f, -2, -16, 5, 3, 32)
                .texOffs(74, 0).addBox(-2.5f, -3, -16, 1, 1, 32)
                .texOffs(74, 0).addBox(1.5f, -3, -16, 1, 1, 32)
                .texOffs(40, 61).addBox(-9, -11, -14.5f, 18, 2, 5)
                .texOffs(40, 61).addBox(-9, 3, -14.5f, 18, 2, 5)
                .texOffs(134, 35).addBox(-9, -9, -14.5f, 2, 12, 5)
                .texOffs(134, 35).addBox(7, -9, -14.5f, 2, 12, 5)
                .texOffs(148, 35).addBox(-3.5f, -9, -14, 1, 12, 4)
                .texOffs(148, 35).addBox(2.5f, -9, -14, 1, 12, 4)
                .texOffs(174, 35).addBox(-7, -9, -13.5f, 3.5f, 12, 3)
                .texOffs(192, 61).addBox(-7.5f, -12, -14, 4.5f, 1, 4)
                .texOffs(192, 61).addBox(-7.5f, 5, -14, 4.5f, 1, 4)
                .texOffs(174, 35).addBox(3.5f, -9, -13.5f, 3.5f, 12, 3)
                .texOffs(192, 61).addBox(3, -12, -14, 4.5f, 1, 4)
                .texOffs(192, 61).addBox(3, 5, -14, 4.5f, 1, 4)
                .texOffs(86, 61).addBox(-9, -11, -14.5f, 18, 2, 5, new CubeDeformation(0.2f))
                .texOffs(240, 35).addBox(-5, -5, 11, 1, 5, 4)
                .texOffs(240, 35).addBox(4, -5, 11, 1, 5, 4)
                .texOffs(148, 61).addBox(-4, -4, 11.5f, 8, 3, 3)
                .texOffs(0, 61).addBox(-3, -4.2f, 11.3f, 6, 3.4f, 3.4f)
                .texOffs(92, 69).addBox(5, -3, 12.5f, 4, 1, 1)
                .texOffs(92, 69).addBox(-9, -3, 12.5f, 4, 1, 1)
                .texOffs(36, 61).addBox(8, -6, 12.5f, 1, 7, 1)
                .texOffs(36, 61).addBox(-9, -6, 12.5f, 1, 7, 1),
            PartPose.offset(0, -1, 0));
        PartDefinition p6 = p5.addOrReplaceChild("arm_left", CubeListBuilder.create()
                .texOffs(60, 69).addBox(-14, -1, -1, 14, 2, 2)
                .texOffs(182, 61).addBox(-15, -1.5f, -1.5f, 2, 3, 3),
            PartPose.offset(-5.5f, -3, -12));
        PartDefinition p7 = p5.addOrReplaceChild("arm_right", CubeListBuilder.create()
                .texOffs(60, 69).addBox(0, -1, -1, 14, 2, 2)
                .texOffs(182, 61).addBox(13, -1.5f, -1.5f, 2, 3, 3),
            PartPose.offset(5.5f, -3, -12));
        PartDefinition p8 = p5.addOrReplaceChild("string_left", CubeListBuilder.create()
                .texOffs(108, 69).addBox(0, -0.5f, -0.5f, 1, 1, 1),
            PartPose.offset(0, -3, 0));
        PartDefinition p9 = p5.addOrReplaceChild("string_right", CubeListBuilder.create()
                .texOffs(108, 69).addBox(0, -0.5f, -0.5f, 1, 1, 1),
            PartPose.offset(0, -3, 0));
        PartDefinition p10 = p5.addOrReplaceChild("slider", CubeListBuilder.create()
                .texOffs(210, 61).addBox(-2, -1, -1, 4, 1, 4)
                .texOffs(102, 69).addBox(-1, -2, -0.5f, 2, 1, 1),
            PartPose.offset(0, -3, 11));
        PartDefinition p11 = p10.addOrReplaceChild("bolt", CubeListBuilder.create()
                .texOffs(84, 35).addBox(-0.5f, -2, -24, 1, 1, 24)
                .texOffs(226, 61).addBox(-1, -2.5f, -27, 2, 2, 3)
                .texOffs(236, 61).addBox(-0.5f, -3.5f, -4, 1, 2, 3),
            PartPose.offset(0, 0, 0));
        PartDefinition p12 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(170, 61).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-15, 18, -10));
        PartDefinition p13 = p12.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p14 = p12.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p15 = p12.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p16 = p12.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p17 = p12.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p18 = p12.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p19 = p12.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p20 = p12.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p21 = p12.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p22 = p12.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p23 = p12.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p24 = p12.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p25 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(170, 61).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(15, 18, -10));
        PartDefinition p26 = p25.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p27 = p25.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p28 = p25.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p29 = p25.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p30 = p25.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p31 = p25.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p32 = p25.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p33 = p25.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p34 = p25.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p35 = p25.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p36 = p25.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p37 = p25.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p38 = root.addOrReplaceChild("wheel2", CubeListBuilder.create()
                .texOffs(170, 61).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-15, 18, 10));
        PartDefinition p39 = p38.addOrReplaceChild("wheel2_felloe0", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p40 = p38.addOrReplaceChild("wheel2_felloe1", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p41 = p38.addOrReplaceChild("wheel2_felloe2", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p42 = p38.addOrReplaceChild("wheel2_felloe3", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p43 = p38.addOrReplaceChild("wheel2_felloe4", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p44 = p38.addOrReplaceChild("wheel2_felloe5", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p45 = p38.addOrReplaceChild("wheel2_felloe6", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p46 = p38.addOrReplaceChild("wheel2_felloe7", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p47 = p38.addOrReplaceChild("wheel2_spoke0", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p48 = p38.addOrReplaceChild("wheel2_spoke1", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p49 = p38.addOrReplaceChild("wheel2_spoke2", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p50 = p38.addOrReplaceChild("wheel2_spoke3", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p51 = root.addOrReplaceChild("wheel3", CubeListBuilder.create()
                .texOffs(170, 61).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(15, 18, 10));
        PartDefinition p52 = p51.addOrReplaceChild("wheel3_felloe0", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p53 = p51.addOrReplaceChild("wheel3_felloe1", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p54 = p51.addOrReplaceChild("wheel3_felloe2", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p55 = p51.addOrReplaceChild("wheel3_felloe3", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p56 = p51.addOrReplaceChild("wheel3_felloe4", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p57 = p51.addOrReplaceChild("wheel3_felloe5", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p58 = p51.addOrReplaceChild("wheel3_felloe6", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p59 = p51.addOrReplaceChild("wheel3_felloe7", CubeListBuilder.create()
                .texOffs(20, 61).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(132, 61).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p60 = p51.addOrReplaceChild("wheel3_spoke0", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p61 = p51.addOrReplaceChild("wheel3_spoke1", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p62 = p51.addOrReplaceChild("wheel3_spoke2", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p63 = p51.addOrReplaceChild("wheel3_spoke3", CubeListBuilder.create()
                .texOffs(236, 35).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }
}
