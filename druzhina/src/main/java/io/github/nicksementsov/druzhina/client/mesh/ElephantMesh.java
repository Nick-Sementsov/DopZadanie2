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
 * A war elephant of India: a heavy body rising to the shoulders, wrinkled grey skin, pillar legs with broad feet and toenails, a domed head with an iron brow plate, fanning ears, a trunk of five tapering pieces and curved tusks; over it a crimson caparison with a tarnished gold border and tassels, a girth, and a howdah of dark wood with a railing, posts and a canopy.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class ElephantMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/war_elephant.png");

    private ElephantMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-10, -6, -14, 20, 16, 28)
                .texOffs(0, 0).addBox(-9, -9, -13, 18, 21, 26)
                .texOffs(0, 47).addBox(-10.8f, -4, -12, 21.6f, 12, 24)
                .texOffs(154, 47).addBox(-8.5f, -10.5f, -11, 17, 3, 19)
                .texOffs(128, 124).addBox(-8, -3, -15.6f, 16, 12, 2)
                .texOffs(60, 124).addBox(-8, -4, 13.6f, 16, 13, 2)
                .texOffs(120, 83).addBox(-7.5f, 11.5f, -9, 15, 2, 18)
                .texOffs(0, 105).addBox(-10, -11.2f, -9.5f, 20, 1, 18)
                .texOffs(92, 47).addBox(-11.5f, -10.6f, -9.5f, 0.6f, 15, 18)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, -8.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, -5.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, -2.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, 0.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, 3.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(-11.5f, 4.4f, 6.5f, 0.6f, 2, 1)
                .texOffs(92, 47).addBox(10.9f, -10.6f, -9.5f, 0.6f, 15, 18)
                .texOffs(182, 151).addBox(10.9f, 4.4f, -8.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(10.9f, 4.4f, -5.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(10.9f, 4.4f, -2.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(10.9f, 4.4f, 0.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(10.9f, 4.4f, 3.5f, 0.6f, 2, 1)
                .texOffs(182, 151).addBox(10.9f, 4.4f, 6.5f, 0.6f, 2, 1)
                .texOffs(38, 151).addBox(-11.2f, -4, -1, 22.4f, 1.5f, 2, new CubeDeformation(0.1f))
                .texOffs(104, 151).addBox(-10.2f, 11.6f, -1, 20.4f, 1, 2),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(164, 124).addBox(-0.5f, 0, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, -5, 15.5f, 0.2443f, 0, 0));
        PartDefinition p2 = p1.addOrReplaceChild("tuft", CubeListBuilder.create()
                .texOffs(22, 151).addBox(-1, 0, -1, 2, 3, 2),
            PartPose.offset(0, 12, 0));
        PartDefinition p3 = p0.addOrReplaceChild("howdah", CubeListBuilder.create()
                .texOffs(76, 105).addBox(-9, -2, -8, 18, 2, 16)
                .texOffs(186, 151).addBox(-9, -7, -8.5f, 18, 1, 1)
                .texOffs(0, 157).addBox(-9, -3, -8.5f, 18, 1, 1)
                .texOffs(100, 151).addBox(-7, -6, -8.25f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-4, -6, -8.25f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-1, -6, -8.25f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(2, -6, -8.25f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(5, -6, -8.25f, 0.6f, 3, 0.6f)
                .texOffs(186, 151).addBox(-9, -7, 7.5f, 18, 1, 1)
                .texOffs(0, 157).addBox(-9, -3, 7.5f, 18, 1, 1)
                .texOffs(100, 151).addBox(-7, -6, 7.75f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-4, -6, 7.75f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-1, -6, 7.75f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(2, -6, 7.75f, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(5, -6, 7.75f, 0.6f, 3, 0.6f)
                .texOffs(144, 105).addBox(-9.5f, -7, -8, 1, 1, 16)
                .texOffs(178, 105).addBox(-9.5f, -3, -8, 1, 1, 16)
                .texOffs(100, 151).addBox(-9.25f, -6, -6, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-9.25f, -6, -3, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-9.25f, -6, 0, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-9.25f, -6, 3, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(-9.25f, -6, 6, 0.6f, 3, 0.6f)
                .texOffs(144, 105).addBox(8.5f, -7, -8, 1, 1, 16)
                .texOffs(178, 105).addBox(8.5f, -3, -8, 1, 1, 16)
                .texOffs(100, 151).addBox(8.75f, -6, -6, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(8.75f, -6, -3, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(8.75f, -6, 0, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(8.75f, -6, 3, 0.6f, 3, 0.6f)
                .texOffs(100, 151).addBox(8.75f, -6, 6, 0.6f, 3, 0.6f)
                .texOffs(212, 105).addBox(-9.5f, -18, -8.5f, 1, 16, 1)
                .texOffs(150, 151).addBox(-9.7f, -19, -8.7f, 1.4f, 1, 1.4f)
                .texOffs(212, 105).addBox(-9.5f, -18, 7.5f, 1, 16, 1)
                .texOffs(150, 151).addBox(-9.7f, -19, 7.3f, 1.4f, 1, 1.4f)
                .texOffs(212, 105).addBox(8.5f, -18, -8.5f, 1, 16, 1)
                .texOffs(150, 151).addBox(8.3f, -19, -8.7f, 1.4f, 1, 1.4f)
                .texOffs(212, 105).addBox(8.5f, -18, 7.5f, 1, 16, 1)
                .texOffs(150, 151).addBox(8.3f, -19, 7.3f, 1.4f, 1, 1.4f)
                .texOffs(40, 83).addBox(-10.5f, -20, -9.5f, 21, 1, 19)
                .texOffs(0, 124).addBox(-8, -22, -7, 16, 2, 14)
                .texOffs(20, 140).addBox(-5, -24, -4, 10, 2, 8)
                .texOffs(30, 151).addBox(-1, -27, -1, 2, 3, 2)
                .texOffs(0, 83).addBox(-10.4f, -19, -9.5f, 0.6f, 3, 19)
                .texOffs(0, 83).addBox(9.8f, -19, -9.5f, 0.6f, 3, 19),
            PartPose.offset(0, -11.2f, 1));
        PartDefinition p4 = p0.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(186, 83).addBox(-6.5f, -4, -9, 13, 11, 9)
                .texOffs(196, 124).addBox(-5.5f, -8, -7.5f, 5.2f, 4, 7)
                .texOffs(196, 124).addBox(0.3f, -8, -7.5f, 5.2f, 4, 7)
                .texOffs(88, 140).addBox(-4, -3, -9.8f, 8, 9, 1)
                .texOffs(140, 140).addBox(-3, 7, -6, 6, 2.5f, 5)
                .texOffs(158, 151).addBox(-6.7f, -1, -6.5f, 0.5f, 1, 1.5f)
                .texOffs(158, 151).addBox(6.2f, -1, -6.5f, 0.5f, 1, 1.5f)
                .texOffs(106, 140).addBox(-4, -8.5f, -10.4f, 8, 8, 0.6f)
                .texOffs(162, 140).addBox(-3.5f, -8, -10.9f, 7, 7, 0.7f)
                .texOffs(164, 151).addBox(-0.5f, -5, -12.2f, 1, 1, 1.4f)
                .texOffs(210, 140).addBox(-6, -8.6f, -4, 12, 1, 5),
            PartPose.offset(0, -3, -16));
        PartDefinition p5 = p4.addOrReplaceChild("ear_left", CubeListBuilder.create()
                .texOffs(130, 47).addBox(0, 0, 0, 1, 13, 11),
            PartPose.offsetAndRotation(6.5f, -4, -5, 0, 0.5236f, 0));
        PartDefinition p6 = p4.addOrReplaceChild("ear_right", CubeListBuilder.create()
                .mirror(true)
                .texOffs(130, 47).addBox(-1, 0, 0, 1, 13, 11),
            PartPose.offsetAndRotation(-6.5f, -4, -5, 0, -0.5236f, 0));
        PartDefinition p7 = p4.addOrReplaceChild("trunk1", CubeListBuilder.create()
                .texOffs(222, 124).addBox(-2.5f, 0, -2.5f, 5, 6, 5),
            PartPose.offsetAndRotation(0, 5, -7.5f, 0.1396f, 0, 0));
        PartDefinition p8 = p7.addOrReplaceChild("trunk2", CubeListBuilder.create()
                .texOffs(0, 140).addBox(-2.2f, 0, -2.2f, 4.4f, 6, 4.4f),
            PartPose.offsetAndRotation(0, 5.6f, 0, 0.1047f, 0, 0));
        PartDefinition p9 = p8.addOrReplaceChild("trunk3", CubeListBuilder.create()
                .texOffs(124, 140).addBox(-1.9f, 0, -1.9f, 3.8f, 5, 3.8f),
            PartPose.offsetAndRotation(0, 5.6f, 0, 0.1047f, 0, 0));
        PartDefinition p10 = p9.addOrReplaceChild("trunk4", CubeListBuilder.create()
                .texOffs(178, 140).addBox(-1.6f, 0, -1.6f, 3.2f, 4, 3.2f),
            PartPose.offsetAndRotation(0, 4.6f, 0, -0.2094f, 0, 0));
        PartDefinition p11 = p10.addOrReplaceChild("trunk5", CubeListBuilder.create()
                .texOffs(244, 140).addBox(-1.3f, 0, -1.3f, 2.6f, 3, 2.6f)
                .texOffs(88, 151).addBox(-1.4f, 2.6f, -1.4f, 2.8f, 0.8f, 2.8f),
            PartPose.offsetAndRotation(0, 3.6f, 0, -0.3491f, 0, 0));
        PartDefinition p12 = p4.addOrReplaceChild("tusk_left", CubeListBuilder.create()
                .texOffs(194, 140).addBox(-1.1f, -1.1f, -5, 2.2f, 2.2f, 5),
            PartPose.offsetAndRotation(3.6f, 6, -6.5f, 0.9599f, -0.1745f, 0));
        PartDefinition p13 = p12.addOrReplaceChild("tusk_left_mid", CubeListBuilder.create()
                .texOffs(0, 151).addBox(-0.9f, -0.9f, -4, 1.8f, 1.8f, 4),
            PartPose.offsetAndRotation(0, 0, -4.7f, -0.6109f, 0, 0));
        PartDefinition p14 = p13.addOrReplaceChild("tusk_left_tip", CubeListBuilder.create()
                .texOffs(12, 151).addBox(-0.7f, -0.7f, -3, 1.4f, 1.4f, 3)
                .texOffs(170, 151).addBox(-1, -1, -0.3f, 2, 2, 1, new CubeDeformation(0.1f)),
            PartPose.offsetAndRotation(0, 0, -3.8f, -0.6109f, 0, 0));
        PartDefinition p15 = p4.addOrReplaceChild("tusk_right", CubeListBuilder.create()
                .texOffs(194, 140).addBox(-1.1f, -1.1f, -5, 2.2f, 2.2f, 5),
            PartPose.offsetAndRotation(-3.6f, 6, -6.5f, 0.9599f, 0.1745f, 0));
        PartDefinition p16 = p15.addOrReplaceChild("tusk_right_mid", CubeListBuilder.create()
                .texOffs(0, 151).addBox(-0.9f, -0.9f, -4, 1.8f, 1.8f, 4),
            PartPose.offsetAndRotation(0, 0, -4.7f, -0.6109f, 0, 0));
        PartDefinition p17 = p16.addOrReplaceChild("tusk_right_tip", CubeListBuilder.create()
                .texOffs(12, 151).addBox(-0.7f, -0.7f, -3, 1.4f, 1.4f, 3)
                .texOffs(170, 151).addBox(-1, -1, -0.3f, 2, 2, 1, new CubeDeformation(0.1f)),
            PartPose.offsetAndRotation(0, 0, -3.8f, -0.6109f, 0, 0));
        PartDefinition p18 = root.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(96, 124).addBox(-4, 0, -4, 8, 7, 8)
                .texOffs(168, 124).addBox(-3.5f, 7, -3.5f, 7, 5, 7)
                .texOffs(56, 140).addBox(-4, 12, -4, 8, 2, 8)
                .texOffs(176, 151).addBox(-2.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f)
                .texOffs(176, 151).addBox(0.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f),
            PartPose.offset(6, 10, -9.5f));
        PartDefinition p19 = root.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(96, 124).addBox(-4, 0, -4, 8, 7, 8)
                .texOffs(168, 124).addBox(-3.5f, 7, -3.5f, 7, 5, 7)
                .texOffs(56, 140).addBox(-4, 12, -4, 8, 2, 8)
                .texOffs(176, 151).addBox(-2.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f)
                .texOffs(176, 151).addBox(0.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f),
            PartPose.offset(-6, 10, -9.5f));
        PartDefinition p20 = root.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(96, 124).addBox(-4, 0, -4, 8, 7, 8)
                .texOffs(168, 124).addBox(-3.5f, 7, -3.5f, 7, 5, 7)
                .texOffs(56, 140).addBox(-4, 12, -4, 8, 2, 8)
                .texOffs(176, 151).addBox(-2.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f)
                .texOffs(176, 151).addBox(0.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f),
            PartPose.offset(6, 10, 9.5f));
        PartDefinition p21 = root.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(96, 124).addBox(-4, 0, -4, 8, 7, 8)
                .texOffs(168, 124).addBox(-3.5f, 7, -3.5f, 7, 5, 7)
                .texOffs(56, 140).addBox(-4, 12, -4, 8, 2, 8)
                .texOffs(176, 151).addBox(-2.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f)
                .texOffs(176, 151).addBox(0.5f, 12.6f, -4.3f, 2, 1.4f, 0.6f),
            PartPose.offset(-6, 10, 9.5f));
        return LayerDefinition.create(mesh, 256, 256);
    }
}
