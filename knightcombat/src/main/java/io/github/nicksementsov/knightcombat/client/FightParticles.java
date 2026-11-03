package io.github.nicksementsov.knightcombat.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.nicksementsov.knightcombat.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/**
 * The particles of the fights: glowing sparks that streak off steel, bright stars where blades meet, drops and
 * splashes of blood, puffs and rings of dust. {@link CombatParticles} makes them with the colours and speeds it
 * needs; the providers registered here only serve {@code /particle} with plain ones.
 */
final class FightParticles {
    private static SpriteSet sparkSprites;
    private static SpriteSet flashSprites;
    private static SpriteSet bloodSprites;
    private static SpriteSet splashSprites;
    private static SpriteSet dustSprites;
    private static SpriteSet ringSprites;

    /** Adds its light to what is behind it, so sparks and flashes glow (and black is invisible). */
    static final ParticleRenderType GLOW = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "knightcombat:glow";
        }
    };

    private FightParticles() {
    }

    static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), sprites -> {
            sparkSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Spark(level, x, y, z, xd, yd, zd);
        });
        event.registerSpriteSet(ModParticles.FLASH.get(), sprites -> {
            flashSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Flash(level, x, y, z, 0.5f);
        });
        event.registerSpriteSet(ModParticles.BLOOD.get(), sprites -> {
            bloodSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Blood(level, x, y, z, xd, yd, zd, 0.55f, 0.03f, 0.03f);
        });
        event.registerSpriteSet(ModParticles.BLOOD_SPLASH.get(), sprites -> {
            splashSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Splash(level, x, y, z, 0.35f, 0.55f, 0.03f, 0.03f);
        });
        event.registerSpriteSet(ModParticles.DUST.get(), sprites -> {
            dustSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Dust(level, x, y, z, xd, yd, zd, 0.4f, 0x9C8B70, 0.6f);
        });
        event.registerSpriteSet(ModParticles.RING.get(), sprites -> {
            ringSprites = sprites;
            return (type, level, x, y, z, xd, yd, zd) -> new Ring(level, x, y, z, 3, 0x9C8B70, 0.5f);
        });
    }

    /** Whether the textures are there to draw with (the providers are registered before the first world). */
    static boolean ready() {
        return sparkSprites != null && flashSprites != null && bloodSprites != null && splashSprites != null
                && dustSprites != null && ringSprites != null;
    }

    /** A particle that can also lie flat, on the ground or over it. */
    abstract static class Flat extends TextureSheetParticle {
        Flat(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
        }

        /** Draws the particle as a square lying flat, {@code half} wide each way from its middle, seen from both sides. */
        void renderFlat(VertexConsumer buffer, Camera camera, float partialTick, float half, float angle, float lift) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
            float py = (float) (Mth.lerp(partialTick, yo, y) - cam.y) + lift;
            float pz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
            float c = Mth.cos(angle) * half;
            float s = Mth.sin(angle) * half;
            Vector3f[] corners = {
                    new Vector3f(px - c + s, py, pz - s - c),
                    new Vector3f(px - c - s, py, pz - s + c),
                    new Vector3f(px + c - s, py, pz + s + c),
                    new Vector3f(px + c + s, py, pz + s - c)};
            quad(buffer, corners, getLightColor(partialTick));
        }

        /** Four corners in order around the square, drawn from both sides. */
        void quad(VertexConsumer buffer, Vector3f[] corners, int light) {
            float[] us = {getU0(), getU0(), getU1(), getU1()};
            float[] vs = {getV1(), getV0(), getV0(), getV1()};
            for (int i = 0; i < 4; i++) {
                vertex(buffer, corners[i], us[i], vs[i], light);
            }
            for (int i = 3; i >= 0; i--) {
                vertex(buffer, corners[i], us[i], vs[i], light);
            }
        }

        private void vertex(VertexConsumer buffer, Vector3f at, float u, float v, int light) {
            buffer.vertex(at.x(), at.y(), at.z()).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }

        void setColor(int rgb) {
            setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        }
    }

    /** A spark of hot steel: a short glowing streak along its flight that cools from white to red and falls. */
    static final class Spark extends Flat {
        Spark(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            gravity = 0.8f;
            friction = 0.9f;
            hasPhysics = true;
            lifetime = 6 + random.nextInt(10);
            quadSize = 0.024f + random.nextFloat() * 0.018f;
            setSize(0.05f, 0.05f);
            setColor(1, 0.95f, 0.75f);
            pickSprite(sparkSprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            setColor(1, Math.max(0.25f, 0.95f - 0.75f * t), Math.max(0.05f, 0.75f - 0.9f * t));
            alpha = t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            Vector3f at = new Vector3f((float) (Mth.lerp(partialTick, xo, x) - cam.x),
                    (float) (Mth.lerp(partialTick, yo, y) - cam.y), (float) (Mth.lerp(partialTick, zo, z) - cam.z));
            Vector3f dir = new Vector3f((float) xd, (float) yd, (float) zd);
            float speed = dir.length();
            Vector3f side = speed < 1.0E-4f ? new Vector3f() : dir.div(speed).cross(at, new Vector3f());
            if (side.lengthSquared() < 1.0E-8f) {
                // Flying straight at the camera (or lying still): a plain dot.
                super.render(buffer, camera, partialTick);
                return;
            }
            side.normalize(quadSize);
            float length = Math.min(0.5f, speed * 2.5f) + quadSize * 2;
            Vector3f head = new Vector3f(dir).mul(quadSize).add(at);
            Vector3f tail = new Vector3f(dir).mul(-length).add(at);
            Vector3f[] corners = {
                    new Vector3f(tail).sub(side),
                    new Vector3f(head).sub(side),
                    new Vector3f(head).add(side),
                    new Vector3f(tail).add(side)};
            quad(buffer, corners, LightTexture.FULL_BRIGHT);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return GLOW;
        }
    }

    /** A star that flashes up for an instant where blades meet. */
    static final class Flash extends Flat {
        private final float size;
        private final float spin;

        Flash(ClientLevel level, double x, double y, double z, float size) {
            super(level, x, y, z);
            this.size = size;
            lifetime = 5;
            gravity = 0;
            hasPhysics = false;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            spin = (random.nextFloat() - 0.5f) * 0.3f;
            setColor(1, 0.95f, 0.82f);
            pickSprite(flashSprites);
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            alpha = 1 - (float) age / lifetime;
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (age + partialTick) / lifetime;
            return size * (t < 0.2f ? 0.35f + 3.25f * t : 1 - 0.5f * (t - 0.2f) / 0.8f);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return GLOW;
        }
    }

    /** A drop of blood: it flies, falls and stays on the ground as a small stain for a while. */
    static final class Blood extends Flat {
        private int landedAt = -1;
        private float stain;
        private float angle;

        Blood(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float r, float g, float b) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            gravity = 1;
            friction = 0.98f;
            hasPhysics = true;
            lifetime = 70 + random.nextInt(50);
            quadSize = 0.03f + random.nextFloat() * 0.035f;
            setSize(0.04f, 0.04f);
            setColor(r, g, b);
            pickSprite(bloodSprites);
        }

        @Override
        public void tick() {
            if (landedAt >= 0) {
                xo = x;
                yo = y;
                zo = z;
                if (age++ >= lifetime) {
                    remove();
                }
                alpha = Math.min(1, (lifetime - age) / 20f);
                return;
            }
            super.tick();
            if (!isAlive()) {
                return;
            }
            if (!level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) {
                // Washed away.
                remove();
                return;
            }
            if (!onGround) {
                return;
            }
            landedAt = age;
            xd = yd = zd = 0;
            stain = quadSize * (2 + random.nextFloat() * 1.5f);
            angle = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            if (landedAt < 0) {
                super.render(buffer, camera, partialTick);
                return;
            }
            float spread = Math.min(1, (age - landedAt + partialTick) / 3);
            renderFlat(buffer, camera, partialTick, stain * (0.5f + 0.5f * spread), angle, 0.01f + (landedAt % 7) * 0.0005f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** A splash of blood where a blow bites in: it bursts out and is gone. */
    static final class Splash extends Flat {
        Splash(ClientLevel level, double x, double y, double z, float size, float r, float g, float b) {
            super(level, x, y, z);
            lifetime = 5 + random.nextInt(3);
            gravity = 0.15f;
            hasPhysics = false;
            quadSize = size;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            setColor(r, g, b);
            pickSprite(splashSprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            alpha = 1 - t * t;
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (0.6f + 0.7f * Math.min(1, (age + partialTick) / lifetime));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** A puff of dust in the colour of the ground it came from: it swells, drifts and thins out. */
    static final class Dust extends Flat {
        private final float startAlpha;
        private final float spin;

        Dust(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float size, int rgb, float alpha) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            gravity = -0.01f;
            friction = 0.86f;
            hasPhysics = true;
            lifetime = 16 + random.nextInt(16);
            quadSize = size * (0.8f + random.nextFloat() * 0.4f);
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            spin = (random.nextFloat() - 0.5f) * 0.08f;
            startAlpha = alpha;
            this.alpha = alpha;
            setColor(rgb);
            pickSprite(dustSprites);
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            float t = (float) age / lifetime;
            alpha = startAlpha * (1 - t) * (1 - t);
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (0.45f + 0.75f * Mth.sqrt(Math.min(1, (age + partialTick) / lifetime)));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** A ring of dust running out over the ground from a stamp or a battle cry. */
    static final class Ring extends Flat {
        private final float radius;
        private final float startAlpha;

        Ring(ClientLevel level, double x, double y, double z, float radius, int rgb, float alpha) {
            super(level, x, y, z);
            this.radius = radius;
            startAlpha = alpha;
            this.alpha = alpha;
            lifetime = 12;
            gravity = 0;
            hasPhysics = false;
            roll = random.nextFloat() * Mth.TWO_PI;
            setColor(rgb);
            pickSprite(ringSprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            alpha = startAlpha * (1 - t);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = Math.min(1, (age + partialTick) / lifetime);
            float grown = 1 - (1 - t) * (1 - t);
            renderFlat(buffer, camera, partialTick, radius * (0.15f + 0.85f * grown), roll, 0.06f);
        }

        @Override
        public boolean shouldCull() {
            return false;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
