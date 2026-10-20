package io.github.obut.tradestation.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementsAndWriterTest {

    @Test
    void parsesAndFormatsRequirementSpecs() {
        assertEquals(List.of(), Requirements.parse("none"));
        assertEquals(List.of(), Requirements.parse("  "));
        var parsed = Requirements.parse("Rock_Stone_Cobble + Wood_Oak_Trunk|Wood_Ash_Trunk");
        assertEquals(parsed, Requirements.parse("Rock_Stone_Cobble,Wood_Oak_Trunk|Wood_Ash_Trunk"));
        assertEquals(List.of(Set.of("Rock_Stone_Cobble"), Set.of("Wood_Oak_Trunk", "Wood_Ash_Trunk")), parsed);
        assertEquals("Rock_Stone_Cobble+Wood_Oak_Trunk|Wood_Ash_Trunk", Requirements.format(parsed));
        assertEquals("none", Requirements.format(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Requirements.parse("A++B"));
        assertThrows(IllegalArgumentException.class, () -> Requirements.parse("A,|"));
    }

    @Test
    void writtenConfigParsesBackIdentically() throws TradeConfigException {
        TradeConfig config = new TradeConfig(new BigDecimal("0.25"),
                Map.of("Brick", 10L, "Planks", 20L, "Free", 3L),
                Map.of("Stone", new TradeConfig.Unlock(500, List.of("Cobble")),
                        "Oak", new TradeConfig.Unlock(200, List.of("Oak_Log")),
                        "Ash", new TradeConfig.Unlock(200, List.of("Ash_Log"))),
                Map.of("Brick", List.of(Set.of("Stone")), "Planks", List.of(Set.of("Oak", "Ash"))));
        String json = TradeConfigWriter.write(config);
        TradeConfigParser.Result back = TradeConfigParser.parse(json, id -> true);
        assertEquals(List.of(), back.warnings());
        assertEquals(config, back.config());
    }

    @Test
    void editsItems() {
        TradeConfig config = new TradeConfig(BigDecimal.ZERO, Map.of("A", 1L));
        TradeConfig added = config.withItem("B", 5, List.of(Set.of("X")));
        assertEquals(5, added.basePrice("B").orElseThrow());
        assertEquals(List.of(Set.of("X")), added.requirementsOf("B"));
        TradeConfig cleared = added.withItem("B", 6, List.of());
        assertEquals(List.of(), cleared.requirementsOf("B"));
        TradeConfig removed = cleared.withoutItem("B");
        assertFalse(removed.isTradeable("B"));
        assertTrue(removed.isTradeable("A"));
        assertThrows(IllegalArgumentException.class, () -> config.withItem("C", 0, List.of()));
    }

    @Test
    void rewritingTheBundledFileChangesNothing() throws Exception {
        String original;
        try (var in = getClass().getResourceAsStream("/tradeables.json")) {
            original = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        TradeConfig config = TradeConfigParser.parse(original, id -> true).config();
        assertEquals(original, TradeConfigWriter.write(config));
    }
}
