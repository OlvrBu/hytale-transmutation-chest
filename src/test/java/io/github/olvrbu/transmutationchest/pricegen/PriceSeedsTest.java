package io.github.olvrbu.transmutationchest.pricegen;

import io.github.olvrbu.transmutationchest.config.TradeConfigException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceSeedsTest {

    @Test
    void parsesAndFilters() throws TradeConfigException {
        var seeds = PriceSeeds.parse("""
                { "spread": 0.25, "excludeCategories": ["Furniture.Benches", "Tool."],
                  "excludeItems": ["Transmutation_Chest", "Bench_*"], "seeds": { "Stone": 10, "Log": 20 } }
                """);
        assertEquals(List.of("Stone", "Log"), List.copyOf(seeds.seeds().keySet()));
        assertTrue(seeds.includes(new AssetConversions.ItemInfo("Brick", true, List.of("Blocks.Rocks"))));
        assertTrue(seeds.includes(new AssetConversions.ItemInfo("Variant", true, List.of())));
        assertFalse(seeds.includes(new AssetConversions.ItemInfo("Stick", false, List.of())));
        assertFalse(seeds.includes(new AssetConversions.ItemInfo("Transmutation_Chest", true, List.of())));
        assertFalse(seeds.includes(new AssetConversions.ItemInfo("Bench", true, List.of("Furniture.Benches"))));
        assertFalse(seeds.includes(new AssetConversions.ItemInfo("Bench_Lumbermill", true, List.of("Furniture.Furniture"))));
        assertTrue(seeds.includes(new AssetConversions.ItemInfo("Benchmark_Block", true, List.of())));
        assertFalse(seeds.includes(new AssetConversions.ItemInfo("Brush", true, List.of("Tool.BrushFilters"))));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("{ \"seeds\": {} }"));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("{ \"spread\": 0, \"seeds\": { \"A\": 0 } }"));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("{ \"spread\": 0, \"seeds\": { \"A\": 1.5 } }"));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("{ \"spread\": 0, \"seeds\": { \"A\": 1, \"A\": 2 } }"));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("{ \"spread\": 0, \"seeds\": {}, \"oops\": 1 }"));
    }

    @Test
    void bundledSeedsAreValid() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/price_seeds.json")) {
            PriceSeeds seeds = PriceSeeds.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(seeds.seeds().isEmpty());
        }
    }

    @Test
    void parsesUnlockGroups() throws TradeConfigException {
        var seeds = PriceSeeds.parse("""
                { "spread": 0, "seeds": { "Stone": 10, "Red": 10, "Blue": 10 },
                  "unlocks": { "defaultAmount": 500, "amounts": { "Clay": 300 },
                               "groups": { "Clay": ["Red", "Blue"] } } }
                """);
        assertTrue(seeds.unlocksEnabled());
        assertEquals("Clay", seeds.unlockIdFor("Red"));
        assertEquals("Stone", seeds.unlockIdFor("Stone"));
        assertEquals(List.of("Red", "Blue"), seeds.acceptedBy("Clay"));
        assertEquals(List.of("Stone"), seeds.acceptedBy("Stone"));
        assertEquals(300, seeds.amountFor("Clay"));
        assertEquals(500, seeds.amountFor("Stone"));
    }

    @Test
    void rejectsBadUnlockGroups() {
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("""
                { "spread": 0, "seeds": { "A": 1 }, "unlocks": { "defaultAmount": 5, "groups": { "G": ["Missing"] } } }"""));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("""
                { "spread": 0, "seeds": { "A": 1 }, "unlocks": { "defaultAmount": 5, "groups": { "G": ["A"], "H": ["A"] } } }"""));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("""
                { "spread": 0, "seeds": { "A": 1 }, "unlocks": { "groups": { "G": ["A"] } } }"""));
        assertThrows(TradeConfigException.class, () -> PriceSeeds.parse("""
                { "spread": 0, "seeds": { "A": 1 }, "unlocks": { "defaultAmount": 0 } }"""));
    }
}
