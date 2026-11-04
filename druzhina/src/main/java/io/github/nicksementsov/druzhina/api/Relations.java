package io.github.nicksementsov.druzhina.api;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Who stands with whom. By itself Druzhina knows only that men of one commander are one side; a mod that has realms,
 * alliances and wars (Prestol) tells it more: which commanders are allies, which are at war, who is an outlaw. Soldiers
 * of commanders at war fall upon each other and upon each other's commanders on sight, and upon whoever else fights
 * for an enemy side (a colony's guards, say: see {@link #setSideOf}).
 */
public final class Relations {
    public enum Stance {
        ALLY,
        NEUTRAL,
        ENEMY
    }

    private static volatile BiFunction<UUID, UUID, Stance> provider;
    private static volatile Predicate<LivingEntity> routed;
    private static volatile Function<Entity, UUID> sideOf;

    private Relations() {
    }

    /** Sets who decides the stance between two commanders (players), or null for one side per commander. */
    public static void setProvider(@Nullable BiFunction<UUID, UUID, Stance> p) {
        provider = p;
    }

    /** Sets who says that a soldier's spirit is broken: he runs instead of fighting. */
    public static void setRouted(@Nullable Predicate<LivingEntity> p) {
        routed = p;
    }

    /**
     * Sets who tells which commander a creature that is neither a soldier nor a player fights for (the lord of a
     * colony whose guard it is, for instance), or null when it fights for nobody. Soldiers fall upon such fighters of
     * a side at war with their own, and spare those of an allied side.
     */
    public static void setSideOf(@Nullable Function<Entity, UUID> f) {
        sideOf = f;
    }

    /** The commander {@code e} fights for: a soldier's commander, a player himself, or whom the provider names. */
    @Nullable
    public static UUID sideOf(@Nullable Entity e) {
        if (e == null) {
            return null;
        }
        if (e instanceof io.github.nicksementsov.druzhina.entity.Soldier s) {
            return s.isBandit() ? null : s.getOwnerUUID();
        }
        if (e instanceof Player p) {
            return p.getUUID();
        }
        Function<Entity, UUID> f = sideOf;
        return f == null ? null : f.apply(e);
    }

    /**
     * Whether the men of commander {@code lord} take {@code e} for an enemy: a bandit, or anyone (soldier, player,
     * guard) who fights for a side at war with him. Monsters are not counted here.
     */
    public static boolean hostile(@Nullable UUID lord, Entity e) {
        if (e instanceof io.github.nicksementsov.druzhina.entity.Soldier s && s.isBandit()) {
            return true;
        }
        UUID side = sideOf(e);
        return lord != null && side != null && between(lord, side) == Stance.ENEMY;
    }

    /** The stance between the men of commander {@code a} and those of commander {@code b} (null: nobody's men). */
    public static Stance between(@Nullable UUID a, @Nullable UUID b) {
        if (Objects.equals(a, b)) {
            return Stance.ALLY;
        }
        BiFunction<UUID, UUID, Stance> p = provider;
        if (p == null || a == null || b == null) {
            return Stance.NEUTRAL;
        }
        Stance s = p.apply(a, b);
        return s == null ? Stance.NEUTRAL : s;
    }

    public static boolean routed(LivingEntity soldier) {
        Predicate<LivingEntity> p = routed;
        return p != null && p.test(soldier);
    }
}
