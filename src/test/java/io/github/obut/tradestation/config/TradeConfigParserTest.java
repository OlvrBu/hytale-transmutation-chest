package io.github.obut.tradestation.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeConfigParserTest {

    private static final Set<String> KNOWN = Set.of("Rock_Stone", "Soil_Dirt", "Soil_Sand");
    private static final Predicate<String> ALL_KNOWN = id -> true;

    private static TradeConfigParser.Result parse(String json) throws TradeConfigException {
        return TradeConfigParser.parse(json, KNOWN::contains);
    }

    @Test
    void parsesValidConfig() throws TradeConfigException {
        var result = parse("""
                { "spread": 0.25, "items": { "Rock_Stone": 2, "Soil_Dirt": 1 } }
                """);
        assertEquals(List.of(), result.warnings());
        assertEquals(0, new BigDecimal("0.25").compareTo(result.config().spread()));
        assertEquals(Map.of("Rock_Stone", 2L, "Soil_Dirt", 1L), result.config().basePrices());
        assertTrue(result.config().isTradeable("Rock_Stone"));
        assertFalse(result.config().isTradeable("Soil_Sand"));
        assertEquals(2, result.config().basePrice("Rock_Stone").orElseThrow());
        assertTrue(result.config().basePrice("Soil_Sand").isEmpty());
    }

    @Test
    void keepsItemOrderFromFile() throws TradeConfigException {
        var result = TradeConfigParser.parse("""
                { "spread": 0, "items": { "C": 1, "A": 1, "B": 1, "E": 1, "D": 1 } }
                """, ALL_KNOWN);
        assertEquals(List.of("C", "A", "B", "E", "D"), List.copyOf(result.config().basePrices().keySet()));
    }

    @Test
    void acceptsZeroSpreadAndEmptyItems() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "items": {} }
                """);
        assertEquals(0, result.config().spread().signum());
        assertTrue(result.config().basePrices().isEmpty());
    }

    @Test
    void acceptsIntegralDecimalPrice() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "items": { "Rock_Stone": 2.0 } }
                """);
        assertEquals(2, result.config().basePrice("Rock_Stone").orElseThrow());
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0.25, "));
        assertThrows(TradeConfigException.class, () -> parse("not json"));
        assertThrows(TradeConfigException.class, () -> parse(""));
        // Strict mode: no comments, no trailing commas, no single quotes.
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0, \"items\": {}, }"));
        assertThrows(TradeConfigException.class, () -> parse("{ 'spread': 0, 'items': {} }"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0, \"items\": {} } // c"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0, \"items\": {} } {}"));
    }

    @Test
    void rejectsWrongTopLevelType() {
        assertThrows(TradeConfigException.class, () -> parse("[]"));
        assertThrows(TradeConfigException.class, () -> parse("42"));
    }

    @Test
    void rejectsMissingOrInvalidSpread() {
        assertThrows(TradeConfigException.class, () -> parse("{ \"items\": {} }"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": -0.1, \"items\": {} }"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": \"0.25\", \"items\": {} }"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0, \"spread\": 1, \"items\": {} }"));
    }

    @Test
    void rejectsMissingOrInvalidItems() {
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0 }"));
        assertThrows(TradeConfigException.class, () -> parse("{ \"spread\": 0, \"items\": [] }"));
    }

    @Test
    void skipsBadItemEntriesWithWarnings() throws TradeConfigException {
        var result = parse("""
                {
                  "spread": 0.25,
                  "items": {
                    "Rock_Stone": 2,
                    "Soil_Dirt": 0,
                    "Soil_Sand": -3,
                    "Not_An_Item": 5
                  }
                }
                """);
        assertEquals(Map.of("Rock_Stone", 2L), result.config().basePrices());
        assertEquals(3, result.warnings().size());
        assertTrue(result.warnings().get(2).contains("Not_An_Item"));
    }

    @Test
    void skipsNonIntegerAndNonNumericPrices() throws TradeConfigException {
        var result = TradeConfigParser.parse("""
                { "spread": 0, "items": { "A": 1.5, "B": "2", "C": null, "D": 99999999999999999999, "E": 3 } }
                """, ALL_KNOWN);
        assertEquals(Map.of("E", 3L), result.config().basePrices());
        assertEquals(4, result.warnings().size());
    }

    @Test
    void skipsAllEntriesOfDuplicatedItem() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "items": { "Rock_Stone": 2, "Soil_Dirt": 1, "Rock_Stone": 3 } }
                """);
        assertEquals(Map.of("Soil_Dirt", 1L), result.config().basePrices());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void skipsEntriesWhoseBuyPriceOverflows() throws TradeConfigException {
        var result = TradeConfigParser.parse("""
                { "spread": 1, "items": { "Big": 9223372036854775807, "Small": 1 } }
                """, ALL_KNOWN);
        assertEquals(Map.of("Small", 1L), result.config().basePrices());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void warnsAboutUnknownTopLevelKeys() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "items": {}, "sprad": 1 }
                """);
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("sprad"));
    }

    @Test
    void bundledDefaultConfigIsValid() throws IOException, TradeConfigException {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/" + TradeConfigManager.FILE_NAME)) {
            assertNotNull(in, "bundled default config missing");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Item IDs are checked against the real assets at server start, not here.
        var result = TradeConfigParser.parse(json, ALL_KNOWN);
        assertEquals(List.of(), result.warnings());
        assertFalse(result.config().basePrices().isEmpty());
    }

    @Test
    void parsesUnlocksAndRequirements() throws TradeConfigException {
        var result = parse("""
                {
                  "spread": 0,
                  "unlocks": {
                    "Stone": { "amount": 500, "accepts": ["Rock_Stone"] },
                    "Clay": { "amount": 300, "accepts": ["Soil_Dirt", "Not_An_Item"] }
                  },
                  "items": {
                    "Rock_Stone": { "price": 10, "requires": ["Stone"] },
                    "Soil_Dirt": { "price": 10, "requires": ["Stone", ["Clay", "Stone"]] },
                    "Soil_Sand": 4
                  }
                }
                """);
        TradeConfig config = result.config();
        assertEquals(List.of("Stone", "Clay"), List.copyOf(config.unlocks().keySet()));
        assertEquals(500, config.unlocks().get("Stone").amount());
        assertEquals(List.of("Soil_Dirt"), config.unlocks().get("Clay").accepts()); // unknown accepted item dropped
        assertEquals(List.of(Set.of("Stone")), config.requirementsOf("Rock_Stone"));
        assertEquals(List.of(Set.of("Stone"), Set.of("Clay", "Stone")), config.requirementsOf("Soil_Dirt"));
        assertEquals(List.of(), config.requirementsOf("Soil_Sand"));
        assertEquals(4, config.basePrice("Soil_Sand").orElseThrow());
        assertEquals(1, result.warnings().size());
    }

    @Test
    void skipsItemsRequiringUndefinedUnlocks() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "unlocks": { "Stone": { "amount": 5, "accepts": ["Rock_Stone"] } },
                  "items": { "Rock_Stone": { "price": 10, "requires": ["Nope"] }, "Soil_Dirt": 1,
                             "Soil_Sand": { "price": 1, "requires": [["Nope", "Stone"]] } } }
                """);
        assertFalse(result.config().isTradeable("Rock_Stone"));
        assertTrue(result.config().isTradeable("Soil_Dirt"));
        assertTrue(result.warnings().get(0).contains("Nope"));
        // An "any of" choice keeps its defined alternatives.
        assertEquals(List.of(Set.of("Stone")), result.config().requirementsOf("Soil_Sand"));
    }

    @Test
    void skipsInvalidUnlocks() throws TradeConfigException {
        var result = parse("""
                { "spread": 0, "items": {}, "unlocks": {
                    "NoAmount": { "accepts": ["Rock_Stone"] },
                    "Zero": { "amount": 0, "accepts": ["Rock_Stone"] },
                    "NoItems": { "amount": 5, "accepts": ["Unknown_Thing"] } } }
                """);
        assertTrue(result.config().unlocks().isEmpty());
        assertEquals(4, result.warnings().size()); // 3 skipped unlocks + 1 unknown accepted item
    }
}
