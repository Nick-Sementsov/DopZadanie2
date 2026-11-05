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
 * A mangonel: a frame of beams on four wheels, a thick skein of twisted rope across it, and in the skein the throwing arm with an iron-bound cup; it is wound back by a winch and strikes a padded crossbar when it throws.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class MangonelMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/mangonel.png");

    private MangonelMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-11, 10, -20, 3, 4, 40)
                .texOffs(0, 0).addBox(8, 10, -20, 3, 4, 40)
                .texOffs(82, 44).addBox(-8, 10, -20, 16, 4, 3)
                .texOffs(82, 44).addBox(-8, 10, 17, 16, 4, 3)
                .texOffs(74, 52).addBox(-15, 15, -14, 30, 2, 2)
                .texOffs(74, 52).addBox(-15, 15, 12, 30, 2, 2)
                .texOffs(128, 0).addBox(-8, 9.5f, 1, 16, 5, 5)
                .texOffs(110, 0).addBox(-11.5f, 9, 0.5f, 3, 6, 6)
                .texOffs(110, 0).addBox(8.5f, 9, 0.5f, 3, 6, 6)
                .texOffs(98, 0).addBox(-11, -15, -6, 3, 25, 3)
                .texOffs(98, 0).addBox(8, -15, -6, 3, 25, 3)
                .texOffs(150, 44).addBox(-11, -18, -6, 22, 3, 3)
                .texOffs(56, 44).addBox(-5, -19, -8, 10, 5, 3)
                .texOffs(12, 52).addBox(-11, -17.5f, -6, 22, 2, 3, new CubeDeformation(0.2f))
                .texOffs(220, 0).addBox(-10, 3, 13, 2, 7, 3)
                .texOffs(220, 0).addBox(8, 3, 13, 2, 7, 3)
                .texOffs(200, 44).addBox(-8, 4, 13, 16, 3, 3)
                .texOffs(0, 44).addBox(-5, 3.8f, 12.8f, 10, 3.4f, 3.4f)
                .texOffs(230, 0).addBox(-12, 1, 14, 2, 9, 1)
                .texOffs(198, 0).addBox(-12, 5, 10, 2, 1, 9)
                .texOffs(230, 0).addBox(10, 1, 14, 2, 9, 1)
                .texOffs(198, 0).addBox(10, 5, 10, 2, 1, 9),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("prop-9.5", CubeListBuilder.create()
                .texOffs(86, 0).addBox(-1.25f, -25.5539f, -1.25f, 2.5f, 25.5539f, 2.5f),
            PartPose.offsetAndRotation(-9.5f, 10, -19, 0.5337f, -3.1416f, 0));
        PartDefinition p2 = p0.addOrReplaceChild("prop9.5", CubeListBuilder.create()
                .texOffs(86, 0).addBox(-1.25f, -25.5539f, -1.25f, 2.5f, 25.5539f, 2.5f),
            PartPose.offsetAndRotation(9.5f, 10, -19, 0.5337f, -3.1416f, 0));
        PartDefinition p3 = root.addOrReplaceChild("arm", CubeListBuilder.create()
                .texOffs(86, 0).addBox(-1.5f, -26, -1.5f, 3, 26, 3)
                .texOffs(236, 0).addBox(-2, -3, -2, 4, 5, 4)
                .texOffs(62, 52).addBox(-1.5f, -15, -1.5f, 3, 2, 3, new CubeDeformation(0.3f))
                .texOffs(170, 0).addBox(-3.5f, -30, -3.5f, 7, 3, 7)
                .texOffs(28, 44).addBox(-3.5f, -31, -3.5f, 7, 1, 7, new CubeDeformation(0.2f)),
            PartPose.offsetAndRotation(0, 12, 3.5f, -1.3614f, 0, 0));
        PartDefinition p4 = p3.addOrReplaceChild("pot", CubeListBuilder.create()
                .texOffs(120, 44).addBox(-2, -3, -2, 4, 3, 4)
                .texOffs(138, 52).addBox(-1, -4, -1, 2, 1, 2),
            PartPose.offset(0, -31, 0));
        PartDefinition p5 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-14, 19, -13));
        PartDefinition p6 = p5.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p7 = p5.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p8 = p5.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p9 = p5.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p10 = p5.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p11 = p5.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p12 = p5.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p13 = p5.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p14 = p5.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p15 = p5.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p16 = p5.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p17 = p5.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p18 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(14, 19, -13));
        PartDefinition p19 = p18.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p20 = p18.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p21 = p18.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p22 = p18.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p23 = p18.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p24 = p18.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p25 = p18.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p26 = p18.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p27 = p18.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p28 = p18.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p29 = p18.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p30 = p18.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p31 = root.addOrReplaceChild("wheel2", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-14, 19, 13));
        PartDefinition p32 = p31.addOrReplaceChild("wheel2_felloe0", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p33 = p31.addOrReplaceChild("wheel2_felloe1", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p34 = p31.addOrReplaceChild("wheel2_felloe2", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p35 = p31.addOrReplaceChild("wheel2_felloe3", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p36 = p31.addOrReplaceChild("wheel2_felloe4", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p37 = p31.addOrReplaceChild("wheel2_felloe5", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p38 = p31.addOrReplaceChild("wheel2_felloe6", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p39 = p31.addOrReplaceChild("wheel2_felloe7", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p40 = p31.addOrReplaceChild("wheel2_spoke0", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p41 = p31.addOrReplaceChild("wheel2_spoke1", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p42 = p31.addOrReplaceChild("wheel2_spoke2", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p43 = p31.addOrReplaceChild("wheel2_spoke3", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p44 = root.addOrReplaceChild("wheel3", CubeListBuilder.create()
                .texOffs(0, 52).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(14, 19, 13));
        PartDefinition p45 = p44.addOrReplaceChild("wheel3_felloe0", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p46 = p44.addOrReplaceChild("wheel3_felloe1", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p47 = p44.addOrReplaceChild("wheel3_felloe2", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p48 = p44.addOrReplaceChild("wheel3_felloe3", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p49 = p44.addOrReplaceChild("wheel3_felloe4", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p50 = p44.addOrReplaceChild("wheel3_felloe5", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p51 = p44.addOrReplaceChild("wheel3_felloe6", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p52 = p44.addOrReplaceChild("wheel3_felloe7", CubeListBuilder.create()
                .texOffs(136, 44).addBox(-1, -5, -2.3711f, 2, 1.6f, 4.7421f)
                .texOffs(238, 44).addBox(-1, -5.01f, -2.3711f, 2, 0.6f, 4.7421f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p53 = p44.addOrReplaceChild("wheel3_spoke0", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p54 = p44.addOrReplaceChild("wheel3_spoke1", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p55 = p44.addOrReplaceChild("wheel3_spoke2", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p56 = p44.addOrReplaceChild("wheel3_spoke3", CubeListBuilder.create()
                .texOffs(252, 0).addBox(-0.5f, -4, -0.5f, 1, 8, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 64);
    }
}
