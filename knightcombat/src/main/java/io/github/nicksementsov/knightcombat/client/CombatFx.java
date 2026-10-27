package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.network.Impact;
import io.github.nicksementsov.knightcombat.network.ImpactPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What a fight feels like on this client. Every blow the server reports stops the swing that struck for an instant
 * (hit-stop), makes the fighter who was struck flinch away from it, shakes and knocks the camera of the players
 * involved (and a little of those standing near) and flashes the edges of their screens. The local player's own
 * attacks carry them a step forward.
 */
public final class CombatFx {
    /** Players further away than this do not feel a blow. */
    private static final float NEAR = 10;

    private CombatFx() {
    }

    public static void onImpact(ImpactPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) {
            return;
        }
        Impact impact = packet.impact();
        Entity attacker = packet.attacker() >= 0 ? level.getEntity(packet.attacker()) : null;
        Entity victim = packet.victim() >= 0 ? level.getEntity(packet.victim()) : null;
        Vec3 point = new Vec3(packet.x(), packet.y(), packet.z());
        float weight = packet.weight();

        if (CombatConfig.HIT_STOP.get() && attacker instanceof Player) {
            float stop = switch (impact) {
                case HIT -> 1.4f + 1.8f * weight;
                case KILL -> 4;
                case BLOCK -> 2 + weight;
                case PARRY, GUARD_BREAK -> 3.5f;
                case WALL -> 2.5f;
                default -> 0;
            };
            FighterAnim anim = ClientCombat.anim(attacker);
            if (stop > 0 && anim != null) {
                anim.hitStop(stop);
            }
        }
        if (victim instanceof Player && (impact.hurts() || impact == Impact.BLOCK || impact == Impact.PARRY)) {
            float strength = switch (impact) {
                case KILL -> 1;
                case GUARD_BREAK, KICK -> 0.9f;
                case HIT -> 0.55f + 0.45f * weight;
                case BLOCK -> 0.3f + 0.35f * weight;
                default -> 0.25f;
            };
            ClientCombat.animOf(victim.getId()).hurt(side(victim, attacker != null ? attacker.position() : point), strength);
        }

        boolean struck = attacker == me;
        boolean receiving = victim == me;
        if (struck) {
            feelOwnBlow(me, impact, weight);
        }
        if (receiving) {
            feelReceivedBlow(me, impact, weight, side(me, attacker != null ? attacker.position() : point));
        }
        if (!struck && !receiving) {
            float distance = (float) me.getEyePosition().distanceTo(point);
            float reach = impact == Impact.CRY ? NEAR * 1.6f : NEAR;
            if (distance < reach && impact != Impact.WALL) {
                CameraEffects.shake(0.09f * (1 - distance / reach) * (0.6f + weight));
            }
        }
    }

    /** The local player's blow landed, was stopped or was a cry. */
    private static void feelOwnBlow(LocalPlayer me, Impact impact, float weight) {
        FighterAnim anim = ClientCombat.anim(me);
        AttackType attack = anim != null ? anim.attack() : AttackType.SLASH;
        float swingSide = anim != null && anim.has(CombatState.MIRRORED) ? -1 : 1;
        switch (impact) {
            case HIT -> {
                CameraEffects.shake(0.16f + 0.22f * weight);
                // The weapon bites and drags the view along the swing.
                switch (attack) {
                    case OVERHEAD -> CameraEffects.punch(0, 1.5f + 2 * weight, 0);
                    case STAB -> CameraEffects.punch(0, 0.6f, 0);
                    default -> CameraEffects.punch(-swingSide * (1 + weight), 0.3f, -swingSide * (1.5f + 1.5f * weight));
                }
            }
            case KILL -> {
                CameraEffects.shake(0.45f);
                CameraEffects.punch(0, 2, 0);
                CameraEffects.fovKick(0.035f);
                CombatHud.flash(CombatHud.Flash.KILL);
            }
            case BLOCK -> {
                // Steel on steel: the blow bounces back up.
                CameraEffects.shake(0.3f + 0.2f * weight);
                CameraEffects.punch(swingSide * 1.2f, -2 - weight, swingSide * 1.5f);
            }
            case PARRY -> {
                CameraEffects.shake(0.55f);
                CameraEffects.punch(swingSide * 2.5f, -3.5f, swingSide * 3);
            }
            case GUARD_BREAK -> {
                CameraEffects.shake(0.4f);
                CameraEffects.punch(0, 1.5f, 0);
                CameraEffects.fovKick(0.03f);
            }
            case WALL -> {
                CameraEffects.shake(0.5f);
                CameraEffects.punch(swingSide * 2, -2.5f, swingSide * 2);
            }
            case KICK -> {
                CameraEffects.shake(0.25f);
                CameraEffects.punch(0, 1.5f, 0);
            }
            case CRY -> {
                CameraEffects.shake(0.22f);
                CameraEffects.fovKick(0.04f);
            }
        }
    }

    /** A blow came at the local player: it landed, was blocked or parried, kicked them or broke their guard. */
    private static void feelReceivedBlow(LocalPlayer me, Impact impact, float weight, float side) {
        switch (impact) {
            case HIT, KILL -> {
                // Knocked away from the blow: the head turns away and tilts, the view kicks out.
                CameraEffects.shake(0.35f + 0.35f * weight);
                CameraEffects.punch(-side * (2 + 2 * weight), -2 - weight, side * (3 + 2 * weight));
                CameraEffects.fovKick(0.02f + 0.015f * weight);
                CombatHud.flash(CombatHud.Flash.HURT);
            }
            case BLOCK -> {
                CameraEffects.shake(0.18f + 0.3f * weight);
                CameraEffects.punch(-side, -1.2f - 1.5f * weight, side * 1.5f);
            }
            case PARRY -> {
                CameraEffects.shake(0.3f);
                CameraEffects.punch(0, -1, 0);
                CombatHud.flash(CombatHud.Flash.PARRY);
            }
            case GUARD_BREAK -> {
                CameraEffects.shake(0.6f);
                CameraEffects.punch(-side * 2, -3, side * 4);
                CameraEffects.fovKick(0.03f);
                CombatHud.flash(CombatHud.Flash.HURT);
            }
            case KICK -> {
                CameraEffects.shake(0.55f);
                CameraEffects.punch(-side, -4, side * 2);
                CameraEffects.fovKick(0.03f);
                CombatHud.flash(CombatHud.Flash.HURT);
            }
            default -> {
            }
        }
    }

    /** The local player's swing started its release (or a kick went out): it carries them a little forward. */
    static void onOwnRelease(LocalPlayer me, AttackType attack, WeaponStats weapon) {
        float weight = weapon != null ? weapon.weight() : 0.4f;
        if (attack != AttackType.KICK) {
            CameraEffects.fovKick(0.01f + 0.015f * weight);
            if (weight >= 0.6f) {
                CameraEffects.shake(0.06f);
            }
        }
        if (!CombatConfig.ATTACK_LUNGE.get() || !me.onGround() || me.isShiftKeyDown() || me.isInWater() || me.isPassenger()) {
            return;
        }
        float speed = switch (attack) {
            case STAB -> 0.24f;
            case OVERHEAD -> 0.18f;
            case SLASH -> 0.1f;
            case KICK -> 0.1f;
        } * (0.85f + 0.3f * weight);
        Vec3 look = me.getLookAngle();
        double length = Math.sqrt(look.x * look.x + look.z * look.z);
        if (length > 1.0E-3) {
            me.push(look.x / length * speed, 0, look.z / length * speed);
        }
    }

    /** Which side of {@code entity} the blow came from: 1 the right, -1 the left, near 0 straight ahead or behind. */
    private static float side(Entity entity, Vec3 from) {
        float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
        double dx = from.x - entity.getX();
        double dz = from.z - entity.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            return 0;
        }
        // The right of an entity looking along (-sin yaw, cos yaw) is (-cos yaw, -sin yaw).
        return (float) Mth.clamp((dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw)) / length, -1, 1);
    }
}
