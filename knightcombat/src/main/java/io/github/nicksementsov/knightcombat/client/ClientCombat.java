package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.CombatRules;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.WeaponStats;
import io.github.nicksementsov.knightcombat.combat.Weapons;
import io.github.nicksementsov.knightcombat.network.CombatAction;
import io.github.nicksementsov.knightcombat.network.CombatNetwork;
import io.github.nicksementsov.knightcombat.network.CombatStatePacket;
import io.github.nicksementsov.knightcombat.network.Feedback;
import io.github.nicksementsov.knightcombat.network.StaminaPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side of the fights: turns mouse and key presses into combat inputs, keeps the combat state of every
 * visible player for the animations and the stamina and messages for the HUD.
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID, value = Dist.CLIENT)
public final class ClientCombat {
    private static final Int2ObjectMap<FighterAnim> ANIMS = new Int2ObjectOpenHashMap<>();
    private static long ticks;

    private static float stamina = -1;
    private static float maxStamina = 100;
    private static long staminaChangedAt;
    private static long riposteUntil = Long.MIN_VALUE / 2;
    private static Feedback lastFeedback;
    private static long feedbackAt = Long.MIN_VALUE / 2;
    private static long hitAt = Long.MIN_VALUE / 2;

    /** The attack key was pressed for a combat attack and is still held: it must not start breaking blocks. */
    private static boolean attackHeld;
    private static boolean useWasDown;
    private static boolean blocking;
    private static long lastScrollAt = Long.MIN_VALUE / 2;
    private static AttackType lastScrollAttack;

    private ClientCombat() {
    }

    public static long ticks() {
        return ticks;
    }

    public static FighterAnim anim(Entity entity) {
        return ANIMS.get(entity.getId());
    }

    static FighterAnim animOf(int entityId) {
        return ANIMS.computeIfAbsent(entityId, id -> new FighterAnim());
    }

    public static float stamina() {
        return stamina;
    }

    public static float maxStamina() {
        return maxStamina;
    }

    public static long staminaChangedAt() {
        return staminaChangedAt;
    }

    public static boolean riposteReady() {
        return ticks <= riposteUntil;
    }

    public static Feedback lastFeedback() {
        return lastFeedback;
    }

    public static long feedbackAt() {
        return feedbackAt;
    }

    public static long hitAt() {
        return hitAt;
    }

    // ---------------------------------------------------------------- packets

    public static void onState(CombatStatePacket packet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        animOf(packet.entityId()).apply(Phase.byId(packet.phase()), AttackType.byId(packet.attack()),
                packet.flags(), packet.length(), ticks - packet.elapsed());
    }

    public static void onStamina(StaminaPacket packet) {
        if (Math.abs(packet.stamina() - stamina) > 0.01f) {
            staminaChangedAt = ticks;
        }
        stamina = packet.stamina();
        maxStamina = packet.max();
    }

    public static void onFeedback(Feedback feedback) {
        if (feedback == Feedback.HIT) {
            hitAt = ticks;
        } else {
            lastFeedback = feedback;
            feedbackAt = ticks;
        }
        if (feedback == Feedback.PARRIED || feedback == Feedback.COUNTERED) {
            riposteUntil = ticks + CombatRules.RIPOSTE_WINDOW;
        }
        CameraEffects.onFeedback(feedback);
    }

    // ---------------------------------------------------------------- input

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (event.phase == TickEvent.Phase.END) {
            for (Player other : mc.level.players()) {
                FighterAnim anim = ANIMS.get(other.getId());
                if (anim != null) {
                    anim.advance(other);
                }
            }
            return;
        }
        ticks++;
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        boolean canFight = weapon != null && mc.screen == null && player.isAlive() && !player.isSpectator();

        // Blocking: the use key starts a block unless it was pressed while looking at a block (doors, chests...).
        boolean useDown = mc.options.keyUse.isDown();
        if (useDown && !useWasDown && canFight && canWeaponBlock(player, weapon) && !usesTarget(mc.hitResult)) {
            blocking = true;
            send(CombatAction.BLOCK_START);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.IDLE) {
                anim.apply(Phase.BLOCK, anim.attack, 0, 0, ticks);
            }
        }
        if (blocking && (!useDown || !canFight)) {
            blocking = false;
            send(CombatAction.BLOCK_STOP);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.BLOCK) {
                anim.apply(Phase.IDLE, anim.attack, 0, 0, ticks);
            }
        }
        useWasDown = useDown;

        if (consume(KeyBindings.OVERHEAD) && canFight) {
            attack(player, weapon, AttackType.OVERHEAD);
        }
        if (consume(KeyBindings.STAB) && canFight) {
            attack(player, weapon, AttackType.STAB);
        }
        if (consume(KeyBindings.FEINT) && canFight) {
            send(CombatAction.FEINT);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.WINDUP && stamina >= CombatRules.FEINT_COST) {
                anim.apply(Phase.RECOVERY, anim.attack, CombatState.FEINT, CombatRules.FEINT_RECOVERY, ticks);
            }
        }
        if (consume(KeyBindings.KICK) && canFight) {
            send(CombatAction.KICK);
            FighterAnim anim = animOf(player.getId());
            if ((anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK) && stamina >= CombatRules.KICK_COST) {
                anim.apply(Phase.KICK, AttackType.KICK, 0, CombatRules.KICK_WINDUP, ticks);
            }
        }
        if (consume(KeyBindings.BATTLE_CRY) && canFight) {
            send(CombatAction.BATTLE_CRY);
        }
        if (!mc.options.keyAttack.isDown()) {
            attackHeld = false;
        }
    }

    private static boolean consume(KeyMapping key) {
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        return pressed;
    }

    /**
     * The attack key slashes with a weapon in hand. It still breaks blocks while sneaking, or when the crosshair
     * is on a block and nobody is within reach.
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (weapon == null) {
            return;
        }
        if (event.isAttack()) {
            if (!attackHeld) {
                boolean onBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
                // Boats, minecarts, item frames and paintings are broken the usual way.
                boolean onObject = mc.hitResult instanceof EntityHitResult hit && !(hit.getEntity() instanceof LivingEntity);
                if (player.isShiftKeyDown() || onObject || onBlock && !enemyInReach(player, weapon)) {
                    return;
                }
                attackHeld = true;
                attack(player, weapon, AttackType.SLASH);
            }
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem() && blocking) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Mouse wheel: up is an overhead, down a stab (can be turned off in the client config). */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null || player.isSpectator() || !CombatConfig.SCROLL_ATTACKS.get()) {
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (weapon == null) {
            return;
        }
        event.setCanceled(true);
        AttackType type = event.getScrollDelta() > 0 ? AttackType.OVERHEAD : AttackType.STAB;
        // One turn of a wheel sends several steps; they count as one attack.
        if (type == lastScrollAttack && ticks - lastScrollAt < 4) {
            return;
        }
        lastScrollAt = ticks;
        lastScrollAttack = type;
        attack(player, weapon, type);
    }

    private static void attack(LocalPlayer player, WeaponStats weapon, AttackType type) {
        send(CombatAction.of(type));
        // Start the wind-up right away instead of waiting for the server, which confirms or corrects it.
        FighterAnim anim = animOf(player.getId());
        boolean free = anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK
                || anim.phase == Phase.RECOVERY && anim.has(CombatState.FEINT);
        if (free) {
            boolean riposte = riposteReady();
            int length = weapon.attack(type).windup();
            if (riposte) {
                length = Math.max(3, Math.round(length * CombatRules.RIPOSTE_WINDUP));
            }
            anim.apply(Phase.WINDUP, type, riposte ? CombatState.RIPOSTE : 0, length, ticks);
            if (riposte) {
                riposteUntil = Long.MIN_VALUE / 2;
            }
        }
    }

    private static void send(CombatAction action) {
        CombatNetwork.sendToServer(action);
    }

    /**
     * Whether the use key is meant for what the crosshair is on: blocks (doors, chests, levers) and entities that are
     * not enemies (villagers, horses, item frames). Against monsters and players, and in the air, it blocks.
     */
    private static boolean usesTarget(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) {
            return !(entityHit.getEntity() instanceof Enemy) && !(entityHit.getEntity() instanceof Player);
        }
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    private static boolean canWeaponBlock(Player player, WeaponStats weapon) {
        return weapon.twoHanded() || !(player.getOffhandItem().getItem() instanceof ShieldItem);
    }

    private static boolean enemyInReach(LocalPlayer player, WeaponStats weapon) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        double reach = weapon.reach() + 0.5;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(reach + 1), ClientCombat::isEnemy)) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            if (to.length() - entity.getBbWidth() / 2 <= reach && to.normalize().dot(look) > 0.5) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEnemy(Entity entity) {
        Player player = Minecraft.getInstance().player;
        return entity instanceof LivingEntity && entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                && !(entity instanceof ArmorStand)
                && !(entity instanceof OwnableEntity pet && player != null && player.getUUID().equals(pet.getOwnerUUID()));
    }

    // ---------------------------------------------------------------- cleanup

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ANIMS.remove(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ANIMS.clear();
        stamina = -1;
        blocking = false;
        attackHeld = false;
        lastFeedback = null;
    }
}
