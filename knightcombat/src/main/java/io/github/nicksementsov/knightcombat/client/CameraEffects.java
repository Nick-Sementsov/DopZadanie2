package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.network.Feedback;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The camera follows the swings a little and shakes when a weapon is stopped by a block, a parry or a wall. */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class CameraEffects {
    private static final int SHAKE_TICKS = 8;
    private static float shakeStrength;
    private static long shakeStart = Long.MIN_VALUE / 2;

    private CameraEffects() {
    }

    static void onFeedback(Feedback feedback) {
        float strength = switch (feedback) {
            case GOT_PARRIED, GOT_COUNTERED, WALL -> 3.0f;
            case GOT_BLOCKED, KICKED -> 2.0f;
            case GUARD_BROKEN, FLINCHED -> 4.0f;
            case BLOCKED, PARRIED, COUNTERED -> 1.2f;
            default -> 0;
        };
        if (strength > 0) {
            shakeStrength = strength;
            shakeStart = ClientCombat.ticks();
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON || !CombatConfig.CAMERA_SWAY.get()) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float roll = 0, yaw = 0, pitch = 0;
        FighterAnim anim = ClientCombat.anim(player);
        if (anim != null) {
            float p = anim.progress(partial);
            float side = anim.has(CombatState.MIRRORED) ? -1 : 1;
            switch (anim.phase()) {
                case WINDUP -> {
                    float t = PoseMath.smooth(p);
                    if (anim.attack() == AttackType.SLASH) {
                        roll += side * 1.5f * t;
                        yaw += side * 1.0f * t;
                    } else if (anim.attack() == AttackType.OVERHEAD) {
                        pitch -= 1.5f * t;
                    }
                }
                case RELEASE -> {
                    float arc = Mth.sin(p * Mth.PI);
                    float sweep = PoseMath.smooth(p) * 2 - 1;
                    if (anim.attack() == AttackType.SLASH) {
                        roll += side * (1.5f - 4.5f * PoseMath.smooth(p));
                        yaw -= side * 2.5f * sweep;
                    } else if (anim.attack() == AttackType.OVERHEAD) {
                        pitch += 3.5f * arc - 1.5f * (1 - p);
                    } else if (anim.attack() == AttackType.STAB) {
                        pitch += 0.8f * arc;
                    }
                }
                case RECOVERY -> {
                    float rest = 1 - PoseMath.smooth(p);
                    if (anim.attack() == AttackType.SLASH && !anim.has(CombatState.REBOUND) && !anim.has(CombatState.FEINT)) {
                        roll -= side * 3.0f * rest;
                        yaw -= side * 2.5f * rest;
                    } else if (anim.attack() == AttackType.KICK) {
                        pitch += 4f * rest;
                    }
                }
                case KICK -> pitch += 4f * PoseMath.smooth(p);
                default -> {
                }
            }
        }
        float shake = ClientCombat.ticks() - shakeStart + partial;
        if (shake < SHAKE_TICKS) {
            float fade = 1 - shake / SHAKE_TICKS;
            roll += shakeStrength * fade * Mth.sin(shake * 2.6f);
            yaw += shakeStrength * 0.6f * fade * Mth.sin(shake * 3.3f + 1);
        }
        event.setRoll(event.getRoll() + roll);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
    }
}
