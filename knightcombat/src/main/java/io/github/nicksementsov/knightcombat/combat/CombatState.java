package io.github.nicksementsov.knightcombat.combat;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.item.Item;

/** Combat state of one player on the server. {@link CombatManager} changes it, clients get a copy of the visible part. */
public final class CombatState {
    /** The slash goes from left to right (the second slash of a combo). */
    public static final int MIRRORED = 1;
    /** The wind-up is a riposte or a counter: it is faster and guarded until the release. */
    public static final int RIPOSTE = 2;
    /** The wind-up continues a combo. */
    public static final int COMBO = 4;
    /** Recovery after the attack was blocked or hit a wall: no attacks, but blocking is allowed. */
    public static final int REBOUND = 8;
    /** Stagger after the guard broke. */
    public static final int GUARD_BROKEN = 16;
    /** Recovery after a feint: attacking and blocking are allowed right away. */
    public static final int FEINT = 32;
    /** Short stagger after being hit during a wind-up: blocking is allowed. */
    public static final int FLINCHED = 64;

    Phase phase = Phase.IDLE;
    AttackType attack = AttackType.SLASH;
    int flags;
    int phaseTicks;
    int phaseLength;
    /** Attack pressed during a release or late recovery, started as soon as possible. */
    AttackType queued;
    /** Weapon of the current attack or block. */
    WeaponStats weapon;
    Item weaponItem;

    boolean blockHeld;
    long blockPressedAt = Long.MIN_VALUE / 2;
    long parryUntil = Long.MIN_VALUE / 2;
    long riposteUntil = Long.MIN_VALUE / 2;
    long blockReadyAt = Long.MIN_VALUE / 2;
    long cryReadyAt = Long.MIN_VALUE / 2;
    long lastStaminaUse = Long.MIN_VALUE / 2;
    /** Whether the current block caught anything; an empty block is followed by a short pause. */
    boolean blockLanded;

    /** Negative until the first tick sets it to the maximum. */
    float stamina = -1;
    final IntOpenHashSet hitEntities = new IntOpenHashSet();
    boolean hitSomething;
    boolean lastSlashMirrored;

    boolean syncState;
    int sentStamina = Integer.MIN_VALUE;
    boolean slowed;

    public Phase phase() {
        return phase;
    }

    public AttackType attack() {
        return attack;
    }

    public int flags() {
        return flags;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int phaseLength() {
        return phaseLength;
    }

    public float stamina() {
        return Math.max(stamina, 0);
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Blocking, or winding up a riposte (ripostes are guarded until they are released). */
    public boolean guarding() {
        return phase == Phase.BLOCK || phase == Phase.WINDUP && has(RIPOSTE);
    }

    void set(Phase newPhase, int length) {
        phase = newPhase;
        phaseTicks = 0;
        phaseLength = length;
        syncState = true;
    }

    void spend(float amount, long now) {
        stamina = Math.max(0, stamina - amount);
        lastStaminaUse = now;
    }

    void restore(float amount, float max) {
        stamina = Math.min(max, stamina + amount);
    }
}
