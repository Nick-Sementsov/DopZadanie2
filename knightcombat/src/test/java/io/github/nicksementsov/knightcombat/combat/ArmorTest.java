package io.github.nicksementsov.knightcombat.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorTest {
    @Test
    void mailAndPlateAreMetal() {
        assertTrue(Armor.isMetal("iron_chestplate", "iron"));
        assertTrue(Armor.isMetal("chainmail_helmet", "chainmail"));
        assertTrue(Armor.isMetal("kettlehat", "kettlehat"));
        assertTrue(Armor.isMetal("eastern_kettlehat", "eastern"));
        assertTrue(Armor.isMetal("coat_of_plates_chestplate", "coat_of_plates"));
        assertTrue(Armor.isMetal("cuman_captain_helmet", "cuman"));
        assertTrue(Armor.isMetal("sutton_hoo_helmet", "sutton_hoo"));
    }

    @Test
    void mailSewnOnClothIsStillMetal() {
        assertTrue(Armor.isMetal("chained_gambeson", "gambeson"));
        assertTrue(Armor.isMetal("rus_chainmail_coif", "rus_chainmail"));
        assertTrue(Armor.isMetal("gilded_chainmail_coif", "gilded_chainmail"));
    }

    @Test
    void leatherAndClothAreNot() {
        assertFalse(Armor.isMetal("leather_chestplate", "leather"));
        assertFalse(Armor.isMetal("gambeson_chestplate", "gambeson"));
        assertFalse(Armor.isMetal("rus_gambeson_boots", "rus_gambeson"));
        assertFalse(Armor.isMetal("kaftan", "kaftan"));
        assertFalse(Armor.isMetal("celtic_tunic", "celtic"));
        assertFalse(Armor.isMetal("fur_cloak", "fur"));
        assertFalse(Armor.isMetal("linen_coif", "linen"));
        assertFalse(Armor.isMetal("straw_hat", "straw"));
        assertFalse(Armor.isMetal("eastern_hat", "eastern"));
        assertFalse(Armor.isMetal("turtle_helmet", "turtle"));
        assertFalse(Armor.isMetal("pillbox_cap", "pillbox"));
    }
}
