package io.github.olvrbu.transmutationchest.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuyGroupTest {

    @Test
    void vanillaCategories() {
        assertEquals(BuyGroup.ROCKS, BuyGroup.of(new String[]{"Blocks.Rocks"}));
        assertEquals(BuyGroup.WOOD, BuyGroup.of(new String[]{"Blocks.Wood"}));
        assertEquals(BuyGroup.SOIL, BuyGroup.of(new String[]{"Blocks.Soils"}));
        assertEquals(BuyGroup.SOIL, BuyGroup.of(new String[]{"Blocks.Soil"}));
    }

    @Test
    void doorsShelvesSignsBedsAndDecoAreFurniture() {
        for (String category : new String[]{"Furniture.Doors", "Furniture.Furniture", "Furniture.Shelves",
                "Furniture.Signs", "Furniture.Beds", "Blocks.Deco"}) {
            assertEquals(BuyGroup.FURNITURE, BuyGroup.of(new String[]{category}), category);
        }
    }

    @Test
    void unknownOrMissingIsOther() {
        assertEquals(BuyGroup.OTHER, BuyGroup.of(null));
        assertEquals(BuyGroup.OTHER, BuyGroup.of(new String[0]));
        assertEquals(BuyGroup.OTHER, BuyGroup.of(new String[]{"Blocks.Metal"}));
        assertEquals(BuyGroup.OTHER, BuyGroup.of(new String[]{null}));
    }

    @Test
    void firstRecognisedCategoryWins() {
        assertEquals(BuyGroup.WOOD, BuyGroup.of(new String[]{"Blocks.Metal", "Blocks.Wood", "Blocks.Rocks"}));
    }

    @Test
    void idsRoundTrip() {
        for (BuyGroup group : BuyGroup.values()) {
            assertEquals(group, BuyGroup.byId(group.id));
        }
        assertNull(BuyGroup.byId("Nope"));
    }
}
