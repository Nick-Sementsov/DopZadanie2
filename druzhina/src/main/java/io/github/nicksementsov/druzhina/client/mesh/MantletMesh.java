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
 * A mobile wall: upright boards with loopholes, iron-banded, on an axle with two wheels at the foot, held up by two props behind; men walk behind it and shoot through the slits.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class MantletMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/mantlet.png");

    private MantletMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("wall", CubeListBuilder.create()
                .texOffs(26, 0).addBox(-22, -18, -3, 44, 34, 3)
                .texOffs(110, 42).addBox(-23, -15, -4, 46, 3, 1)
                .texOffs(0, 49).addBox(-23, -15, 0, 46, 3, 1)
                .texOffs(110, 42).addBox(-23, 9, -4, 46, 3, 1)
                .texOffs(0, 49).addBox(-23, 9, 0, 46, 3, 1)
                .texOffs(124, 0).addBox(-23, -21, -4, 46, 3, 5)
                .texOffs(0, 0).addBox(-23, -21, -4, 2, 37, 5)
                .texOffs(0, 0).addBox(21, -21, -4, 2, 37, 5)
                .texOffs(94, 49).addBox(-17, 15, -2, 34, 2, 2)
                .texOffs(0, 42).addBox(-20, -26, -3.5f, 40, 6, 1),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("prop-18", CubeListBuilder.create()
                .texOffs(14, 0).addBox(-1.25f, -38.5876f, -1.25f, 2.5f, 38.5876f, 2.5f),
            PartPose.offsetAndRotation(-17, -10, 0, 2.5967f, -3.1416f, 0));
        PartDefinition p2 = p0.addOrReplaceChild("prop16", CubeListBuilder.create()
                .texOffs(14, 0).addBox(-1.25f, -38.5876f, -1.25f, 2.5f, 38.5876f, 2.5f),
            PartPose.offsetAndRotation(17, -10, 0, 2.5967f, -3.1416f, 0));
        PartDefinition p3 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(98, 42).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(-19, 18, -1));
        PartDefinition p4 = p3.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p5 = p3.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p6 = p3.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p7 = p3.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p8 = p3.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p9 = p3.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p10 = p3.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p11 = p3.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p12 = p3.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p13 = p3.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p14 = p3.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p15 = p3.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        PartDefinition p16 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(98, 42).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3),
            PartPose.offset(19, 18, -1));
        PartDefinition p17 = p16.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p18 = p16.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p19 = p16.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p20 = p16.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p21 = p16.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p22 = p16.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p23 = p16.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p24 = p16.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(226, 0).addBox(-1, -6, -2.7853f, 2, 1.6f, 5.5706f)
                .texOffs(82, 42).addBox(-1, -6.01f, -2.7853f, 2, 0.6f, 5.5706f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p25 = p16.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p26 = p16.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.1781f, 0, 0));
        PartDefinition p27 = p16.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p28 = p16.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(120, 0).addBox(-0.5f, -5, -0.5f, 1, 10, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.7489f, 0, 0));
        return LayerDefinition.create(mesh, 256, 64);
    }
}
