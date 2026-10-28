package io.github.nicksementsov.knightcombat.combat;

import io.github.nicksementsov.knightcombat.CombatConfig;
import io.github.nicksementsov.knightcombat.KnightCombat;
import io.github.nicksementsov.knightcombat.network.CombatAction;
import io.github.nicksementsov.knightcombat.network.CombatNetwork;
import io.github.nicksementsov.knightcombat.network.CombatStatePacket;
import io.github.nicksementsov.knightcombat.network.Feedback;
import io.github.nicksementsov.knightcombat.network.FeedbackPacket;
import io.github.nicksementsov.knightcombat.network.Impact;
import io.github.nicksementsov.knightcombat.network.ImpactPacket;
import io.github.nicksementsov.knightcombat.network.StaminaPacket;
import io.github.nicksementsov.knightcombat.entity.ThrownWeapon;
import io.github.nicksementsov.knightcombat.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The combat rules. Runs on the server: reads inputs, advances every player's {@link CombatState} each tick,
 * traces the weapon during releases and resolves hits, blocks, parries, counters and kicks.
 */
@Mod.EventBusSubscriber(modid = KnightCombat.MODID)
public final class CombatManager {
    private static final Map<UUID, CombatState> STATES = new HashMap<>();
    private static final UUID SLOW_ID = UUID.fromString("5b0f7c9e-2f4e-4d8a-9c1e-6a8b1f0e3d21");
    /** Extra width of hit boxes: weapons are wider than a ray. */
    private static final double HIT_MARGIN = 0.2;
    /** Set while the mod itself damages an entity, so its own hits are not treated as blockable vanilla damage. */
    private static boolean applyingHit;
    /** Set while a thrown weapon damages an entity: it can be blocked, and a knight it hits cries out. */
    private static boolean weaponHurt;

    private CombatManager() {
    }

    public static CombatState state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new CombatState());
    }

    // ---------------------------------------------------------------- inputs

    public static void handleInput(ServerPlayer player, CombatAction action) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        CombatState s = state(player);
        long now = player.level().getGameTime();
        AttackType attack = action.attack();
        if (attack != null) {
            attackInput(player, s, attack, now);
        } else {
            switch (action) {
                case FEINT -> feint(player, s, now);
                case KICK -> kickInput(player, s, now);
                case BLOCK_START -> {
                    s.blockHeld = true;
                    s.blockPressedAt = now;
                    blockInput(player, s, now);
                }
                case BLOCK_STOP -> {
                    s.blockHeld = false;
                    if (s.phase == Phase.BLOCK) {
                        leaveBlock(s, now);
                    }
                }
                case BATTLE_CRY -> cryInput(player, s, now);
                case ATTACK_RELEASE -> s.attackHeld = false;
                case THROW -> throwInput(player, s, now);
                case DODGE_BACK -> dodgeInput(player, s, 0, now);
                case DODGE_LEFT -> dodgeInput(player, s, CombatState.DODGE_LEFT, now);
                case DODGE_RIGHT -> dodgeInput(player, s, CombatState.DODGE_RIGHT, now);
                case DODGE_FORWARD -> dodgeInput(player, s, CombatState.DODGE_FORWARD, now);
                default -> {
                }
            }
        }
        // Always answer, so a client that guessed wrong about the result of its input is corrected.
        s.syncState = true;
        sync(player, s);
    }

    private static void attackInput(ServerPlayer player, CombatState s, AttackType type, long now) {
        if (Weapons.of(player.getMainHandItem()) == null) {
            return;
        }
        s.attackHeld = true;
        switch (s.phase) {
            case IDLE -> startWindup(player, s, type, now <= s.riposteUntil, false, now);
            case BLOCK -> {
                boolean riposte = now <= s.riposteUntil;
                leaveBlock(s, now);
                startWindup(player, s, type, riposte, false, now);
            }
            case WINDUP -> morph(player, s, type, now);
            case RELEASE, DODGE -> s.queued = type;
            case RECOVERY -> {
                if (s.has(CombatState.FEINT)) {
                    startWindup(player, s, type, false, false, now);
                } else if (!s.has(CombatState.REBOUND) && !s.has(CombatState.THROWN) && s.attack.usesWeapon()
                        && s.phaseTicks * 2 <= s.phaseLength) {
                    startWindup(player, s, type, false, true, now);
                } else {
                    s.queued = type;
                }
            }
            default -> {
            }
        }
    }

    private static void startWindup(ServerPlayer player, CombatState s, AttackType type, boolean riposte, boolean combo, long now) {
        ItemStack stack = player.getMainHandItem();
        WeaponStats weapon = Weapons.of(stack);
        if (weapon == null) {
            s.queued = null;
            s.breakChain();
            s.set(Phase.IDLE, 0);
            return;
        }
        s.chain(type, combo);
        Combo finisher = Combo.NONE;
        if (CombatConfig.finishers() && !riposte) {
            finisher = combo ? Combo.match(s.chain, s.chainLength) : now <= s.kickFollowUntil ? Combo.KICK_FOLLOW : Combo.NONE;
        }
        s.kickFollowUntil = Long.MIN_VALUE / 2;
        // A running attack: the weight of the run goes into the blow.
        boolean sprint = !riposte && !combo && player.isSprinting();
        int length = CombatRules.windup(weapon, type, riposte, combo, finisher, sprint);
        // Slashes of a combo alternate between right-to-left and left-to-right.
        boolean mirrored = type == AttackType.SLASH && combo && s.attack == AttackType.SLASH && !s.lastSlashMirrored;
        if (type == AttackType.SLASH) {
            s.lastSlashMirrored = mirrored;
        }
        s.attack = type;
        s.combo = finisher;
        s.weapon = weapon;
        s.weaponItem = stack.getItem();
        s.queued = null;
        s.hitEntities.clear();
        s.hitSomething = false;
        s.riposteUntil = Long.MIN_VALUE / 2;
        s.flags = (mirrored ? CombatState.MIRRORED : 0) | (riposte ? CombatState.RIPOSTE : 0) | (combo ? CombatState.COMBO : 0)
                | (sprint ? CombatState.SPRINT : 0);
        s.set(Phase.WINDUP, length);
    }


    /** Switching to another attack during the wind-up. */
    private static void morph(ServerPlayer player, CombatState s, AttackType type, long now) {
        if (type == s.attack || s.has(CombatState.RIPOSTE) || s.has(CombatState.HEAVY) || s.phaseTicks >= s.phaseLength * CombatRules.MORPH_LIMIT
                || s.stamina < CombatRules.MORPH_COST || s.weapon == null) {
            return;
        }
        s.spend(CombatRules.MORPH_COST, now);
        int elapsed = s.phaseTicks;
        boolean combo = s.has(CombatState.COMBO);
        // The morphed attack takes the place of the last one in the chain, so it may become (or stop being) a finisher.
        if (s.chainLength > 0) {
            s.chain[s.chainLength - 1] = type;
        }
        if (combo && CombatConfig.finishers()) {
            s.combo = Combo.match(s.chain, s.chainLength);
        } else if (s.combo != Combo.KICK_FOLLOW) {
            s.combo = Combo.NONE;
        }
        int windup = CombatRules.windup(s.weapon, type, false, combo, s.combo, s.has(CombatState.SPRINT));
        s.attack = type;
        s.flags &= ~CombatState.MIRRORED;
        s.set(Phase.WINDUP, Math.max(elapsed + 2, windup + 1));
        s.phaseTicks = elapsed;
        playSound(player, ModSounds.FEINT.get(), 0.4f, 1.2f);
    }

    private static void feint(ServerPlayer player, CombatState s, long now) {
        if (s.phase != Phase.WINDUP || s.phaseTicks >= s.phaseLength - 1 || s.stamina < CombatRules.FEINT_COST) {
            return;
        }
        s.spend(CombatRules.FEINT_COST, now);
        s.queued = null;
        s.breakChain();
        s.flags = CombatState.FEINT;
        s.set(Phase.RECOVERY, CombatRules.FEINT_RECOVERY);
        playSound(player, ModSounds.FEINT.get(), 0.6f, 1.0f);
    }

    private static void kickInput(ServerPlayer player, CombatState s, long now) {
        // A wind-up can be cancelled into a kick, for the price of a feint.
        boolean cancel = s.phase == Phase.WINDUP;
        boolean free = s.phase == Phase.IDLE || s.phase == Phase.BLOCK || cancel
                || s.phase == Phase.RECOVERY && s.has(CombatState.FEINT);
        float cost = CombatRules.KICK_COST + (cancel ? CombatRules.FEINT_COST : 0);
        if (!free || s.stamina < cost) {
            return;
        }
        if (s.phase == Phase.BLOCK) {
            leaveBlock(s, now);
        }
        s.spend(cost, now);
        s.attack = AttackType.KICK;
        s.breakChain();
        s.flags = 0;
        s.queued = null;
        s.set(Phase.KICK, CombatRules.KICK_WINDUP);
    }

    private static void throwInput(ServerPlayer player, CombatState s, long now) {
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        if (weapon == null || !CombatConfig.weaponThrowing()) {
            return;
        }
        boolean cancel = s.phase == Phase.WINDUP;
        boolean free = s.phase == Phase.IDLE || s.phase == Phase.BLOCK || cancel
                || s.phase == Phase.RECOVERY && canBlockFromRecovery(s);
        float cost = CombatRules.THROW_COST + (cancel ? CombatRules.FEINT_COST : 0);
        if (!free || s.stamina < cost) {
            return;
        }
        if (s.phase == Phase.BLOCK) {
            leaveBlock(s, now);
        }
        s.spend(cost, now);
        s.weapon = weapon;
        s.weaponItem = player.getMainHandItem().getItem();
        s.breakChain();
        s.flags = 0;
        s.queued = null;
        s.set(Phase.THROW, CombatRules.throwWindup(weapon));
    }

    /** A quick step: {@code direction} is one of the dodge flags of {@link CombatState}, or 0 for a step back. */
    private static void dodgeInput(ServerPlayer player, CombatState s, int direction, long now) {
        if (!CombatConfig.dodges() || Weapons.of(player.getMainHandItem()) == null || now < s.dodgeReadyAt || player.isPassenger()
                || player.isFallFlying() || player.isInWater()) {
            return;
        }
        boolean cancel = s.phase == Phase.WINDUP;
        boolean free = s.phase == Phase.IDLE || s.phase == Phase.BLOCK || cancel
                || s.phase == Phase.RECOVERY && canBlockFromRecovery(s);
        float cost = CombatRules.DODGE_COST + (cancel ? CombatRules.FEINT_COST : 0);
        if (!free || s.stamina < cost) {
            return;
        }
        if (s.phase == Phase.BLOCK) {
            leaveBlock(s, now);
        }
        s.spend(cost, now);
        s.breakChain();
        s.queued = null;
        s.flags = direction;
        s.set(Phase.DODGE, CombatRules.DODGE_LENGTH);
        s.dodgeReadyAt = now + CombatRules.DODGE_LENGTH + CombatRules.DODGE_COOLDOWN;
        playSound(player, ModSounds.DODGE.get(), 0.7f, 0.92f + player.getRandom().nextFloat() * 0.16f);
    }

    private static void blockInput(ServerPlayer player, CombatState s, long now) {
        if (!canWeaponBlock(player)) {
            return;
        }
        switch (s.phase) {
            case IDLE -> {
                if (now >= s.blockReadyAt) {
                    startBlock(player, s, now, true);
                }
            }
            case WINDUP -> {
                // Cancelling an attack into a parry costs as much as a feint.
                if (s.stamina >= CombatRules.FEINT_COST) {
                    s.spend(CombatRules.FEINT_COST, now);
                    s.breakChain();
                    startBlock(player, s, now, true);
                }
            }
            case RECOVERY -> {
                if (canBlockFromRecovery(s)) {
                    startBlock(player, s, now, true);
                }
            }
            case STAGGER -> {
                if (s.has(CombatState.FLINCHED)) {
                    startBlock(player, s, now, true);
                }
            }
            default -> {
            }
        }
    }

    /** Blocking with the weapon needs the weapon in the main hand; a one-handed weapon with a shield blocks with the shield. */
    private static boolean canWeaponBlock(ServerPlayer player) {
        WeaponStats weapon = Weapons.of(player.getMainHandItem());
        return weapon != null && (weapon.twoHanded() || !(player.getOffhandItem().getItem() instanceof ShieldItem));
    }

    private static boolean canBlockFromRecovery(CombatState s) {
        return s.has(CombatState.REBOUND) || s.has(CombatState.FEINT) || s.phaseTicks * 2 >= s.phaseLength;
    }

    private static void startBlock(ServerPlayer player, CombatState s, long now, boolean pressed) {
        s.weapon = Weapons.of(player.getMainHandItem());
        s.weaponItem = player.getMainHandItem().getItem();
        s.flags = 0;
        s.queued = null;
        // A block that starts right after the key was pressed opens with a parry; a block that was simply held does not.
        boolean fresh = pressed || now - s.blockPressedAt <= 4;
        s.parryUntil = fresh ? now + CombatRules.PARRY_WINDOW : Long.MIN_VALUE / 2;
        s.blockLanded = false;
        s.set(Phase.BLOCK, 0);
    }

    private static void leaveBlock(CombatState s, long now) {
        if (!s.blockLanded) {
            s.blockReadyAt = now + CombatRules.BLOCK_RECOVERY;
        }
        s.set(Phase.IDLE, 0);
    }

    private static void cryInput(ServerPlayer player, CombatState s, long now) {
        if (s.phase != Phase.IDLE && s.phase != Phase.BLOCK || now < s.cryReadyAt) {
            return;
        }
        if (s.phase == Phase.BLOCK) {
            leaveBlock(s, now);
        }
        s.flags = 0;
        s.queued = null;
        s.breakChain();
        s.cryReadyAt = now + CombatConfig.battleCryCooldownTicks();
        s.set(Phase.CRY, CombatRules.CRY_LENGTH);
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER && event.player instanceof ServerPlayer player) {
            tick(player);
        }
    }

    private static void tick(ServerPlayer player) {
        CombatState s = state(player);
        long now = player.level().getGameTime();
        float max = CombatConfig.maxStamina();
        if (s.stamina < 0 || s.stamina > max) {
            s.stamina = max;
        }
        if (!player.isAlive() || player.isSpectator()) {
            if (s.phase != Phase.IDLE) {
                s.queued = null;
                s.set(Phase.IDLE, 0);
            }
            sync(player, s);
            return;
        }

        WeaponStats held = Weapons.of(player.getMainHandItem());
        Item heldItem = player.getMainHandItem().getItem();
        if (s.heldKnown && heldItem != s.lastHeld) {
            s.attackHeld = false;
            if (held != null) {
                // Steel drawn from the scabbard.
                playSound(player, ModSounds.DRAW.get(), 0.45f, 0.95f + player.getRandom().nextFloat() * 0.1f);
            }
        }
        s.lastHeld = heldItem;
        s.heldKnown = true;
        boolean weaponPhase = s.phase == Phase.WINDUP || s.phase == Phase.RELEASE || s.phase == Phase.BLOCK || s.phase == Phase.THROW;
        if (weaponPhase && (held == null || heldItem != s.weaponItem)) {
            // The weapon was put away or swapped: the attack or block is lost.
            s.queued = null;
            s.flags = 0;
            s.breakChain();
            s.set(Phase.IDLE, 0);
        }

        switch (s.phase) {
            case WINDUP -> {
                if (++s.phaseTicks >= s.phaseLength) {
                    if (canCharge(s)) {
                        startCharge(player, s, now);
                    } else {
                        startRelease(player, s);
                    }
                }
            }
            case THROW -> {
                if (++s.phaseTicks >= s.phaseLength) {
                    throwWeapon(player, s);
                }
            }
            case DODGE -> {
                if (++s.phaseTicks >= s.phaseLength) {
                    finishPhase(player, s, now);
                }
            }
            case RELEASE -> releaseTick(player, s, now);
            case RECOVERY, STAGGER -> {
                if (++s.phaseTicks >= s.phaseLength) {
                    finishPhase(player, s, now);
                }
            }
            case KICK -> {
                if (++s.phaseTicks >= s.phaseLength) {
                    kick(player, now);
                    s.flags = 0;
                    s.set(Phase.RECOVERY, CombatRules.KICK_RECOVERY);
                }
            }
            case CRY -> {
                if (++s.phaseTicks == CombatRules.CRY_EFFECT_TICK) {
                    battleCry(player, s, max);
                }
                if (s.phaseTicks >= s.phaseLength) {
                    finishPhase(player, s, now);
                }
            }
            default -> {
            }
        }

        // A block key that is still held raises the guard as soon as the fighter is free again.
        if (s.blockHeld && held != null && canWeaponBlock(player)
                && (s.phase == Phase.IDLE && now >= s.blockReadyAt || s.phase == Phase.RECOVERY && canBlockFromRecovery(s))) {
            startBlock(player, s, now, false);
        }

        if (s.phase != Phase.BLOCK && now - s.lastStaminaUse >= CombatRules.REGEN_DELAY && s.stamina < max) {
            s.restore(CombatConfig.staminaRegenPerTick(), max);
        }
        updateSlowness(player, s);
        sync(player, s);
    }

    private static void finishPhase(ServerPlayer player, CombatState s, long now) {
        AttackType queued = s.queued;
        s.flags = 0;
        s.queued = null;
        if (queued != null && Weapons.of(player.getMainHandItem()) != null) {
            startWindup(player, s, queued, now <= s.riposteUntil, false, now);
        } else {
            s.breakChain();
            s.set(Phase.IDLE, 0);
        }
    }

    /** Whether the wind-up that just ended goes on as the charge of a heavy attack: its key is still held. */
    private static boolean canCharge(CombatState s) {
        return s.attackHeld && CombatConfig.heavyAttacks() && !s.has(CombatState.HEAVY) && !s.has(CombatState.RIPOSTE)
                && s.attack.usesWeapon() && s.weapon != null && s.stamina >= CombatRules.HEAVY_COST;
    }

    /** The weapon is drawn back further and the blow gathers strength. */
    private static void startCharge(ServerPlayer player, CombatState s, long now) {
        s.spend(CombatRules.HEAVY_COST, now);
        s.flags |= CombatState.HEAVY;
        s.set(Phase.WINDUP, CombatRules.heavyWindup(s.weapon, s.attack));
        playSound(player, ModSounds.EFFORT.get(), 0.6f, 0.95f + player.getRandom().nextFloat() * 0.1f);
    }

    private static void startRelease(ServerPlayer player, CombatState s) {
        AttackStats attack = s.weapon.attack(s.attack);
        s.set(Phase.RELEASE, Math.max(1, attack.release()));
        boolean strong = s.has(CombatState.HEAVY) || s.combo != Combo.NONE && s.combo != Combo.KICK_FOLLOW;
        boolean heavy = strong || s.weapon.grip() != Grip.ONE_HANDED || s.weapon.kind() == WeaponKind.BLUNT || s.weapon.kind() == WeaponKind.AXE;
        float pitch = (s.weapon.kind() == WeaponKind.DAGGER ? 1.25f : 1.0f) * (strong ? 0.88f : 1);
        playSound(player, heavy ? ModSounds.WHOOSH_HEAVY.get() : ModSounds.WHOOSH.get(), strong ? 1.0f : 0.8f,
                pitch + player.getRandom().nextFloat() * 0.12f);
        // Knights shout as they strike: always on heavy blows and finishers, often on overheads and with heavy
        // weapons, now and then on slashes.
        float chance = strong ? 1 : switch (s.attack) {
            case OVERHEAD -> 0.55f;
            case STAB -> 0.4f;
            default -> 0.3f;
        } + 0.2f * s.weapon.weight();
        yell(player, s, chance);
    }

    /** An effort shout with the given chance, but never two within a second and a half. */
    private static void yell(ServerPlayer player, CombatState s, float chance) {
        long now = player.level().getGameTime();
        if (!CombatConfig.attackYells() || now < s.yellReadyAt || player.getRandom().nextFloat() >= chance) {
            return;
        }
        s.yellReadyAt = now + CombatRules.YELL_COOLDOWN;
        playSound(player, ModSounds.ATTACK_YELL.get(), 0.9f, 0.92f + player.getRandom().nextFloat() * 0.16f);
    }

    private static void releaseTick(ServerPlayer player, CombatState s, long now) {
        float p0 = s.phaseTicks / (float) s.phaseLength;
        float p1 = (s.phaseTicks + 1) / (float) s.phaseLength;
        if (sweep(player, s, p0, p1, now)) {
            return;
        }
        if (++s.phaseTicks >= s.phaseLength) {
            if (!s.hitSomething) {
                s.spend(CombatRules.MISS_COST, now);
            }
            AttackType queued = s.queued;
            if (queued != null && Weapons.of(player.getMainHandItem()) != null) {
                startWindup(player, s, queued, false, true, now);
            } else {
                int recovery = s.weapon.attack(s.attack).recovery() + CombatRules.extraRecovery(s.flags);
                s.flags &= CombatState.MIRRORED;
                s.queued = null;
                s.set(Phase.RECOVERY, recovery);
            }
        }
    }

    /** The attack was stopped by a block or a wall: a recovery during which the attacker can block but not attack. */
    private static void rebound(CombatState s, int length) {
        s.queued = null;
        s.breakChain();
        s.flags = (s.flags & CombatState.MIRRORED) | CombatState.REBOUND;
        s.set(Phase.RECOVERY, length);
    }

    private static void stagger(CombatState s, int length, int flag) {
        s.queued = null;
        s.breakChain();
        s.flags = flag;
        s.set(Phase.STAGGER, length);
    }

    // ---------------------------------------------------------------- tracing the weapon

    /** Traces the weapon from release progress p0 to p1. Returns true when the attack was stopped. */
    private static boolean sweep(ServerPlayer player, CombatState s, float p0, float p1, long now) {
        WeaponStats weapon = s.weapon;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        Vec3 up = player.getUpVector(1);
        Vec3 right = look.cross(up);
        double[] forward = {look.x, look.y, look.z};
        double[] upArr = {up.x, up.y, up.z};
        double[] rightArr = {right.x, right.y, right.z};
        Combo combo = s.combo;
        double reach = weapon.reach() + combo.reach;
        List<Entity> candidates = player.level().getEntities(player,
                player.getBoundingBox().inflate(reach + 2), e -> canTarget(player, e));
        boolean mirrored = s.has(CombatState.MIRRORED);
        int samples = SwingGeometry.samples(s.attack, p0, p1, combo);
        for (int i = p0 <= 0 ? 0 : 1; i <= samples; i++) {
            float p = p0 + (p1 - p0) * i / samples;
            float yaw = SwingGeometry.yaw(s.attack, mirrored, p, combo);
            float pitch = SwingGeometry.pitch(s.attack, p, combo);
            double length = reach * SwingGeometry.reachFactor(s.attack, p);
            if (traceRay(player, s, candidates, eye, forward, upArr, rightArr, yaw, pitch, length, SwingGeometry.wallStops(s.attack, mirrored, p, combo), now)) {
                return true;
            }
            if (s.attack == AttackType.STAB) {
                float spread = SwingGeometry.STAB_SPREAD;
                float[][] offsets = {{spread, 0}, {-spread, 0}, {0, spread}, {0, -spread}};
                for (float[] offset : offsets) {
                    if (traceRay(player, s, candidates, eye, forward, upArr, rightArr, yaw + offset[0], pitch + offset[1], length, false, now)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record RayHit(Entity entity, Vec3 point, double distance) {
    }

    private static boolean traceRay(ServerPlayer player, CombatState s, List<Entity> candidates, Vec3 eye,
                                    double[] forward, double[] up, double[] right, float yaw, float pitch, double length,
                                    boolean wallStops, long now) {
        double[] dir = new double[3];
        SwingGeometry.direction(forward, up, right, yaw, pitch, dir);
        Vec3 end = eye.add(dir[0] * length, dir[1] * length, dir[2] * length);
        BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = blockHit.getType() == HitResult.Type.MISS ? length : eye.distanceTo(blockHit.getLocation());

        List<RayHit> hits = new ArrayList<>();
        for (Entity entity : candidates) {
            if (s.hitEntities.contains(entity.getId())) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius() + HIT_MARGIN);
            Optional<Vec3> point = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (point.isPresent()) {
                double distance = eye.distanceTo(point.get());
                if (distance <= limit) {
                    hits.add(new RayHit(entity, point.get(), distance));
                }
            }
        }
        hits.sort(Comparator.comparingDouble(RayHit::distance));
        for (RayHit hit : hits) {
            s.hitEntities.add(hit.entity.getId());
            if (resolveHit(player, s, hit.entity, hit.point, now)) {
                return true;
            }
        }

        if (wallStops && blockHit.getType() == HitResult.Type.BLOCK && blockHit.getDirection().getAxis().isHorizontal()
                && CombatConfig.wallsStopAttacks()) {
            hitWall(player, s, blockHit);
            return true;
        }
        return false;
    }

    private static boolean canTarget(ServerPlayer player, Entity entity) {
        if (!entity.isAlive() || !entity.isPickable() || entity.isSpectator() || entity == player.getVehicle()
                || player.isPassengerOfSameVehicle(entity) || entity.isAlliedTo(player)) {
            return false;
        }
        if (entity instanceof ArmorStand stand && stand.isMarker()) {
            return false;
        }
        if (entity instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
            return false;
        }
        if (entity instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof PartEntity<?> || entity instanceof EndCrystal;
    }

    /** Whether {@code defender} faces the point {@code from} closely enough to block something coming from there. */
    private static boolean facing(LivingEntity defender, Vec3 from) {
        Vec3 look = defender.getViewVector(1);
        Vec3 to = from.subtract(defender.getEyePosition());
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        double toLength = Math.sqrt(to.x * to.x + to.z * to.z);
        if (lookLength < 1.0E-4 || toLength < 1.0E-4) {
            return true;
        }
        double cos = (look.x * to.x + look.z * to.z) / (lookLength * toLength);
        return cos >= Math.cos(Math.toRadians(CombatRules.BLOCK_HALF_ANGLE));
    }

    // ---------------------------------------------------------------- resolving hits

    /** Resolves the weapon meeting {@code target}. Returns true when the attack was stopped. */
    private static boolean resolveHit(ServerPlayer player, CombatState s, Entity target, Vec3 point, long now) {
        AttackStats attack = s.weapon.attack(s.attack);
        if (target instanceof ServerPlayer defender) {
            CombatState d = state(defender);
            boolean facing = facing(defender, player.getEyePosition());
            if (facing && d.guarding()) {
                blocked(player, s, defender, d, attack, point, now);
                return true;
            }
            if (facing && d.phase == Phase.WINDUP && !d.has(CombatState.RIPOSTE) && !d.has(CombatState.HEAVY) && d.attack == s.attack
                    && d.phaseTicks <= CombatRules.COUNTER_WINDOW) {
                countered(player, s, defender, d, attack, point, now);
                return true;
            }
            if (facing && defender.isBlocking()) {
                shieldBlocked(player, s, defender, d, attack, point, now);
                return true;
            }
        }
        dealDamage(player, s, target, point);
        return false;
    }

    private static void blocked(ServerPlayer attacker, CombatState s, ServerPlayer defender, CombatState d,
                                AttackStats attack, Vec3 point, long now) {
        boolean parry = d.phase == Phase.BLOCK && now <= d.parryUntil;
        WeaponStats guard = d.weapon != null ? d.weapon : s.weapon;
        d.spend(attack.staminaDamage() * drain(s) * (parry ? CombatRules.PARRY_FACTOR : 1) * guard.blockCost(), now);
        d.blockLanded = true;
        d.syncState = true;
        if (d.stamina <= 0) {
            guardBroken(attacker, s, defender, d, point);
            return;
        }
        if (parry) {
            d.riposteUntil = now + CombatRules.RIPOSTE_WINDOW;
            rebound(s, attack.recovery() + CombatRules.REBOUND_PARRY);
            clash(attacker, point, ModSounds.PARRY.get(), 1.0f, 12);
            glint(attacker, point);
            // A parried blow throws the attacker back a step.
            push(attacker, defender, 0.3f);
            feedback(defender, Feedback.PARRIED);
            feedback(attacker, Feedback.GOT_PARRIED);
            impact(attacker, Impact.PARRY, attacker, defender, point, s.weapon.weight());
        } else {
            float weight = force(s);
            rebound(s, Math.max(4, attack.recovery() / 2 + CombatRules.REBOUND_BLOCK));
            clash(attacker, point, ModSounds.BLOCK.get(), 0.9f, 6);
            // Heavy blows drive the blocker back.
            push(defender, attacker, 0.1f + 0.25f * weight);
            feedback(defender, Feedback.BLOCKED);
            feedback(attacker, Feedback.GOT_BLOCKED);
            impact(attacker, Impact.BLOCK, attacker, defender, point, weight);
        }
    }

    private static void countered(ServerPlayer attacker, CombatState s, ServerPlayer defender, CombatState d,
                                  AttackStats attack, Vec3 point, long now) {
        WeaponStats guard = d.weapon != null ? d.weapon : s.weapon;
        d.spend(attack.staminaDamage() * drain(s) * CombatRules.COUNTER_FACTOR * guard.blockCost(), now);
        if (d.stamina <= 0) {
            guardBroken(attacker, s, defender, d, point);
            return;
        }
        // The counter-attack goes on as a riposte with only a few ticks of wind-up left.
        d.flags |= CombatState.RIPOSTE;
        d.phaseLength = Math.min(d.phaseLength, d.phaseTicks + CombatRules.COUNTER_REMAINING);
        d.syncState = true;
        rebound(s, attack.recovery() + CombatRules.REBOUND_PARRY);
        clash(attacker, point, ModSounds.PARRY.get(), 1.1f, 12);
        glint(attacker, point);
        push(attacker, defender, 0.3f);
        feedback(defender, Feedback.COUNTERED);
        feedback(attacker, Feedback.GOT_COUNTERED);
        impact(attacker, Impact.PARRY, attacker, defender, point, s.weapon.weight());
    }

    private static void shieldBlocked(ServerPlayer attacker, CombatState s, ServerPlayer defender, CombatState d,
                                      AttackStats attack, Vec3 point, long now) {
        d.spend(attack.staminaDamage() * drain(s) * CombatRules.SHIELD_FACTOR, now);
        ItemStack shield = defender.getUseItem();
        shield.hurtAndBreak(1 + Mth.floor(attack.damage() / 3), defender, p -> p.broadcastBreakEvent(defender.getUsedItemHand()));
        attacker.level().playSound(null, defender.getX(), defender.getY(), defender.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.9f + attacker.getRandom().nextFloat() * 0.2f);
        if (s.weapon.breaksShields() || d.stamina <= 0) {
            defender.disableShield(true);
        }
        if (d.stamina <= 0) {
            stagger(d, CombatRules.GUARD_BREAK, CombatState.GUARD_BROKEN);
            push(defender, attacker, 0.45f);
            feedback(defender, Feedback.GUARD_BROKEN);
            feedback(attacker, Feedback.BROKE_GUARD);
            impact(attacker, Impact.GUARD_BREAK, attacker, defender, point, s.weapon.weight());
        } else {
            push(defender, attacker, 0.1f + 0.25f * s.weapon.weight());
            feedback(defender, Feedback.BLOCKED);
            feedback(attacker, Feedback.GOT_BLOCKED);
            impact(attacker, Impact.BLOCK, attacker, defender, point, s.weapon.weight());
        }
        rebound(s, Math.max(4, attack.recovery() / 2 + CombatRules.REBOUND_BLOCK));
    }

    /** The defender ran out of stamina: no damage, but they cannot act for a while and the attacker may follow up. */
    private static void guardBroken(ServerPlayer attacker, CombatState s, ServerPlayer defender, CombatState d, Vec3 point) {
        stagger(d, CombatRules.GUARD_BREAK, CombatState.GUARD_BROKEN);
        s.queued = null;
        s.flags &= CombatState.MIRRORED;
        s.set(Phase.RECOVERY, Math.max(2, s.weapon.attack(s.attack).recovery() / 2));
        clash(attacker, point, ModSounds.GUARD_BREAK.get(), 1.0f, 16);
        glint(attacker, point);
        // The broken guard knocks the defender off balance.
        push(defender, attacker, 0.45f);
        feedback(defender, Feedback.GUARD_BROKEN);
        feedback(attacker, Feedback.BROKE_GUARD);
        impact(attacker, Impact.GUARD_BREAK, attacker, defender, point, s.weapon.weight());
    }

    private static void hitWall(ServerPlayer player, CombatState s, BlockHitResult hit) {
        rebound(s, s.weapon.attack(s.attack).recovery() + CombatRules.REBOUND_WALL);
        ServerLevel level = player.serverLevel();
        Vec3 point = hit.getLocation();
        level.playSound(null, point.x, point.y, point.z, ModSounds.WALL.get(), SoundSource.PLAYERS, 0.9f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(hit.getBlockPos())),
                point.x, point.y, point.z, 10, 0.05, 0.05, 0.05, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 4, 0.05, 0.05, 0.05, 0.3);
        feedback(player, Feedback.WALL);
        impact(player, Impact.WALL, player, null, point, s.weapon.weight());
    }

    /** How much stronger than usual an attack is: heavy, a finisher, a running attack. */
    private static float bonus(CombatState s) {
        float bonus = s.combo.damage;
        if (s.has(CombatState.HEAVY)) {
            bonus *= CombatRules.HEAVY_DAMAGE;
        }
        if (s.has(CombatState.SPRINT)) {
            bonus *= CombatRules.SPRINT_DAMAGE;
        }
        return Math.min(CombatRules.MAX_DAMAGE_BONUS, bonus);
    }

    /** Multiplier of the stamina a blocker of this attack loses. */
    private static float drain(CombatState s) {
        return s.combo.staminaDamage * (s.has(CombatState.HEAVY) ? CombatRules.HEAVY_STAMINA_DAMAGE : 1);
    }

    /** How hard the blow feels (0 to 1, for knockback, shaking cameras and hit-stops): the weapon's weight, more for strong blows. */
    private static float force(CombatState s) {
        float force = s.weapon.weight();
        if (s.has(CombatState.HEAVY)) {
            force += 0.3f;
        }
        if (s.combo != Combo.NONE && s.combo != Combo.KICK_FOLLOW || s.has(CombatState.SPRINT)) {
            force += 0.2f;
        }
        return Math.min(1, force);
    }

    private static void dealDamage(ServerPlayer player, CombatState s, Entity target, Vec3 point) {
        ItemStack weapon = player.getMainHandItem();
        LivingEntity living = target instanceof LivingEntity le ? le
                : target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent ? parent : null;
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * s.weapon.damageFactor(s.attack) * bonus(s);
        damage += EnchantmentHelper.getDamageBonus(weapon, living != null ? living.getMobType() : MobType.UNDEFINED);
        if (living != null && s.weapon.armorPiercing() > 0) {
            damage += damage * s.weapon.armorPiercing() * Math.min(1f, living.getArmorValue() / 20f);
        }
        damage *= CombatConfig.damageMultiplier();

        int fireAspect = EnchantmentHelper.getFireAspect(player);
        boolean litTarget = living != null && fireAspect > 0 && !living.isOnFire();
        if (litTarget) {
            living.setSecondsOnFire(1);
        }
        float healthBefore = living != null ? living.getHealth() : 0;
        target.invulnerableTime = 0;
        boolean hurt;
        applyingHit = true;
        try {
            hurt = target.hurt(player.damageSources().playerAttack(player), damage);
        } finally {
            applyingHit = false;
        }
        if (!hurt) {
            if (litTarget) {
                living.clearFire();
            }
            return;
        }

        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        float weight = force(s);
        // Heavy weapons push harder than the usual knockback of a hit, overheads the most, heavy attacks more.
        float push = EnchantmentHelper.getKnockbackBonus(player) * 0.5f + weight * switch (s.attack) {
            case OVERHEAD -> 0.35f;
            case SLASH -> 0.2f;
            default -> 0.08f;
        } * (s.has(CombatState.HEAVY) ? CombatRules.HEAVY_KNOCKBACK : 1);
        if (living != null && s.combo == Combo.HAMSTRING) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CombatRules.HAMSTRING_SLOW, 1), player);
        }
        if (push > 0 && living != null) {
            living.knockback(push, Mth.sin(yaw), -Mth.cos(yaw));
        }
        if (fireAspect > 0) {
            target.setSecondsOnFire(fireAspect * 4);
        }
        if (living != null) {
            EnchantmentHelper.doPostHurtEffects(living, player);
        }
        EnchantmentHelper.doPostDamageEffects(player, target);
        player.setLastHurtMob(target);

        ServerLevel level = player.serverLevel();
        if (living != null) {
            float dealt = healthBefore - living.getHealth();
            player.awardStat(Stats.DAMAGE_DEALT, Math.round(dealt * 10));
            int hearts = (int) (dealt * 0.5);
            if (hearts > 0) {
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.5), target.getZ(), hearts, 0.1, 0, 0.1, 0.2);
            }
        }
        if (!s.hitSomething && !player.getAbilities().instabuild) {
            weapon.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        if (!s.hitSomething && s.attack == AttackType.SLASH) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        player.causeFoodExhaustion(0.1f);
        s.hitSomething = true;
        boolean killed = living != null && living.isDeadOrDying();
        boolean heavy = killed || weight >= 0.6f;
        level.playSound(null, point.x, point.y, point.z, heavy ? ModSounds.HIT_HEAVY.get() : ModSounds.HIT.get(), SoundSource.PLAYERS, 1.0f,
                0.95f + player.getRandom().nextFloat() * 0.15f);
        hitSounds(level, living, point, killed, player.getRandom().nextFloat());
        if (killed) {
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 14, 0.15, 0.15, 0.15, 0.45);
        }
        feedback(player, Feedback.HIT);
        impact(player, killed ? Impact.KILL : Impact.HIT, player, target, point, weight);
    }

    private static void kick(ServerPlayer player, long now) {
        Vec3 pos = player.position();
        Vec3 look = player.getViewVector(1);
        double lookLength = Math.max(1.0E-4, Math.sqrt(look.x * look.x + look.z * look.z));
        double minCos = Math.cos(Math.toRadians(CombatRules.KICK_HALF_ANGLE));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(CombatRules.KICK_RANGE + 1), e -> e != player && canTarget(player, e))) {
            double dx = entity.getX() - pos.x;
            double dz = entity.getZ() - pos.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double distance = horizontal - entity.getBbWidth() / 2;
            if (distance > CombatRules.KICK_RANGE || entity.getY() > pos.y + 1.2 || entity.getY() + entity.getBbHeight() < pos.y + 0.2) {
                continue;
            }
            if (horizontal > 0.4 && (look.x * dx + look.z * dz) / (lookLength * horizontal) < minCos) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        yell(player, state(player), 0.5f);
        if (best == null) {
            playSound(player, ModSounds.FEINT.get(), 0.5f, 0.8f);
            return;
        }
        // The one who was kicked is off balance: an attack right now comes out fast.
        state(player).kickFollowUntil = now + CombatRules.KICK_FOLLOW_WINDOW;
        if (best instanceof ServerPlayer defender) {
            CombatState d = state(defender);
            if (d.guarding() || defender.isBlocking()) {
                d.spend(CombatRules.KICK_DRAIN, now);
                if (defender.isBlocking()) {
                    defender.getCooldowns().addCooldown(defender.getUseItem().getItem(), 60);
                    defender.stopUsingItem();
                    defender.level().broadcastEntityEvent(defender, (byte) 30);
                }
                stagger(d, CombatRules.KICK_STAGGER, 0);
                feedback(defender, Feedback.KICKED);
                feedback(player, Feedback.KICK_BREAK);
            }
        }
        best.invulnerableTime = 0;
        applyingHit = true;
        try {
            best.hurt(player.damageSources().playerAttack(player), WeaponStats.KICK.damage());
        } finally {
            applyingHit = false;
        }
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        best.knockback(0.6, Mth.sin(yaw), -Mth.cos(yaw));
        best.hurtMarked = true;
        player.level().playSound(null, best.getX(), best.getY(0.5), best.getZ(), ModSounds.KICK.get(), SoundSource.PLAYERS, 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        player.serverLevel().sendParticles(ParticleTypes.POOF, best.getX(), best.getY(0.4), best.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
        impact(player, Impact.KICK, player, best, best.position().add(0, best.getBbHeight() * 0.45, 0), 0.6f);
    }

    private static void battleCry(ServerPlayer player, CombatState s, float max) {
        s.restore(CombatRules.CRY_STAMINA, max);
        playSound(player, ModSounds.BATTLE_CRY.get(), 1.4f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY() + 0.2, player.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
        feedback(player, Feedback.BATTLE_CRY);
        impact(player, Impact.CRY, player, null, player.getEyePosition(), 0);
    }

    // ---------------------------------------------------------------- thrown weapons

    /** The throw leaves the hand: the weapon flies where the fighter looks, and the hand is empty (except in creative). */
    private static void throwWeapon(ServerPlayer player, CombatState s) {
        ItemStack stack = player.getMainHandItem();
        WeaponStats weapon = Weapons.of(stack);
        s.flags = CombatState.THROWN;
        s.set(Phase.RECOVERY, CombatRules.THROW_RECOVERY);
        if (weapon == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ThrownWeapon.damageFactor(weapon)
                * CombatConfig.damageMultiplier();
        boolean creative = player.getAbilities().instabuild;
        ItemStack thrownStack = creative ? stack.copyWithCount(1) : stack.split(1);
        ThrownWeapon thrown = new ThrownWeapon(level, player, thrownStack, weapon, damage);
        // From the hand, aimed so that it crosses the middle of the view a little way ahead.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        Vec3 right = look.cross(player.getUpVector(1)).normalize();
        Vec3 hand = eye.add(right.scale(0.28)).add(0, -0.12, 0);
        Vec3 aim = eye.add(look.scale(16)).subtract(hand).normalize();
        thrown.setPos(hand.x, hand.y, hand.z);
        thrown.shoot(aim.x, aim.y + 0.04, aim.z, ThrownWeapon.speed(weapon), 0.4f);
        level.addFreshEntity(thrown);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.awardStat(Stats.ITEM_USED.get(thrownStack.getItem()));
        playSound(player, ModSounds.THROW.get(), 1.0f, 1.1f - 0.3f * weapon.weight() + player.getRandom().nextFloat() * 0.1f);
        yell(player, s, 0.6f);
        s.weapon = null;
        s.weaponItem = null;
    }

    /**
     * Damages {@code target} with a thrown weapon. Unlike the blows of the combat rules, a raised weapon or shield
     * blocks it like any projectile; a knight it hits cries out.
     */
    public static boolean thrownHit(Entity target, DamageSource source, float amount) {
        weaponHurt = true;
        try {
            return target.hurt(source, amount);
        } finally {
            weaponHurt = false;
        }
    }

    /** Armour rings under a blow, and a creature killed by it falls. */
    public static void hitSounds(ServerLevel level, LivingEntity living, Vec3 point, boolean killed, float random) {
        if (living == null) {
            return;
        }
        int armor = living.getArmorValue();
        if (armor >= 4) {
            level.playSound(null, point.x, point.y, point.z, ModSounds.HIT_ARMOR.get(), SoundSource.PLAYERS,
                    Math.min(1, 0.35f + armor / 20f), 0.9f + random * 0.2f);
        }
        if (killed && living.getBbHeight() >= 1.2f) {
            level.playSound(null, living.getX(), living.getY(), living.getZ(), ModSounds.BODY_FALL.get(), SoundSource.PLAYERS,
                    0.9f, 0.9f + random * 0.15f);
        }
    }

    // ---------------------------------------------------------------- vanilla damage and attacks

    /**
     * Attacks on creatures with weapons only go through the combat rules: the vanilla left click on them does nothing.
     * Boats, minecarts, item frames and the like are still broken with a normal click.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        boolean creature = target instanceof LivingEntity || target instanceof PartEntity<?> || target instanceof EndCrystal;
        if (creature && Weapons.isWeapon(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    /** A raised weapon also blocks mobs, arrows and players fighting the vanilla way. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (applyingHit || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CombatState s = state(player);
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (!s.guarding() || direct == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        boolean projectile = direct instanceof Projectile;
        if (!projectile && !(direct instanceof LivingEntity) || !facing(player, direct.position().add(0, direct.getBbHeight() / 2, 0))) {
            return;
        }
        long now = player.level().getGameTime();
        boolean parry = s.phase == Phase.BLOCK && now <= s.parryUntil;
        float blockCost = s.weapon != null ? s.weapon.blockCost() : 1;
        s.spend(event.getAmount() * (projectile ? CombatRules.PROJECTILE_BLOCK_COST : CombatRules.MOB_BLOCK_COST)
                * blockCost * (parry ? CombatRules.PARRY_FACTOR : 1), now);
        s.blockLanded = true;
        s.syncState = true;
        Vec3 point = direct.position().add(0, direct.getBbHeight() / 2, 0).lerp(player.getEyePosition(), 0.6);
        if (s.stamina <= 0) {
            // Out of stamina: the guard breaks and the damage goes through.
            stagger(s, CombatRules.GUARD_BREAK, CombatState.GUARD_BROKEN);
            clash(player, point, ModSounds.GUARD_BREAK.get(), 1.0f, 16);
            feedback(player, Feedback.GUARD_BROKEN);
            impact(player, Impact.GUARD_BREAK, direct, player, point, 0.5f);
            return;
        }
        event.setCanceled(true);
        if (parry) {
            s.riposteUntil = now + CombatRules.RIPOSTE_WINDOW;
            if (direct instanceof LivingEntity mob) {
                mob.knockback(0.8, player.getX() - mob.getX(), player.getZ() - mob.getZ());
            }
            clash(player, point, ModSounds.PARRY.get(), 1.0f, 12);
            glint(player, point);
            feedback(player, Feedback.PARRIED);
            impact(player, Impact.PARRY, direct, player, point, 0.4f);
        } else {
            clash(player, point, ModSounds.BLOCK.get(), 0.9f, 6);
            feedback(player, Feedback.BLOCKED);
            impact(player, Impact.BLOCK, direct, player, point, 0.4f);
        }
    }

    /** A knight struck by a weapon or a kick cries out in pain instead of Minecraft's usual grunt. */
    @SubscribeEvent
    public static void onPlaySound(PlayLevelSoundEvent.AtPosition event) {
        if ((applyingHit || weaponHurt) && CombatConfig.attackYells() && event.getSound() != null && event.getSound().value() == SoundEvents.PLAYER_HURT) {
            ModSounds.PAIN.getHolder().ifPresent(event::setSound);
        }
    }

    /** Any hit interrupts a wind-up (except a riposte), a kick or a battle cry. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0 || event.getSource().getEntity() == null) {
            return;
        }
        CombatState s = state(player);
        if (s.phase == Phase.WINDUP && !s.has(CombatState.RIPOSTE) || s.phase == Phase.KICK || s.phase == Phase.CRY || s.phase == Phase.THROW) {
            stagger(s, CombatRules.FLINCH, CombatState.FLINCHED);
            feedback(player, Feedback.FLINCHED);
        }
        if (!applyingHit) {
            // Blows of mobs and of players fighting the vanilla way shake the one who is hit too.
            Entity source = event.getSource().getEntity();
            impact(player, Impact.HIT, source, player, player.position().add(0, player.getBbHeight() * 0.6, 0),
                    Math.min(1, event.getAmount() / 10f));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            STATES.remove(player.getUUID());
            removeSlowness(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Fighters of a closed world must not come back in the next one (single player keeps the same game running). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    /** A player who starts seeing a fighter gets the fighter's current state. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            CombatNetwork.sendToPlayer(viewer, statePacket(target, state(target)));
        }
    }

    // ---------------------------------------------------------------- effects and syncing

    private static void updateSlowness(ServerPlayer player, CombatState s) {
        boolean slow = s.phase == Phase.BLOCK || s.phase == Phase.STAGGER && s.has(CombatState.GUARD_BROKEN);
        if (slow == s.slowed) {
            return;
        }
        s.slowed = slow;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(SLOW_ID);
        if (slow) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, "Knightly combat guard", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlowness(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
        }
    }

    private static void sync(ServerPlayer player, CombatState s) {
        if (s.syncState) {
            s.syncState = false;
            CombatNetwork.sendToTrackingAndSelf(player, statePacket(player, s));
        }
        int stamina = Math.round(s.stamina() * 4);
        if (stamina != s.sentStamina) {
            s.sentStamina = stamina;
            CombatNetwork.sendToPlayer(player, new StaminaPacket(s.stamina(), CombatConfig.maxStamina()));
        }
    }

    private static CombatStatePacket statePacket(ServerPlayer player, CombatState s) {
        return new CombatStatePacket(player.getId(), s.phase.ordinal(), s.attack.ordinal(), s.flags, s.combo.ordinal(), s.phaseLength, s.phaseTicks);
    }

    public static void feedback(ServerPlayer player, Feedback feedback) {
        CombatNetwork.sendToPlayer(player, new FeedbackPacket(feedback));
    }

    private static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Tells everyone around that something struck something, for the shaking cameras and the flinching fighters. */
    public static void impact(Entity around, Impact impact, Entity attacker, Entity victim, Vec3 point, float weight) {
        CombatNetwork.sendAround(around, new ImpactPacket(impact, attacker == null ? -1 : attacker.getId(),
                victim == null ? -1 : victim.getId(), (float) point.x, (float) point.y, (float) point.z, weight));
    }

    /** Pushes {@code entity} away from {@code from}, also when it is a player (whose movement the client controls). */
    private static void push(LivingEntity entity, Entity from, float strength) {
        if (strength <= 0) {
            return;
        }
        double dx = from.getX() - entity.getX();
        double dz = from.getZ() - entity.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        entity.knockback(strength, dx, dz);
        entity.hurtMarked = true;
    }

    /** A bright glint where blades meet on a parry or a broken guard. */
    private static void glint(ServerPlayer player, Vec3 point) {
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 5, 0.04, 0.04, 0.04, 0.06);
    }

    /** Sound and sparks where two weapons meet. */
    private static void clash(ServerPlayer player, Vec3 point, SoundEvent sound, float pitch, int sparks) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, point.x, point.y, point.z, sound, SoundSource.PLAYERS, 1.0f, pitch + player.getRandom().nextFloat() * 0.1f);
        ParticleOptions spark = ParticleTypes.ELECTRIC_SPARK;
        level.sendParticles(spark, point.x, point.y, point.z, sparks, 0.08, 0.08, 0.08, 0.4);
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, sparks / 2, 0.05, 0.05, 0.05, 0.3);
    }
}
