package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the blade of an item model points once the model is placed in the hand with its own display transform.
 * Weapon models of different mods are drawn in different ways (flat textures of any size, 3D models, custom hand
 * transforms), so the axes are measured from the model itself: the longest extent of the model is the blade,
 * pointing away from the hand, and the side where the head sticks out (the edge of an axe) is the edge.
 */
final class HeldModel {
    /**
     * @param blade  unit direction from the hand to the tip
     * @param edge   unit direction across the blade, towards the side with more of the head
     * @param length distance from the hand to the tip along the blade, in blocks
     * @param center a point on the line of the blade (the middle of the model)
     */
    record Axes(Vector3f blade, Vector3f edge, float length, Vector3f center) {
        /** The point of the blade at {@code fraction} of the way from the hand to the tip. */
        Vector3f point(float fraction) {
            return new Vector3f(blade).mul(fraction * length - center.dot(blade)).add(center);
        }
    }

    private record Key(Item item, ItemDisplayContext context) {
    }

    private static final Map<Key, Axes> CACHE = new HashMap<>();

    private HeldModel() {
    }

    /** Axes of the item held in the given context (third person right or left hand). Render thread only. */
    static Axes axes(ItemStack stack, ItemDisplayContext context) {
        return CACHE.computeIfAbsent(new Key(stack.getItem(), context), key -> measure(stack, context));
    }

    /**
     * Whether the item is drawn from a baked model that can be measured. Items with their own renderer (3D models
     * of some mods) are left to Minecraft, which draws them in the hand the usual way.
     */
    static boolean measurable(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        return !mc.getItemRenderer().getModel(stack, mc.level, null, 0).isCustomRenderer();
    }

    /** Models change when resource packs are reloaded. */
    static void clear() {
        CACHE.clear();
    }

    private static Axes measure(ItemStack stack, ItemDisplayContext context) {
        Minecraft mc = Minecraft.getInstance();
        boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        // Without a holder: the model of a weapon at rest, not the one some mods show while it is used.
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
        PoseStack poseStack = new PoseStack();
        model = ForgeHooksClient.handleCameraTransforms(poseStack, model, context, left);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f transform = poseStack.last().pose();

        List<Vector3f> points = new ArrayList<>();
        List<Vector3f> faces = new ArrayList<>();
        RandomSource random = RandomSource.create(42);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random, ModelData.EMPTY, null));
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(null, direction, random, ModelData.EMPTY, null));
        }
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            List<Vector3f> corners = new ArrayList<>(4);
            float minX = 1, maxX = 0, minY = 1, maxY = 0;
            for (int i = 0; i < 4; i++) {
                float x = Float.intBitsToFloat(data[i * stride]);
                float y = Float.intBitsToFloat(data[i * stride + 1]);
                float z = Float.intBitsToFloat(data[i * stride + 2]);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                corners.add(transform.transformPosition(new Vector3f(x, y, z)));
            }
            // The two faces of a flat item cover the whole texture; only its outline tells the shape of the weapon.
            boolean wholeTexture = maxX - minX > 0.9f && maxY - minY > 0.9f;
            (wholeTexture ? faces : points).addAll(corners);
        }
        if (points.size() < 8) {
            points.addAll(faces);
        }
        if (points.size() < 4) {
            return fallback(transform);
        }
        return principalAxes(points);
    }

    /** A flat texture with the blade along its diagonal, as Minecraft's swords are drawn. */
    private static Axes fallback(Matrix4f transform) {
        Vector3f origin = transform.transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        Vector3f tip = transform.transformPosition(new Vector3f(1, 1, 0.5f)).sub(origin);
        Vector3f side = transform.transformPosition(new Vector3f(0, 1, 0.5f)).sub(origin);
        Vector3f blade = new Vector3f(tip).normalize();
        Vector3f edge = new Vector3f(side).fma(-side.dot(blade), blade).normalize();
        return new Axes(blade, edge, tip.length() * 1.4f, new Vector3f());
    }

    static Axes principalAxes(List<Vector3f> points) {
        Vector3f mean = new Vector3f();
        for (Vector3f p : points) {
            mean.add(p);
        }
        mean.div(points.size());
        double[][] cov = new double[3][3];
        for (Vector3f p : points) {
            double[] d = {p.x - mean.x, p.y - mean.y, p.z - mean.z};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    cov[i][j] += d[i] * d[j];
                }
            }
        }
        Vector3f blade = dominant(cov, null);
        Vector3f second = dominant(cov, blade);
        // The blade points from the hand (the origin) to the far end of the weapon.
        float far = 0, near = 0;
        for (Vector3f p : points) {
            float t = p.dot(blade);
            far = Math.max(far, t);
            near = Math.min(near, t);
        }
        if (-near > far) {
            blade.negate();
            far = -near;
        }
        // The edge is the side the head sticks out to: the third moment across the blade, in the outer part of the weapon.
        double skew = 0;
        for (Vector3f p : points) {
            if (p.dot(blade) > far * 0.4f) {
                double s = new Vector3f(p).sub(mean).dot(second);
                skew += s * s * s;
            }
        }
        if (skew < 0) {
            second.negate();
        }
        return new Axes(blade, second, far, mean);
    }

    /** Main eigenvector of a symmetric 3x3 matrix, perpendicular to {@code exclude} if given (power iteration). */
    private static Vector3f dominant(double[][] m, Vector3f exclude) {
        double[][] a = m;
        if (exclude != null) {
            // Project the matrix onto the plane square to the excluded direction: P M P with P = I - e e^T.
            double[] e = {exclude.x, exclude.y, exclude.z};
            double[][] p = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    p[i][j] = (i == j ? 1 : 0) - e[i] * e[j];
                }
            }
            a = multiply(p, multiply(m, p));
        }
        // Start from the longest column, which always has a part along the main eigenvector.
        int best = 0;
        double bestLength = -1;
        for (int j = 0; j < 3; j++) {
            double length = a[0][j] * a[0][j] + a[1][j] * a[1][j] + a[2][j] * a[2][j];
            if (length > bestLength) {
                bestLength = length;
                best = j;
            }
        }
        if (bestLength < 1.0E-12) {
            return exclude == null ? new Vector3f(0, 1, 0) : PoseMath.perpendicular(exclude);
        }
        Vector3f v = new Vector3f((float) a[0][best], (float) a[1][best], (float) a[2][best]).normalize();
        for (int iteration = 0; iteration < 64; iteration++) {
            Vector3f next = new Vector3f(
                    (float) (a[0][0] * v.x + a[0][1] * v.y + a[0][2] * v.z),
                    (float) (a[1][0] * v.x + a[1][1] * v.y + a[1][2] * v.z),
                    (float) (a[2][0] * v.x + a[2][1] * v.y + a[2][2] * v.z));
            if (next.lengthSquared() < 1.0E-12f) {
                break;
            }
            v = next.normalize();
        }
        if (exclude != null) {
            v.fma(-v.dot(exclude), exclude).normalize();
        }
        return v;
    }

    private static double[][] multiply(double[][] x, double[][] y) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    r[i][j] += x[i][k] * y[k][j];
                }
            }
        }
        return r;
    }
}
