package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
