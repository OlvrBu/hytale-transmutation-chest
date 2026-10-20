package io.github.obut.tradestation.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemFilterTest {

    @Test
    void emptyFilterMatchesEverything() {
        assertTrue(ItemFilter.NONE.matches("Rock_Stone", "Stone", 0));
        assertTrue(ItemFilter.of("   ", false).matches("Rock_Stone", "Stone", 0));
        assertTrue(ItemFilter.of(null, false).matches("Rock_Stone", "Stone", 0));
    }

    @Test
    void matchesDisplayNameOrIdCaseInsensitively() {
        assertTrue(ItemFilter.of("COBBLE", false).matches("Rock_Stone_Cobble", "Cobblestone", 0));
        assertTrue(ItemFilter.of("rock", false).matches("Rock_Stone_Cobble", "Cobblestone", 0));
        assertFalse(ItemFilter.of("dirt", false).matches("Rock_Stone_Cobble", "Cobblestone", 0));
    }

    @Test
    void underscoresInIdsCountAsSpaces() {
        assertTrue(ItemFilter.of("stone cobble", false).matches("Rock_Stone_Cobble", null, 0));
        assertTrue(ItemFilter.of("stone_cobble", false).matches("Rock_Stone_Cobble", null, 0));
    }

    @Test
    void everyWordMustMatchSomewhere() {
        ItemFilter filter = ItemFilter.of("sand brick", false);
        assertTrue(filter.matches("Rock_Sandstone_Brick", "Sandstone Brick", 0));
        assertFalse(filter.matches("Rock_Sandstone", "Sandstone", 0));
        // Words may come from different places: one from the ID, one from the name.
        assertTrue(ItemFilter.of("rock planks", false).matches("Rock_Thing", "Fancy Planks", 0));
    }

    @Test
    void ownedOnlyHidesItemsYouDontHave() {
        ItemFilter filter = ItemFilter.of("", true);
        assertFalse(filter.matches("Soil_Dirt", "Dirt", 0));
        assertTrue(filter.matches("Soil_Dirt", "Dirt", 3));
        assertFalse(ItemFilter.of("sand", true).matches("Soil_Dirt", "Dirt", 3));
    }

    @Test
    void updatesKeepTheOtherSetting() {
        ItemFilter filter = ItemFilter.of("dirt", false).withOwnedOnly(true);
        assertFalse(filter.matches("Soil_Dirt", "Dirt", 0));
        assertTrue(filter.withQuery("").matches("Rock_Stone", "Stone", 1));
        assertFalse(filter.withQuery("").matches("Rock_Stone", "Stone", 0));
    }
}
