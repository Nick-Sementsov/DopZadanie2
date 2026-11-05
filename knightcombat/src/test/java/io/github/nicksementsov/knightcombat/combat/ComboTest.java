package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static io.github.nicksementsov.knightcombat.combat.AttackType.OVERHEAD;
import static io.github.nicksementsov.knightcombat.combat.AttackType.SLASH;
import static io.github.nicksementsov.knightcombat.combat.AttackType.STAB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboTest {
    private static Combo match(AttackType... chain) {
        return Combo.match(chain, chain.length);
    }

    @Test
    void finishersEndChainsInTheirOrder() {
        assertEquals(Combo.WHIRLWIND, match(SLASH, SLASH, SLASH));
        assertEquals(Combo.MILL, match(SLASH, SLASH, OVERHEAD));
        assertEquals(Combo.HAMMERFALL, match(OVERHEAD, OVERHEAD));
        assertEquals(Combo.STING, match(STAB, STAB, STAB));
        assertEquals(Combo.HAMSTRING, match(STAB, SLASH));
        assertEquals(Combo.HAMSTRING, match(STAB, STAB, SLASH));
        assertEquals(Combo.HAMMERFALL, match(SLASH, OVERHEAD, OVERHEAD));
    }

    @Test
    void otherChainsAreNoFinishers() {
        assertEquals(Combo.NONE, match(SLASH));
        assertEquals(Combo.NONE, match(SLASH, SLASH));
        assertEquals(Combo.NONE, match(SLASH, OVERHEAD));
        assertEquals(Combo.NONE, match(OVERHEAD, SLASH, STAB));
        assertEquals(Combo.NONE, match(STAB, STAB));
    }

    @Test
    void aChainStartsOverAfterAFinisher() {
        CombatState s = new CombatState();
        s.chain(SLASH, false);
        s.chain(SLASH, true);
        s.chain(SLASH, true);
        s.combo = Combo.match(s.chain, s.chainLength);
        assertEquals(Combo.WHIRLWIND, s.combo);
        s.chain(SLASH, true);
        assertEquals(1, s.chainLength);
        s.combo = Combo.match(s.chain, s.chainLength);
        assertEquals(Combo.NONE, s.combo);
    }

    @Test
    void anAttackThatDoesNotContinueTheChainStartsANewOne() {
        CombatState s = new CombatState();
        s.chain(OVERHEAD, false);
        s.chain(OVERHEAD, false);
        assertEquals(Combo.NONE, Combo.match(s.chain, s.chainLength));
    }

    @Test
    void longChainsKeepTheLastAttacks() {
        CombatState s = new CombatState();
        s.chain(STAB, false);
        s.chain(OVERHEAD, true);
        s.chain(SLASH, true);
        s.chain(SLASH, true);
        s.chain(SLASH, true);
        assertEquals(3, s.chainLength);
        assertEquals(Combo.WHIRLWIND, Combo.match(s.chain, s.chainLength));
    }

    @Test
    void whirlwindsSweepWiderAndHamstringsGoLow() {
        assertEquals(SwingGeometry.WIDE_ARC, SwingGeometry.yaw(SLASH, false, 0, Combo.WHIRLWIND), 1e-6);
        assertTrue(SwingGeometry.samples(SLASH, 0, 1, Combo.WHIRLWIND) > SwingGeometry.samples(SLASH, 0, 1));
        assertTrue(SwingGeometry.pitch(SLASH, 0, Combo.HAMSTRING) > SwingGeometry.pitch(SLASH, 0), "hamstrings start lower");
        assertTrue(SwingGeometry.pitch(SLASH, 1, Combo.HAMSTRING) > SwingGeometry.pitch(SLASH, 1), "and end lower");
    }
}
