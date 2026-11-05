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
 * A trebuchet on four wheels: a frame of beams, two uprights braced fore and aft, an iron axle on them and on it the throwing arm, iron-banded, with a box of stones at its short end and a sling at its long end; a winch at the back to wind it down and a trough for the sling.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class TrebuchetMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/trebuchet.png");

    private TrebuchetMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-16, 10, -24, 3, 4, 48)
                .texOffs(0, 0).addBox(13, 10, -24, 3, 4, 48)
                .texOffs(60, 98).addBox(-13, 10, -24, 26, 4, 3)
                .texOffs(60, 98).addBox(-13, 10, 21, 26, 4, 3)
                .texOffs(0, 98).addBox(-13, 11, -2, 26, 3, 4)
                .texOffs(158, 98).addBox(-16.5f, 14, -18, 4, 3, 4)
                .texOffs(158, 98).addBox(-16.5f, 14, 14, 4, 3, 4)
                .texOffs(158, 98).addBox(12.5f, 14, -18, 4, 3, 4)
                .texOffs(158, 98).addBox(12.5f, 14, 14, 4, 3, 4)
                .texOffs(0, 105).addBox(-20, 15, -17, 40, 2, 2)
                .texOffs(0, 105).addBox(-20, 15, 15, 40, 2, 2)
                .texOffs(102, 0).addBox(-13, -37, -2, 4, 47, 4)
                .texOffs(180, 98).addBox(-13, 5, -2, 4, 2, 4, new CubeDeformation(0.3f))
                .texOffs(180, 98).addBox(-13, -20, -2, 4, 2, 4, new CubeDeformation(0.3f))
                .texOffs(118, 98).addBox(-13.5f, -39, -2.5f, 5, 2, 5)
                .texOffs(102, 0).addBox(9, -37, -2, 4, 47, 4)
                .texOffs(180, 98).addBox(9, 5, -2, 4, 2, 4, new CubeDeformation(0.3f))
                .texOffs(180, 98).addBox(9, -20, -2, 4, 2, 4, new CubeDeformation(0.3f))
                .texOffs(118, 98).addBox(8.5f, -39, -2.5f, 5, 2, 5)
                .texOffs(84, 105).addBox(-15, -36, -1, 30, 2, 2)
                .texOffs(120, 52).addBox(-4, 8, 2, 8, 2, 19)
                .texOffs(174, 52).addBox(-4, 6, 2, 1, 2, 19)
                .texOffs(174, 52).addBox(3, 6, 2, 1, 2, 19)
                .texOffs(218, 52).addBox(-12, 0, 21, 2, 10, 3)
                .texOffs(218, 52).addBox(10, 0, 21, 2, 10, 3)
                .texOffs(164, 86).addBox(-10, 1, 20.5f, 20, 4, 4)
                .texOffs(44, 86).addBox(-6, 0.6f, 20.1f, 12, 4.8f, 4.8f)
                .texOffs(158, 86).addBox(-14.5f, -1, 22, 2, 8, 1)
                .texOffs(118, 86).addBox(-14.5f, 2.5f, 18.5f, 2, 1, 8)
                .texOffs(158, 86).addBox(12.5f, -1, 22, 2, 8, 1)
                .texOffs(118, 86).addBox(12.5f, 2.5f, 18.5f, 2, 1, 8),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("brace-11_-1", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-1.5f, -38.7072f, -1.5f, 3, 38.7072f, 3),
            PartPose.offsetAndRotation(-11, 10, -21, 0.4983f, -3.1416f, 0));
        PartDefinition p2 = p0.addOrReplaceChild("brace-11_1", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-1.5f, -38.7072f, -1.5f, 3, 38.7072f, 3),
            PartPose.offsetAndRotation(-11, 10, 21, 0.4983f, -0, 0));
        PartDefinition p3 = p0.addOrReplaceChild("brace11_-1", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-1.5f, -38.7072f, -1.5f, 3, 38.7072f, 3),
            PartPose.offsetAndRotation(11, 10, -21, 0.4983f, -3.1416f, 0));
        PartDefinition p4 = p0.addOrReplaceChild("brace11_1", CubeListBuilder.create()
                .texOffs(204, 0).addBox(-1.5f, -38.7072f, -1.5f, 3, 38.7072f, 3),
            PartPose.offsetAndRotation(11, 10, 21, 0.4983f, -0, 0));
        PartDefinition p5 = root.addOrReplaceChild("arm", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-2, -2, -14, 4, 4, 30)
                .texOffs(118, 0).addBox(-1.5f, -1.5f, 16, 3, 3, 40)
                .texOffs(78, 86).addBox(-2.5f, -2.5f, -2.5f, 5, 5, 5)
                .texOffs(196, 98).addBox(-2, -2, -11, 4, 4, 2, new CubeDeformation(0.3f))
                .texOffs(196, 98).addBox(-2, -2, 7, 4, 4, 2, new CubeDeformation(0.3f))
                .texOffs(196, 98).addBox(-2, -2, 13, 4, 4, 2, new CubeDeformation(0.3f))
                .texOffs(220, 98).addBox(-1.5f, -1.5f, 28, 3, 3, 2, new CubeDeformation(0.3f))
                .texOffs(220, 98).addBox(-1.5f, -1.5f, 44, 3, 3, 2, new CubeDeformation(0.3f))
                .texOffs(230, 98).addBox(-1.5f, -1.5f, 54, 3, 3, 2, new CubeDeformation(0.3f)),
            PartPose.offsetAndRotation(0, -35, 0, -0.6109f, 0, 0));
        PartDefinition p6 = p5.addOrReplaceChild("weight", CubeListBuilder.create()
                .texOffs(148, 105).addBox(-7.5f, -0.5f, -0.5f, 15, 1, 1)
                .texOffs(174, 98).addBox(-6.5f, -1, -1, 1, 5, 2)
                .texOffs(174, 98).addBox(5.5f, -1, -1, 1, 5, 2)
                .texOffs(68, 52).addBox(-7, 3, -6, 14, 12, 12)
                .texOffs(0, 86).addBox(-6, 1.5f, -5, 12, 2, 10),
            PartPose.offset(0, 0, -12));
        PartDefinition p7 = p5.addOrReplaceChild("sling", CubeListBuilder.create()
                .texOffs(228, 52).addBox(-1.5f, 0, -0.5f, 1, 12, 1)
                .texOffs(228, 52).addBox(0.5f, 0, -0.5f, 1, 12, 1)
                .texOffs(138, 98).addBox(-2.5f, 11, -2.5f, 5, 2, 5),
            PartPose.offset(0, 0, 55));
        PartDefinition p8 = p7.addOrReplaceChild("stone", CubeListBuilder.create()
                .texOffs(212, 86).addBox(-2, 0, -2, 4, 4, 4),
            PartPose.offset(0, 7.5f, 0));
        PartDefinition p9 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(208, 98).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-21, 16, -16));
        PartDefinition p10 = p9.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p11 = p9.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p12 = p9.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p13 = p9.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p14 = p9.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p15 = p9.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p16 = p9.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p17 = p9.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p18 = p9.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p19 = p9.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p20 = p9.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p21 = p9.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p22 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(208, 98).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(21, 16, -16));
        PartDefinition p23 = p22.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p24 = p22.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p25 = p22.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p26 = p22.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p27 = p22.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p28 = p22.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p29 = p22.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p30 = p22.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p31 = p22.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p32 = p22.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p33 = p22.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p34 = p22.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p35 = root.addOrReplaceChild("wheel2", CubeListBuilder.create()
                .texOffs(208, 98).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-21, 16, 16));
        PartDefinition p36 = p35.addOrReplaceChild("wheel2_felloe0", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p37 = p35.addOrReplaceChild("wheel2_felloe1", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p38 = p35.addOrReplaceChild("wheel2_felloe2", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p39 = p35.addOrReplaceChild("wheel2_felloe3", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p40 = p35.addOrReplaceChild("wheel2_felloe4", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p41 = p35.addOrReplaceChild("wheel2_felloe5", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p42 = p35.addOrReplaceChild("wheel2_felloe6", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p43 = p35.addOrReplaceChild("wheel2_felloe7", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p44 = p35.addOrReplaceChild("wheel2_spoke0", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p45 = p35.addOrReplaceChild("wheel2_spoke1", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p46 = p35.addOrReplaceChild("wheel2_spoke2", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p47 = p35.addOrReplaceChild("wheel2_spoke3", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p48 = root.addOrReplaceChild("wheel3", CubeListBuilder.create()
                .texOffs(208, 98).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(21, 16, 16));
        PartDefinition p49 = p48.addOrReplaceChild("wheel3_felloe0", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p50 = p48.addOrReplaceChild("wheel3_felloe1", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p51 = p48.addOrReplaceChild("wheel3_felloe2", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p52 = p48.addOrReplaceChild("wheel3_felloe3", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p53 = p48.addOrReplaceChild("wheel3_felloe4", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p54 = p48.addOrReplaceChild("wheel3_felloe5", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p55 = p48.addOrReplaceChild("wheel3_felloe6", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p56 = p48.addOrReplaceChild("wheel3_felloe7", CubeListBuilder.create()
                .texOffs(98, 86).addBox(-1, -8, -3.6137f, 2, 1.6f, 7.2274f)
                .texOffs(138, 86).addBox(-1, -8.01f, -3.6137f, 2, 0.6f, 7.2274f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p57 = p48.addOrReplaceChild("wheel3_spoke0", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p58 = p48.addOrReplaceChild("wheel3_spoke1", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p59 = p48.addOrReplaceChild("wheel3_spoke2", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p60 = p48.addOrReplaceChild("wheel3_spoke3", CubeListBuilder.create()
                .texOffs(214, 52).addBox(-0.5f, -7, -0.5f, 1, 14, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }
}
