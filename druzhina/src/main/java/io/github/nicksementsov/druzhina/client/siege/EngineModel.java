package io.github.nicksementsov.druzhina.client.siege;

import io.github.nicksementsov.druzhina.entity.siege.SiegeEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * A siege engine's model: its mesh is written by {@code tools/models.py} (client/mesh), its wheels (parts {@code
 * wheel0}, {@code wheel1}...) turn as it rolls, and each engine moves its own working parts.
 */
public abstract class EngineModel<T extends SiegeEngine> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart[] wheels;
    /** How far a wheel turns for a step: the smaller the wheel, the faster. */
    private final float roll;

    protected EngineModel(ModelPart root, int wheels, float radius) {
        this.root = root;
        this.wheels = new ModelPart[wheels];
        for (int i = 0; i < wheels; i++) {
            this.wheels[i] = root.getChild("wheel" + i);
        }
        this.roll = 4.0f / radius;
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public final void setupAnim(T e, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);
        for (ModelPart w : this.wheels) {
            w.xRot = limbSwing * this.roll;
        }
        this.work(e, Minecraft.getInstance().getFrameTime(), age);
    }

    /** Moves the working parts: the arm, the string, the barrel... */
    protected abstract void work(T e, float partialTick, float age);
}
