package io.github.olvrbu.transmutationchest.pricegen;

import io.github.olvrbu.transmutationchest.config.TradeConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.github.olvrbu.transmutationchest.pricegen.Conversion.Ingredient.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceGeneratorTest {

    private static Conversion recipe(String name, List<Conversion.Ingredient> in, Map<String, Integer> out) {
        return new Conversion(name, in, out);
    }

    private static long price(PriceGenerator.Result r, String id) {
        return r.prices().get(id).value();
    }

    @Test
    void derivesPricesAlongChains() {
        var r = PriceGenerator.generate(Map.of("Stone", 10L), List.of(
                recipe("brick", List.of(item("Stone", 1)), Map.of("Brick", 1)),
                recipe("half", List.of(item("Brick", 1)), Map.of("Half", 2)),
                recipe("wall", List.of(item("Brick", 3)), Map.of("Wall", 1))));
        assertEquals(10, price(r, "Brick"));
        assertEquals(5, price(r, "Half"));
        assertEquals(30, price(r, "Wall"));
        assertTrue(r.converged());
    }

    @Test
    void roundsDownSoCraftingNeverGainsValue() {
        var r = PriceGenerator.generate(Map.of("Stone", 10L), List.of(
                recipe("thirds", List.of(item("Stone", 1)), Map.of("Third", 3))));
        assertEquals(3, price(r, "Third")); // 3 * 3 = 9 <= 10
    }

    @Test
    void cheapestRecipeWinsAndLowersSeeds() {
        var r = PriceGenerator.generate(Map.of("Stone", 10L, "Brick", 50L), List.of(
                recipe("expensive", List.of(item("Stone", 4)), Map.of("Brick", 1)),
                recipe("cheap", List.of(item("Stone", 1)), Map.of("Brick", 1))));
        assertEquals(10, price(r, "Brick"));
        assertEquals("cheap", r.prices().get("Brick").reason());
        assertEquals(1, r.loweredSeeds().size());
    }

    @Test
    void groupSwapsForceEqualPrices() {
        // A block group cycles A -> B -> C -> A for free: all must end up at the cheapest.
        var r = PriceGenerator.generate(Map.of("A", 30L, "B", 10L, "C", 20L), List.of(
                recipe("group", List.of(item("A", 1)), Map.of("B", 1)),
                recipe("group", List.of(item("B", 1)), Map.of("C", 1)),
                recipe("group", List.of(item("C", 1)), Map.of("A", 1))));
        assertEquals(10, price(r, "A"));
        assertEquals(10, price(r, "B"));
        assertEquals(10, price(r, "C"));
    }

    @Test
    void resourceInputsUseCheapestCandidatePerUnit() {
        // "any log": Oak counts as 1 unit for 20, Big counts as 4 units for 40 (10 per unit).
        var anyLog = new Conversion.Ingredient(Map.of("Oak", 1, "Big", 4, "Unpriced", 1), 2);
        var r = PriceGenerator.generate(Map.of("Oak", 20L, "Big", 40L), List.of(
                recipe("planks", List.of(anyLog), Map.of("Planks", 1))));
        assertEquals(20, price(r, "Planks"));
    }

    @Test
    void unpricedInputsLeaveOutputsUnpriced() {
        var r = PriceGenerator.generate(Map.of("Stone", 10L), List.of(
                recipe("needs fabric", List.of(item("Stone", 1), item("Fabric", 1)), Map.of("Banner", 1))));
        assertFalse(r.prices().containsKey("Banner"));
    }

    @Test
    void freeItemsGetZero() {
        var r = PriceGenerator.generate(Map.of("Stone", 1L), List.of(
                recipe("many", List.of(item("Stone", 1)), Map.of("Pebble", 4))));
        assertEquals(0, price(r, "Pebble"));
    }

    @Test
    void generatedPricesHaveNoLoops() {
        var conversions = List.of(
                recipe("brick", List.of(item("Stone", 1)), Map.of("Brick", 1)),
                recipe("half", List.of(item("Brick", 1)), Map.of("Half", 2)),
                recipe("back", List.of(item("Half", 2)), Map.of("Brick", 1)),
                recipe("wall", List.of(item("Brick", 3), item("Stone", 1)), Map.of("Wall", 2)));
        var r = PriceGenerator.generate(Map.of("Stone", 11L), conversions);
        Map<String, Long> base = new java.util.LinkedHashMap<>();
        r.prices().forEach((id, p) -> { if (p.value() > 0) base.put(id, p.value()); });
        var issues = LoopChecker.check(new TradeConfig(BigDecimal.ZERO, base), conversions);
        assertEquals(List.of(), issues);
    }

    @Test
    void tracksWhichSeedsEachItemComesFrom() {
        var anyLog = new Conversion.Ingredient(Map.of("Oak", 1, "Birch", 1), 1);
        var r = PriceGenerator.generate(Map.of("Stone", 10L, "Oak", 20L, "Birch", 20L), List.of(
                recipe("brick", List.of(item("Stone", 1)), Map.of("Brick", 1)),
                recipe("stairs", List.of(item("Brick", 1)), Map.of("Stairs", 1)),
                recipe("planks", List.of(anyLog), Map.of("Planks", 1)),
                recipe("table", List.of(item("Planks", 2), item("Stairs", 1)), Map.of("Table", 1)),
                recipe("group", List.of(item("Brick", 1)), Map.of("Smooth", 1)),
                recipe("group", List.of(item("Smooth", 1)), Map.of("Brick", 1))));
        var deps = PriceGenerator.seedDependencies(r);
        assertEquals(List.of(java.util.Set.of("Stone")), deps.get("Stairs"));
        assertEquals(List.of(java.util.Set.of("Stone")), deps.get("Smooth"));
        // Equally cheap logs are alternatives: any one of them is enough.
        assertEquals(List.of(java.util.Set.of("Birch", "Oak")), deps.get("Planks"));
        assertEquals(List.of(java.util.Set.of("Birch", "Oak"), java.util.Set.of("Stone")), deps.get("Table"));
        assertEquals(List.of(java.util.Set.of("Oak")), deps.get("Oak"));
    }

    @Test
    void dropsImpliedRequirements() {
        assertEquals(List.of(java.util.Set.of("A")),
                PriceGenerator.simplify(List.of(java.util.Set.of("A", "B"), java.util.Set.of("A"), java.util.Set.of("A"))));
        assertEquals(List.of(java.util.Set.of("A"), java.util.Set.of("B", "C")),
                PriceGenerator.simplify(List.of(java.util.Set.of("C", "B"), java.util.Set.of("A"))));
    }

    @Test
    void cheaperCandidateIsNotAnAlternative() {
        var anyLog = new Conversion.Ingredient(Map.of("Oak", 1, "Exotic", 1), 1);
        var r = PriceGenerator.generate(Map.of("Oak", 20L, "Exotic", 30L), List.of(
                recipe("planks", List.of(anyLog), Map.of("Planks", 1))));
        assertEquals(List.of(java.util.Set.of("Oak")), PriceGenerator.seedDependencies(r).get("Planks"));
    }

    @Test
    void resolvesRequirementsThroughCycles() {
        // Sticks are cheapest via salvaging a fence made from planks and sticks: a cycle.
        var r = PriceGenerator.generate(Map.of("Log", 40L), List.of(
                recipe("planks", List.of(item("Log", 1)), Map.of("Planks", 4)),
                recipe("sticks", List.of(item("Planks", 1)), Map.of("Stick", 2)),
                recipe("fence", List.of(item("Planks", 1), item("Stick", 1)), Map.of("Fence", 1)),
                recipe("salvage", List.of(item("Fence", 1)), Map.of("Stick", 4)),
                recipe("mug", List.of(item("Planks", 1), item("Stick", 1)), Map.of("Mug", 1))));
        var deps = PriceGenerator.seedDependencies(r);
        assertEquals(List.of(java.util.Set.of("Log")), deps.get("Stick"));
        assertEquals(List.of(java.util.Set.of("Log")), deps.get("Mug"));
    }

    @Test
    void restrictingInputsDropsItemsNeedingNonBlocks() {
        var conversions = List.of(
                recipe("planks", List.of(item("Log", 1)), Map.of("Planks", 4)),
                recipe("sticks", List.of(item("Planks", 1)), Map.of("Stick", 2)),
                recipe("table", List.of(item("Planks", 4)), Map.of("Table", 1)),
                recipe("bed", List.of(item("Planks", 2), new Conversion.Ingredient(Map.of("Fibre", 1, "Planks", 1), 3)), Map.of("Bed", 1)),
                recipe("fence", List.of(item("Stick", 4)), Map.of("Fence", 1)));
        var blocks = java.util.Set.of("Log", "Planks", "Table", "Bed", "Fence");
        var reachable = PriceGenerator.generate(Map.of("Log", 40L),
                PriceGenerationService.restrictInputs(conversions, blocks)).prices().keySet();
        assertTrue(reachable.contains("Table"));
        assertTrue(reachable.contains("Bed"));      // its "any of" input can use planks instead of fibre
        assertFalse(reachable.contains("Fence"));   // only via sticks, a non-block ingredient
        // Sticks themselves are reachable (made from planks) but never listed: not a block candidate.
    }
}
