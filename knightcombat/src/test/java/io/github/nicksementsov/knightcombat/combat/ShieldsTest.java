package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldsTest {
    @Test
    void sizesComeFromTheNamesOfTheShields() {
        assertEquals(Shields.Size.SMALL, Shields.size("iron_buckler"));
        assertEquals(Shields.Size.SMALL, Shields.size("steel_target"));
        assertEquals(Shields.Size.SMALL, Shields.size("iron_rondache"));
        assertEquals(Shields.Size.LARGE, Shields.size("iron_pavese"));
        assertEquals(Shields.Size.LARGE, Shields.size("bronze_scutum"));
        assertEquals(Shields.Size.MEDIUM, Shields.size("shield"));
        assertEquals(Shields.Size.MEDIUM, Shields.size("iron_heatershield"));
        assertEquals(Shields.Size.MEDIUM, Shields.size("iron_kiteshield"));
        assertEquals(Shields.Size.MEDIUM, Shields.size("iron_roundshield"));
    }

    @Test
    void shieldsOfTheAddonsOfEpicKnightsHaveTheirSizes() {
        // Antique Legacy: the hoplite's round shield and the oval thureos are large, the pelta is small.
        assertEquals(Shields.Size.LARGE, Shields.size("bronze_hoplon"));
        assertEquals(Shields.Size.LARGE, Shields.size("bronze_tureos"));
        assertEquals(Shields.Size.LARGE, Shields.size("bronze_imperial_scutum"));
        assertEquals(Shields.Size.SMALL, Shields.size("pelta"));
        // Epic Knights: Addon: the judicial dueling shield is as tall as a man.
        assertEquals(Shields.Size.LARGE, Shields.size("dueling_shield"));
    }

    @Test
    void largerShieldsCoverMoreAndCostLessButParryShorter() {
        Shields.Size[] sizes = Shields.Size.values();
        for (int i = 1; i < sizes.length; i++) {
            assertTrue(sizes[i].halfAngle > sizes[i - 1].halfAngle);
            assertTrue(sizes[i].blockCost < sizes[i - 1].blockCost);
            assertTrue(sizes[i].parryWindow < sizes[i - 1].parryWindow);
        }
    }
}
