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
 * A bombard on a wheeled carriage: two cheeks with transoms and iron straps, a trail to the ground, two great wheels, and on trunnions between the cheeks the barrel of welded iron bars bound with hoops, its chamber behind.
 *
 * <p>Written by {@code tools/models.py}: change the model there and run it again, not here.
 */
@SuppressWarnings("unused")
public final class BombardMesh {
    public static final ResourceLocation TEXTURE = Druzhina.id("textures/entity/siege/bombard.png");

    private BombardMesh() {
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("carriage", CubeListBuilder.create()
                .texOffs(152, 0).addBox(-8, 6, -8, 3, 8, 24)
                .texOffs(136, 38).addBox(-8, 6, -6, 3, 8, 2, new CubeDeformation(0.3f))
                .texOffs(136, 38).addBox(-8, 6, 10, 3, 8, 2, new CubeDeformation(0.3f))
                .texOffs(152, 0).addBox(5, 6, -8, 3, 8, 24)
                .texOffs(136, 38).addBox(5, 6, -6, 3, 8, 2, new CubeDeformation(0.3f))
                .texOffs(136, 38).addBox(5, 6, 10, 3, 8, 2, new CubeDeformation(0.3f))
                .texOffs(164, 38).addBox(-5, 10, -6, 10, 3, 3)
                .texOffs(164, 38).addBox(-5, 11, 11, 10, 3, 3)
                .texOffs(0, 55).addBox(-14, 14, 3, 28, 2, 2)
                .texOffs(86, 38).addBox(-3, 2, 9, 6, 3, 7),
            PartPose.offset(0, 0, 0));
        PartDefinition p1 = p0.addOrReplaceChild("trail", CubeListBuilder.create()
                .texOffs(206, 0).addBox(-2.5f, -25.8118f, -2, 5, 25.8118f, 4),
            PartPose.offsetAndRotation(0, 13, 14, 1.9477f, -3.1416f, 0));
        PartDefinition p2 = root.addOrReplaceChild("barrel", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -5, -22, 10, 10, 28)
                .texOffs(38, 38).addBox(-5, -5, -22, 10, 10, 2, new CubeDeformation(0.45f))
                .texOffs(38, 38).addBox(-5, -5, -15, 10, 10, 2, new CubeDeformation(0.45f))
                .texOffs(38, 38).addBox(-5, -5, -8, 10, 10, 2, new CubeDeformation(0.45f))
                .texOffs(38, 38).addBox(-5, -5, -1, 10, 10, 2, new CubeDeformation(0.45f))
                .texOffs(0, 38).addBox(-3.5f, -3.5f, 6, 7, 7, 10)
                .texOffs(146, 38).addBox(-3.5f, -3.5f, 13, 7, 7, 2, new CubeDeformation(0.35f))
                .texOffs(60, 55).addBox(-6.5f, -1, -1, 13, 2, 2)
                .texOffs(90, 55).addBox(-0.5f, -4, 9, 1, 1, 1),
            PartPose.offset(0, 3, 2));
        PartDefinition p3 = p2.addOrReplaceChild("barrel45", CubeListBuilder.create()
                .texOffs(76, 0).addBox(-4.6f, -4.6f, -21.9f, 9.2f, 9.2f, 27.8f),
            PartPose.offsetAndRotation(0, 0, 0, 0, 0, 0.7854f));
        PartDefinition p4 = root.addOrReplaceChild("wheel0", CubeListBuilder.create()
                .texOffs(190, 38).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(-12, 15, 4));
        PartDefinition p5 = p4.addOrReplaceChild("wheel0_felloe0", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p6 = p4.addOrReplaceChild("wheel0_felloe1", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p7 = p4.addOrReplaceChild("wheel0_felloe2", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p8 = p4.addOrReplaceChild("wheel0_felloe3", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p9 = p4.addOrReplaceChild("wheel0_felloe4", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p10 = p4.addOrReplaceChild("wheel0_felloe5", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p11 = p4.addOrReplaceChild("wheel0_felloe6", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p12 = p4.addOrReplaceChild("wheel0_felloe7", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p13 = p4.addOrReplaceChild("wheel0_spoke0", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p14 = p4.addOrReplaceChild("wheel0_spoke1", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.9163f, 0, 0));
        PartDefinition p15 = p4.addOrReplaceChild("wheel0_spoke2", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.4399f, 0, 0));
        PartDefinition p16 = p4.addOrReplaceChild("wheel0_spoke3", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p17 = p4.addOrReplaceChild("wheel0_spoke4", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.4871f, 0, 0));
        PartDefinition p18 = p4.addOrReplaceChild("wheel0_spoke5", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 3.0107f, 0, 0));
        PartDefinition p19 = root.addOrReplaceChild("wheel1", CubeListBuilder.create()
                .texOffs(190, 38).addBox(-2, -1.5f, -1.5f, 4, 3, 3),
            PartPose.offset(12, 15, 4));
        PartDefinition p20 = p19.addOrReplaceChild("wheel1_felloe0", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offset(0, 0, 0));
        PartDefinition p21 = p19.addOrReplaceChild("wheel1_felloe1", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 0.7854f, 0, 0));
        PartDefinition p22 = p19.addOrReplaceChild("wheel1_felloe2", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 1.5708f, 0, 0));
        PartDefinition p23 = p19.addOrReplaceChild("wheel1_felloe3", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 2.3562f, 0, 0));
        PartDefinition p24 = p19.addOrReplaceChild("wheel1_felloe4", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.1416f, 0, 0));
        PartDefinition p25 = p19.addOrReplaceChild("wheel1_felloe5", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 3.927f, 0, 0));
        PartDefinition p26 = p19.addOrReplaceChild("wheel1_felloe6", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 4.7124f, 0, 0));
        PartDefinition p27 = p19.addOrReplaceChild("wheel1_felloe7", CubeListBuilder.create()
                .texOffs(62, 38).addBox(-1.5f, -9, -4.0279f, 3, 1.6f, 8.0558f)
                .texOffs(112, 38).addBox(-1.5f, -9.01f, -4.0279f, 3, 0.6f, 8.0558f, new CubeDeformation(0.06f)),
            PartPose.offsetAndRotation(0, 0, 0, 5.4978f, 0, 0));
        PartDefinition p28 = p19.addOrReplaceChild("wheel1_spoke0", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.3927f, 0, 0));
        PartDefinition p29 = p19.addOrReplaceChild("wheel1_spoke1", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 0.9163f, 0, 0));
        PartDefinition p30 = p19.addOrReplaceChild("wheel1_spoke2", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.4399f, 0, 0));
        PartDefinition p31 = p19.addOrReplaceChild("wheel1_spoke3", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 1.9635f, 0, 0));
        PartDefinition p32 = p19.addOrReplaceChild("wheel1_spoke4", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 2.4871f, 0, 0));
        PartDefinition p33 = p19.addOrReplaceChild("wheel1_spoke5", CubeListBuilder.create()
                .texOffs(34, 38).addBox(-0.5f, -8, -0.5f, 1, 16, 1),
            PartPose.offsetAndRotation(0, 0, 0, 3.0107f, 0, 0));
        return LayerDefinition.create(mesh, 256, 64);
    }
}
