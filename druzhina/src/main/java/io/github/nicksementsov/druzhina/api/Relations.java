package io.github.nicksementsov.druzhina.api;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;

/**
 * Who stands with whom. By itself Druzhina knows only that men of one commander are one side; a mod that has realms,
 * alliances and wars (Prestol) tells it more: which commanders are allies, which are at war, who is an outlaw. Soldiers
 * of commanders at war fall upon each other and upon each other's commanders on sight.
 */
public final class Relations {
    public enum Stance {
        ALLY,
        NEUTRAL,
        ENEMY
    }

    private static volatile BiFunction<UUID, UUID, Stance> provider;
    private static volatile Predicate<LivingEntity> routed;

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
