package io.github.nicksementsov.knightcombat.client;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.combat.AttackType;
import io.github.nicksementsov.knightcombat.combat.Combo;
import io.github.nicksementsov.knightcombat.combat.CombatRules;
import io.github.nicksementsov.knightcombat.combat.CombatState;
import io.github.nicksementsov.knightcombat.combat.Lances;
import io.github.nicksementsov.knightcombat.combat.MountTracker;
import io.github.nicksementsov.knightcombat.combat.Phase;
import io.github.nicksementsov.knightcombat.combat.Shields;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
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
    private static boolean slashHeld;
    /** The key of the last attack, while it is held (a held attack becomes a heavy attack). */
    private static KeyMapping holdKey;
    private static long dodgeReadyAt = Long.MIN_VALUE / 2;
    // Double taps of left, right and back for dodging: whether each key was down last tick and when it was last pressed.
    private static final boolean[] TAP_DOWN = new boolean[3];
    private static final long[] TAP_AT = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
    /** Ticks between the two presses of a double tap. */
    private static final int DOUBLE_TAP = 6;
    private static Combo comboShown = Combo.NONE;
    private static long comboAt = Long.MIN_VALUE / 2;
    /** The use key holds a lance couched. */
    private static boolean couching;
    /** How fast the local player's mount runs, for the force of a couched lance (the client moves the mount itself). */
    private static final MountTracker MOUNT = new MountTracker();
    private static boolean useWasDown;
    private static boolean blocking;
    private static long lastScrollAt = Long.MIN_VALUE / 2;
    private static AttackType lastScrollAttack;
    private static Phase lastOwnPhase = Phase.IDLE;

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

    /** Whether the key of the local player's last attack is still held. */
    public static boolean attackHeld() {
        return holdKey != null;
    }

    /** The finisher the local player last started, for the HUD. */
    public static Combo comboShown() {
        return comboShown;
    }

    public static long comboAt() {
        return comboAt;
    }

    /** How hard a lance couched by the local player would strike now, 0 to 1, from the speed of the mount along the look. */
    public static float chargePower() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() == null) {
            return 0;
        }
        return Lances.power(MOUNT.speedAlong(player.getYRot() * Mth.DEG_TO_RAD));
    }

    // ---------------------------------------------------------------- packets

    public static void onState(CombatStatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        FighterAnim anim = animOf(packet.entityId());
        Phase phase = Phase.byId(packet.phase());
        Combo combo = Combo.byId(packet.combo());
        if (mc.player != null && packet.entityId() == mc.player.getId() && phase == Phase.WINDUP && combo != Combo.NONE
                && combo != Combo.KICK_FOLLOW && (anim.phase != Phase.WINDUP || anim.combo != combo)) {
            comboShown = combo;
            comboAt = ticks;
        }
        anim.apply(phase, AttackType.byId(packet.attack()), packet.flags(), combo, packet.length(), ticks - packet.elapsed());
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
                // Fighters with a weapon, a lance or a shield are animated.
                boolean armed = Weapons.isWeapon(other.getMainHandItem()) || Lances.isLance(other.getMainHandItem())
                        || Shields.isShield(other.getOffhandItem());
                FighterAnim anim = armed ? animOf(other.getId()) : ANIMS.get(other.getId());
                if (anim != null) {
                    anim.watchHeld(other);
                    anim.advance(other, other == player);
                    anim.tickBody(other);
                }
            }
            trackMount(player);
            // The own swing that just began its release (or a kick going out) carries the player forward.
            FighterAnim own = ANIMS.get(player.getId());
            Phase phase = own != null ? own.phase : Phase.IDLE;
            if ((phase == Phase.RELEASE || phase == Phase.KICK) && phase != lastOwnPhase) {
                CombatFx.onOwnRelease(player, own, Weapons.of(player.getMainHandItem()));
            }
            lastOwnPhase = phase;
            return;
        }
        ticks++;
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        boolean ready = mc.screen == null && player.isAlive() && !player.isSpectator();
        boolean canFight = weapon != null && ready;
        // A shield alone is enough to kick (bash) and dodge.
        boolean canAct = ready && (weapon != null || !Shields.carried(player).isEmpty());
        boolean useDown = mc.options.keyUse.isDown();

        // On horseback the use key couches a lance or spear for a charge.
        boolean canCouch = ready && Lances.canCouch(player);
        if (canCouch && useDown && !couching) {
            couching = true;
            send(CombatAction.COUCH_START);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK) {
                anim.apply(Phase.COUCH, anim.attack, 0, Combo.NONE, 0, ticks);
            }
        }
        if (couching && (!useDown || !canCouch)) {
            couching = false;
            send(CombatAction.COUCH_STOP);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.COUCH) {
                anim.apply(Phase.IDLE, anim.attack, 0, Combo.NONE, 0, ticks);
            }
        }

        // An attack from behind a raised shield lowers it and strikes (Minecraft would swallow the click).
        if (canFight && Shields.raised(player) && consume(mc.options.keyAttack)) {
            slashHeld = true;
            attack(player, weapon, AttackType.SLASH, mc.options.keyAttack);
        }

        // Blocking: the use key starts a block unless it was pressed while looking at a block (doors, chests...).
        if (useDown && !useWasDown && canFight && !canCouch && canWeaponBlock(player, weapon) && !usesTarget(mc.hitResult)) {
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

        // The key of an attack let go: if it is let go before the wind-up ends, the attack is not heavy.
        if (holdKey != null && (!holdKey.isDown() || !canFight)) {
            holdKey = null;
            send(CombatAction.ATTACK_RELEASE);
        }
        if (consume(KeyBindings.OVERHEAD) && canFight) {
            attack(player, weapon, AttackType.OVERHEAD, KeyBindings.OVERHEAD);
        }
        if (consume(KeyBindings.STAB) && canFight) {
            attack(player, weapon, AttackType.STAB, KeyBindings.STAB);
        }
        if (consume(KeyBindings.FEINT) && canFight) {
            send(CombatAction.FEINT);
            FighterAnim anim = animOf(player.getId());
            if (anim.phase == Phase.WINDUP && stamina >= CombatRules.FEINT_COST) {
                anim.apply(Phase.RECOVERY, anim.attack, CombatState.FEINT, CombatRules.FEINT_RECOVERY, ticks);
            }
        }
        if (consume(KeyBindings.KICK) && canAct) {
            lowerShield(player);
            send(CombatAction.KICK);
            FighterAnim anim = animOf(player.getId());
            // A wind-up can be cancelled into a kick for the price of a feint. With a shield in hand it is a shield bash.
            boolean cancel = anim.phase == Phase.WINDUP;
            boolean bash = CombatConfig.shields() && !Shields.carried(player).isEmpty();
            float cost = CombatRules.KICK_COST + (cancel ? CombatRules.FEINT_COST : 0);
            if ((anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK || cancel) && stamina >= cost) {
                anim.apply(Phase.KICK, AttackType.KICK, bash ? CombatState.SHIELD_BASH : 0, Combo.NONE,
                        bash ? CombatRules.SHIELD_BASH_WINDUP : CombatRules.KICK_WINDUP, ticks);
            }
        }
        if (consume(KeyBindings.BATTLE_CRY) && canFight) {
            send(CombatAction.BATTLE_CRY);
        }
        if (consume(KeyBindings.THROW) && canFight) {
            throwWeapon(player, weapon);
        }
        if (consume(KeyBindings.DODGE) && canAct) {
            dodge(player, inputDirection(player));
        }
        doubleTaps(mc, player, canAct);
        if (!mc.options.keyAttack.isDown()) {
            slashHeld = false;
        }
    }

    /** The direction the player is moving: -1 left, 1 right, 2 forward, 0 back (also when standing still). */
    private static int inputDirection(LocalPlayer player) {
        float left = player.input.leftImpulse;
        float forward = player.input.forwardImpulse;
        if (Math.abs(left) > 0.1f && Math.abs(left) >= Math.abs(forward)) {
            return left > 0 ? -1 : 1;
        }
        return forward > 0.1f ? 2 : 0;
    }

    /** Left, right or back pressed twice quickly dodges that way. */
    private static void doubleTaps(Minecraft mc, LocalPlayer player, boolean canFight) {
        KeyMapping[] keys = {mc.options.keyLeft, mc.options.keyRight, mc.options.keyDown};
        int[] directions = {-1, 1, 0};
        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i].isDown();
            if (down && !TAP_DOWN[i]) {
                if (canFight && CombatConfig.DOUBLE_TAP_DODGE.get() && ticks - TAP_AT[i] <= DOUBLE_TAP) {
                    dodge(player, directions[i]);
                    TAP_AT[i] = Long.MIN_VALUE / 2;
                } else {
                    TAP_AT[i] = ticks;
                }
            }
            TAP_DOWN[i] = down;
        }
    }

    /** A quick step in {@code direction} (-1 left, 1 right, 2 forward, 0 back); a wind-up is cancelled for the price of a feint. */
    private static void dodge(LocalPlayer player, int direction) {
        FighterAnim anim = animOf(player.getId());
        boolean cancel = anim.phase == Phase.WINDUP;
        boolean free = anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK || cancel || anim.phase == Phase.RECOVERY
                && (anim.has(CombatState.REBOUND) || anim.has(CombatState.FEINT) || anim.progress(0) >= 0.5f);
        float cost = CombatRules.DODGE_COST + (cancel ? CombatRules.FEINT_COST : 0);
        if (!CombatConfig.dodges() || !free || stamina < cost || ticks < dodgeReadyAt || !player.onGround() || player.isInWater()
                || player.isPassenger()) {
            return;
        }
        lowerShield(player);
        send(CombatAction.dodge(direction));
        dodgeReadyAt = ticks + CombatRules.DODGE_LENGTH + CombatRules.DODGE_COOLDOWN;
        int flags = switch (direction) {
            case -1 -> CombatState.DODGE_LEFT;
            case 1 -> CombatState.DODGE_RIGHT;
            case 2 -> CombatState.DODGE_FORWARD;
            default -> 0;
        };
        anim.apply(Phase.DODGE, anim.attack, flags, Combo.NONE, CombatRules.DODGE_LENGTH, ticks);
        CombatFx.onOwnDodge(player, direction);
    }

    /** Draws the weapon back to throw it; a wind-up is cancelled into the throw for the price of a feint. */
    private static void throwWeapon(LocalPlayer player, WeaponStats weapon) {
        lowerShield(player);
        send(CombatAction.THROW);
        FighterAnim anim = animOf(player.getId());
        boolean cancel = anim.phase == Phase.WINDUP;
        boolean free = anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK || cancel || anim.phase == Phase.RECOVERY
                && (anim.has(CombatState.REBOUND) || anim.has(CombatState.FEINT) || anim.progress(0) >= 0.5f);
        float cost = CombatRules.THROW_COST + (cancel ? CombatRules.FEINT_COST : 0);
        if (CombatConfig.weaponThrowing() && free && stamina >= cost) {
            anim.apply(Phase.THROW, anim.attack, 0, Combo.NONE, CombatRules.throwWindup(weapon), ticks);
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
        if (event.isUseItem() && (Lances.canCouch(player)
                || CombatConfig.mountedCharges() && player.isPassenger() && Lances.isLance(player.getMainHandItem()))) {
            // In the saddle the use key couches the lance (and Epic Knights' own raising and lowering of lances is not
            // used). On foot it goes on to the shield in the other hand.
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (event.isUseItem() && CombatConfig.shields() && !Shields.carried(player).isEmpty() && busy(player)) {
            // No raising the shield in the middle of an attack, a kick, a throw or a dodge.
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (weapon == null) {
            return;
        }
        if (event.isAttack()) {
            if (!slashHeld) {
                boolean onBlock = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
                // Boats, minecarts, item frames and paintings are broken the usual way.
                boolean onObject = mc.hitResult instanceof EntityHitResult hit && !(hit.getEntity() instanceof LivingEntity);
                if (player.isShiftKeyDown() || onObject || onBlock && !enemyInReach(player, weapon)) {
                    return;
                }
                slashHeld = true;
                attack(player, weapon, AttackType.SLASH, mc.options.keyAttack);
            }
            event.setCanceled(true);
            event.setSwingHand(false);
        } else if (event.isUseItem() && blocking) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * Minecraft slows a player holding a shield up to a fifth of the walking speed; a knight can still advance behind
     * a shield at about half speed (a large shield slower).
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (CombatConfig.shields() && Shields.raised(player) && !player.isPassenger()) {
            float boost = Shields.size(player.getUseItem()) == Shields.Size.LARGE ? 1.5f : 2.25f;
            event.getInput().leftImpulse *= boost;
            event.getInput().forwardImpulse *= boost;
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
        // A turn of the wheel cannot be held, so these attacks are never heavy.
        attack(player, weapon, type, null);
    }

    /** An attack; {@code key} is the key that made it, which makes it heavy if it is held through the wind-up. */
    private static void attack(LocalPlayer player, WeaponStats weapon, AttackType type, KeyMapping key) {
        lowerShield(player);
        send(CombatAction.of(type));
        holdKey = key;
        if (key == null) {
            send(CombatAction.ATTACK_RELEASE);
        }
        // Start the wind-up right away instead of waiting for the server, which confirms or corrects it.
        FighterAnim anim = animOf(player.getId());
        boolean free = anim.phase == Phase.IDLE || anim.phase == Phase.BLOCK
                || anim.phase == Phase.RECOVERY && anim.has(CombatState.FEINT);
        if (free) {
            boolean riposte = riposteReady();
            boolean sprint = !riposte && player.isSprinting();
            int length = CombatRules.windup(weapon, type, riposte, false, Combo.NONE, sprint);
            anim.apply(Phase.WINDUP, type, (riposte ? CombatState.RIPOSTE : 0) | (sprint ? CombatState.SPRINT : 0), Combo.NONE, length, ticks);
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
        return weapon.twoHanded() || !Shields.isShield(player.getOffhandItem());
    }

    /** Follows the mount of the local player, as the server does, for the force of a couched lance. */
    private static void trackMount(LocalPlayer player) {
        MOUNT.track(player.getVehicle() != null ? player.getVehicle().position() : null);
    }

    /** Lowers a raised shield at once (the server would lower it a moment later anyway). */
    private static void lowerShield(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (Shields.raised(player) && mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(player);
        }
    }

    /** Whether the player is busy with something the shield cannot be raised during: attacks, kicks, throws, dodges, reeling. */
    private static boolean busy(Player player) {
        FighterAnim anim = ANIMS.get(player.getId());
        if (anim == null) {
            return false;
        }
        return switch (anim.phase) {
            case WINDUP, RELEASE, KICK, THROW, DODGE, CRY -> true;
            case STAGGER -> !anim.has(CombatState.FLINCHED);
            case RECOVERY -> !anim.has(CombatState.REBOUND) && !anim.has(CombatState.FEINT) && anim.progress(0) < 0.5f;
            default -> false;
        };
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
        lastOwnPhase = Phase.IDLE;
        stamina = -1;
        blocking = false;
        slashHeld = false;
        couching = false;
        holdKey = null;
        comboShown = Combo.NONE;
        lastFeedback = null;
    }
}
