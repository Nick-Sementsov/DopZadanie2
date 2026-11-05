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
 * A siege tower on four wheels: four corner posts, three floors, walls of boards with battens, wet hides over the front, a ladder up through it seen through the door at the back, a crenellated parapet at the top and a drawbridge before it that is let down onto the wall.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class SiegeTowerMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/siege_tower.png");

    private SiegeTowerMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(84, 104).addBox(-20, 12, -20, 4, 4, 40)
                .texOffs(84, 104).addBox(16, 12, -20, 4, 4, 40)
                .texOffs(80, 268).addBox(-16, 12, -20, 32, 4, 4)
                .texOffs(80, 268).addBox(-16, 12, 16, 32, 4, 4)
                .texOffs(0, 276).addBox(-22, 17, -14, 44, 2, 2)
                .texOffs(0, 276).addBox(-22, 17, 10, 44, 2, 2)
                .texOffs(66, 0).addBox(-20, -64, -20, 4, 76, 4)
                .texOffs(66, 0).addBox(-20, -64, 16, 4, 76, 4)
                .texOffs(66, 0).addBox(16, -64, -20, 4, 76, 4)
                .texOffs(66, 0).addBox(16, -64, 16, 4, 76, 4)
                .texOffs(0, 234).addBox(-16, 10, -16, 32, 2, 32)
                .texOffs(0, 150).addBox(-20, -22, -20, 40, 2, 40)
                .texOffs(0, 192).addBox(-20, -66, -20, 40, 2, 40)
                .texOffs(0, 0).addBox(-20.8f, -62, -16, 1, 72, 32)
                .texOffs(0, 0).addBox(19.8f, -62, -16, 1, 72, 32)
                .texOffs(86, 0).addBox(-16, -62, -21.2f, 32, 72, 1)
                .texOffs(152, 0).addBox(-16, -62, 20, 32, 50, 1)
                .texOffs(188, 234).addBox(-16, -12, 20, 11, 22, 1)
                .texOffs(188, 234).addBox(5, -12, 20, 11, 22, 1)
                .texOffs(172, 104).addBox(-21.6f, -26, -20, 1, 3, 40)
                .texOffs(172, 104).addBox(20.6f, -26, -20, 1, 3, 40)
                .texOffs(92, 276).addBox(-20, -26, 20.6f, 40, 3, 1)
                .texOffs(172, 104).addBox(-21.6f, -58, -20, 1, 3, 40)
                .texOffs(172, 104).addBox(20.6f, -58, -20, 1, 3, 40)
                .texOffs(92, 276).addBox(-20, -58, 20.6f, 40, 3, 1)
                .texOffs(172, 104).addBox(-21.6f, 6, -20, 1, 3, 40)
                .texOffs(172, 104).addBox(20.6f, 6, -20, 1, 3, 40)
                .texOffs(92, 276).addBox(-20, 6, 20.6f, 40, 3, 1)
                .texOffs(82, 0).addBox(-3, -64, 16, 1, 74, 1)
                .texOffs(82, 0).addBox(2, -64, 16, 1, 74, 1)
                .texOffs(58, 280).addBox(-2, -60, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -54, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -48, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -42, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -36, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -30, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -24, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -18, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -12, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, -6, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, 0, 16, 4, 1, 1)
                .texOffs(58, 280).addBox(-2, 6, 16, 4, 1, 1)
                .texOffs(0, 104).addBox(-20.5f, -72, -20, 1.5f, 6, 40)
                .texOffs(216, 234).addBox(-20.5f, -76, -18, 1.5f, 4, 6)
                .texOffs(216, 234).addBox(-20.5f, -76, -6, 1.5f, 4, 6)
                .texOffs(216, 234).addBox(-20.5f, -76, 6, 1.5f, 4, 6)
                .texOffs(0, 104).addBox(19, -72, -20, 1.5f, 6, 40)
                .texOffs(216, 234).addBox(19, -76, -18, 1.5f, 4, 6)
                .texOffs(216, 234).addBox(19, -76, -6, 1.5f, 4, 6)
                .texOffs(216, 234).addBox(19, -76, 6, 1.5f, 4, 6)
                .texOffs(0, 268).addBox(-19, -72, 18.5f, 38, 6, 1.5f)
                .texOffs(172, 268).addBox(-17, -76, 18.5f, 6, 4, 1.5f)
                .texOffs(172, 268).addBox(-5, -76, 18.5f, 6, 4, 1.5f)
                .texOffs(172, 268).addBox(7, -76, 18.5f, 6, 4, 1.5f),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = root.addOrReplaceChild("bridge", CubeListBuilder.create()
                .texOffs(128, 234).addBox(-14, -26, -2, 28, 26, 2)
                .texOffs(0, 280).addBox(-14, -24, -2.8f, 28, 2, 1)
                .texOffs(0, 280).addBox(-14, -13, -2.8f, 28, 2, 1)
                .texOffs(0, 280).addBox(-14, -3, -2.8f, 28, 2, 1)
                .texOffs(174, 276).addBox(-14, -27, -2.2f, 28, 1, 2.4f)
                .texOffs(68, 280).addBox(-15, -26, -1, 2, 1, 1)
                .texOffs(68, 280).addBox(13, -26, -1, 2, 1, 1),
            PartPose.offset(0, -64, -20));
        PartDefinition p2 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(188, 268).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(-23, 17, -12));
        PartDefinition p3 = p2.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p4 = p2.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p5 = p2.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p6 = p2.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p7 = p2.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p8 = p2.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p9 = p2.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p10 = p2.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p11 = p2.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p12 = p2.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p13 = p2.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p14 = p2.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p15 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(188, 268).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(23, 17, -12));
        PartDefinition p16 = p15.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p17 = p15.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p18 = p15.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p19 = p15.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p20 = p15.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p21 = p15.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p22 = p15.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p23 = p15.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p24 = p15.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p25 = p15.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p26 = p15.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p27 = p15.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p28 = root.addOrReplaceChild("wheel2", CubeListBuilder.create()
                .texOffs(188, 268).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(-23, 17, 12));
        PartDefinition p29 = p28.addOrReplaceChild("wheel2_felloe0", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p30 = p28.addOrReplaceChild("wheel2_felloe1", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p31 = p28.addOrReplaceChild("wheel2_felloe2", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p32 = p28.addOrReplaceChild("wheel2_felloe3", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p33 = p28.addOrReplaceChild("wheel2_felloe4", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p34 = p28.addOrReplaceChild("wheel2_felloe5", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p35 = p28.addOrReplaceChild("wheel2_felloe6", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p36 = p28.addOrReplaceChild("wheel2_felloe7", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p37 = p28.addOrReplaceChild("wheel2_spoke0", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p38 = p28.addOrReplaceChild("wheel2_spoke1", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p39 = p28.addOrReplaceChild("wheel2_spoke2", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p40 = p28.addOrReplaceChild("wheel2_spoke3", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p41 = root.addOrReplaceChild("wheel3", CubeListBuilder.create()
                .texOffs(188, 268).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(23, 17, 12));
        PartDefinition p42 = p41.addOrReplaceChild("wheel3_felloe0", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p43 = p41.addOrReplaceChild("wheel3_felloe1", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p44 = p41.addOrReplaceChild("wheel3_felloe2", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p45 = p41.addOrReplaceChild("wheel3_felloe3", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p46 = p41.addOrReplaceChild("wheel3_felloe4", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p47 = p41.addOrReplaceChild("wheel3_felloe5", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p48 = p41.addOrReplaceChild("wheel3_felloe6", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p49 = p41.addOrReplaceChild("wheel3_felloe7", CubeListBuilder.create()
                .texOffs(232, 234).addBox(-1.5f, -7, -3.1995f, 3, 1.6f, 6.399f)
                .texOffs(152, 268).addBox(-1.5f, -7.01f, -3.1995f, 3, 0.6f, 6.399f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p50 = p41.addOrReplaceChild("wheel3_spoke0", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p51 = p41.addOrReplaceChild("wheel3_spoke1", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p52 = p41.addOrReplaceChild("wheel3_spoke2", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p53 = p41.addOrReplaceChild("wheel3_spoke3", CubeListBuilder.create()
                .texOffs(212, 234).addBox(-0.5f, -6, -0.5f, 1, 12, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 512);
    }
}
