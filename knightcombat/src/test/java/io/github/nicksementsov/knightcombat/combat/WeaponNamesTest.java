package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponNamesTest {
    @Test
    void weaponsOfDarkAgesAreKnownByTheirNames() {
        assertEquals(WeaponKind.SPEAR, Weapons.knownName("steel_javelin"));
        assertEquals(WeaponKind.SPEAR, Weapons.knownName("steel_harnham_spear"));
        assertEquals(WeaponKind.AXE, Weapons.knownName("steel_crayke_axe"));
        assertEquals(WeaponKind.AXE, Weapons.knownName("steel_small_broadaxe"));
        // The small seaxes are knives; the long seax and the scramasax are fought with like swords.
        assertEquals(WeaponKind.DAGGER, Weapons.knownName("steel_small_seax"));
        assertEquals(WeaponKind.DAGGER, Weapons.knownName("steel_sittingbourne_seax"));
        assertNull(Weapons.knownName("steel_langseax"));
        assertNull(Weapons.knownName("steel_scramasax"));
        assertNull(Weapons.knownName("steel_magyar_sabre"));
    }

    @Test
    void weaponsOfTheOtherAddonsAreKnownByTheirNames() {
        assertEquals(WeaponKind.SPEAR, Weapons.knownName("iron_sarissa"));
        assertEquals(WeaponKind.SPEAR, Weapons.knownName("iron_doru"));
        assertEquals(WeaponKind.DAGGER, Weapons.knownName("iron_sica"));
        assertEquals(WeaponKind.SPEAR, Weapons.knownName("steel_rogatina"));
        assertEquals(WeaponKind.POLEAXE, Weapons.knownName("steel_bardiche"));
        assertEquals(WeaponKind.DAGGER, Weapons.knownName("steel_short_seax"));
        assertNull(Weapons.knownName("steel_long_seax"));
        assertEquals(WeaponKind.AXE, Weapons.knownName("steel_francisca_axe"));
    }

    @Test
    void toolsAreKnownByTheirNames() {
        // A pickaxe has an "axe" in its name, but it is a tool.
        assertTrue(Weapons.toolName("iron_pickaxe"));
        assertTrue(Weapons.toolName("netherite_pickaxe"));
        assertTrue(Weapons.toolName("diamond_shovel"));
        assertTrue(Weapons.toolName("golden_hoe"));
        assertTrue(Weapons.toolName("steel_paxel"));
        assertFalse(Weapons.toolName("steel_war_pick"));
        assertFalse(Weapons.toolName("steel_crayke_axe"));
        assertFalse(Weapons.toolName("iron_sword"));
    }

    @Test
    void battleAxesAreToldFromWoodAxes() {
        assertTrue(Weapons.battleAxeName("iron_battle_axe"));
        assertTrue(Weapons.battleAxeName("steel_bearded_axe"));
        assertTrue(Weapons.battleAxeName("steel_small_broadaxe"));
        assertTrue(Weapons.battleAxeName("iron_greataxe"));
        assertFalse(Weapons.battleAxeName("iron_axe"));
        assertFalse(Weapons.battleAxeName("diamond_axe"));
        assertFalse(Weapons.battleAxeName("copper_axe"));
    }
}
