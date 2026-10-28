package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Everything the camera does in a fight:
 * <ul>
 *     <li>it follows the swings a little (first person only);</li>
 *     <li>it shakes when blows land, are blocked or parried: every blow adds "trauma", the shake grows faster than
 *     it (light blows tremble, heavy ones rock the view) and dies away within a second;</li>
 *     <li>it is knocked in a direction by a blow (a punch on a damped spring, so it springs back with a small
 *     rebound);</li>
 *     <li>the field of view narrows while a heavy blow is wound up and kicks out when it is released or lands.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class CameraEffects {
    // Largest shake angles in degrees, at full trauma.
    private static final float SHAKE_YAW = 4.0f;
    private static final float SHAKE_PITCH = 3.5f;
    private static final float SHAKE_ROLL = 6.0f;
    /** Trauma lost per second. */
    private static final float TRAUMA_DECAY = 1.5f;
    // The punch spring: stiffness and damping (a little under critical, so it rebounds once).
    private static final float STIFFNESS = 260;
    private static final float DAMPING = 14.5f;
    /** Initial speed of a punch per degree it should reach. */
    private static final float PUNCH_SPEED = 27;

    private static float trauma;
    private static float noiseTime;
    private static final float[] PUNCH = new float[3];
    private static final float[] PUNCH_SPEEDS = new float[3];
    private static float fovKick;
    private static long lastFrame;

    private CameraEffects() {
    }

    /** Adds shake; {@code amount} 0.1 is a light tremor, 0.5 a hard blow, 1 the most there is. */
    static void shake(float amount) {
        trauma = Math.min(1, trauma + amount * CombatConfig.CAMERA_SHAKE.get().floatValue());
    }

    /** Knocks the camera: it swings to about these angles (degrees) and springs back. */
    static void punch(float yaw, float pitch, float roll) {
        float scale = CombatConfig.CAMERA_SHAKE.get().floatValue() * PUNCH_SPEED;
        PUNCH_SPEEDS[0] += yaw * scale;
        PUNCH_SPEEDS[1] += pitch * scale;
        PUNCH_SPEEDS[2] += roll * scale;
    }

    /** Widens the view for an instant by {@code amount} (0.03 is three percent). */
    static void fovKick(float amount) {
        if (CombatConfig.CAMERA_SHAKE.get() > 0) {
            fovKick = Math.min(0.1f, fovKick + amount);
        }
    }

    /** Moves the shake, the punch and the view kick on to the current frame. */
    private static void update() {
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1.0E9f);
        lastFrame = now;
        trauma = Math.max(0, trauma - seconds * TRAUMA_DECAY);
        noiseTime += seconds;
        fovKick *= (float) Math.exp(-seconds * 7);
        int steps = Math.max(1, (int) Math.ceil(seconds * 240));
        float dt = seconds / steps;
        for (int step = 0; step < steps; step++) {
            for (int axis = 0; axis < 3; axis++) {
                PUNCH_SPEEDS[axis] += (-STIFFNESS * PUNCH[axis] - DAMPING * PUNCH_SPEEDS[axis]) * dt;
                PUNCH[axis] += PUNCH_SPEEDS[axis] * dt;
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Smooth random-looking motion between -1 and 1: a few sines that never line up. */
    private static float noise(float time, float seed) {
        return 0.5f * Mth.sin(time * 31 + seed) + 0.3f * Mth.sin(time * 47.3f + seed * 2.1f) + 0.2f * Mth.sin(time * 71.9f + seed * 3.7f);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        update();
        float roll = 0, yaw = 0, pitch = 0;
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON && CombatConfig.CAMERA_SWAY.get()) {
            float[] sway = sway(ClientCombat.anim(player), (float) event.getPartialTick());
            yaw += sway[0];
            pitch += sway[1];
            roll += sway[2];
        }
        float amount = trauma * (float) Math.sqrt(trauma);
        yaw += SHAKE_YAW * amount * noise(noiseTime, 0) + PUNCH[0];
        pitch += SHAKE_PITCH * amount * noise(noiseTime, 11) + PUNCH[1];
        roll += SHAKE_ROLL * amount * noise(noiseTime, 23) + PUNCH[2];
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !event.usedConfiguredFov() || CombatConfig.CAMERA_SHAKE.get() <= 0) {
            return;
        }
        float factor = 1 + fovKick;
        FighterAnim anim = ClientCombat.anim(player);
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (anim != null && weapon != null && anim.phase() == Phase.WINDUP && anim.attack() != AttackType.KICK
                && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            // Focusing on the blow: the heavier the weapon, the more the view narrows while it is wound up,
            // and the charge of a heavy attack narrows it further.
            float p = PoseMath.smooth(anim.progress((float) event.getPartialTick()));
            factor -= anim.has(CombatState.HEAVY)
                    ? 0.035f * (0.5f + weapon.weight()) + 0.03f * p
                    : 0.035f * p * (0.5f + weapon.weight());
        }
        event.setFOV(event.getFOV() * factor);
    }

    /** How the camera follows the swing: yaw, pitch and roll in degrees. */
    private static float[] sway(FighterAnim anim, float partial) {
        float roll = 0, yaw = 0, pitch = 0;
        if (anim != null) {
            float p = anim.progress(partial);
            float side = anim.has(CombatState.MIRRORED) ? -1 : 1;
            switch (anim.phase()) {
                case WINDUP -> {
                    float t = anim.has(CombatState.HEAVY) ? 1 + 0.5f * PoseMath.smooth(p) : PoseMath.smooth(p);
                    if (anim.attack() == AttackType.SLASH) {
                        roll += side * 2.0f * t;
                        yaw += side * 1.5f * t;
                    } else if (anim.attack() == AttackType.OVERHEAD) {
                        pitch -= 2.5f * t;
                    } else if (anim.attack() == AttackType.STAB) {
                        pitch -= 0.8f * t;
                    }
                }
                case RELEASE -> {
                    float arc = Mth.sin(p * Mth.PI);
                    float sweep = PoseMath.smooth(p) * 2 - 1;
                    // Heavy blows and finishers pull the view along harder.
                    float strength = anim.has(CombatState.HEAVY) || anim.finisher() ? 1.6f : 1;
                    if (anim.combo() == Combo.WHIRLWIND) {
                        roll += side * (4.0f - 12.0f * PoseMath.smooth(p));
                        yaw -= side * 7f * sweep;
                    } else if (anim.attack() == AttackType.SLASH) {
                        roll += side * (2.0f - 6.0f * PoseMath.smooth(p)) * strength;
                        yaw -= side * 3.5f * sweep * strength;
                        if (anim.combo() == Combo.HAMSTRING) {
                            pitch += 3f * arc;
                        }
                    } else if (anim.attack() == AttackType.OVERHEAD) {
                        pitch += (5.0f * arc - 2.5f * (1 - p)) * strength;
                    } else if (anim.attack() == AttackType.STAB) {
                        pitch += 1.2f * arc * strength;
                    }
                }
                case THROW -> {
                    // The view leans back with the arm, then follows the throw.
                    if (p < 0.72f) {
                        float t = PoseMath.smooth(p / 0.72f);
                        pitch -= 2f * t;
                        roll += 1.5f * t;
                    } else {
                        float t = PoseMath.smooth((p - 0.72f) / 0.28f);
                        pitch += lerp(-2f, 2.5f, t);
                        roll += lerp(1.5f, -1f, t);
                    }
                }
                case DODGE -> {
                    float k = Mth.sin(Mth.PI * Math.min(1, p * 1.15f));
                    float dodgeSide = anim.has(CombatState.DODGE_LEFT) ? -1 : anim.has(CombatState.DODGE_RIGHT) ? 1 : 0;
                    roll += dodgeSide * 5f * k;
                    if (dodgeSide == 0) {
                        pitch += (anim.has(CombatState.DODGE_FORWARD) ? 2f : -2.5f) * k;
                    }
                }
                case RECOVERY -> {
                    float rest = 1 - PoseMath.smooth(p);
                    if (anim.has(CombatState.THROWN)) {
                        pitch += 2.5f * rest;
                    } else if (anim.attack() == AttackType.SLASH && !anim.has(CombatState.REBOUND) && !anim.has(CombatState.FEINT)) {
                        roll -= side * 4.0f * rest;
                        yaw -= side * 3.5f * rest;
                    } else if (anim.attack() == AttackType.OVERHEAD && !anim.has(CombatState.REBOUND)) {
                        pitch += 2.5f * rest;
                    } else if (anim.attack() == AttackType.KICK) {
                        pitch += 4f * rest;
                    }
                }
                case KICK -> pitch += 4f * PoseMath.smooth(p);
                default -> {
                }
            }
        }
        return new float[]{yaw, pitch, roll};
    }
}
