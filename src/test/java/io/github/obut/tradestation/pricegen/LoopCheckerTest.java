package io.github.obut.tradestation.pricegen;

import io.github.obut.tradestation.config.TradeConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static io.github.obut.tradestation.pricegen.Conversion.Ingredient.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopCheckerTest {

    private static final Conversion BRICK = new Conversion("brick", List.of(item("Stone", 1)), Map.of("Brick", 1));

    @Test
    void flagsRealLoops() {
        // Buy stone for ceil(10 * 1.25) = 13, sell brick for 20: profit.
        var issues = LoopChecker.check(new TradeConfig(new BigDecimal("0.25"), Map.of("Stone", 10L, "Brick", 20L)), List.of(BRICK));
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).isLoop());
    }

    @Test
    void reportsLoopsOnlyTheSpreadPrevents() {
        // Brick 12 > stone 10, but buying stone costs 13: not a loop now, a loop if spread drops to 0.
        var issues = LoopChecker.check(new TradeConfig(new BigDecimal("0.25"), Map.of("Stone", 10L, "Brick", 12L)), List.of(BRICK));
        assertEquals(1, issues.size());
        assertFalse(issues.get(0).isLoop());
    }

    @Test
    void ignoresFairPricesAndUnbuyableInputs() {
        assertEquals(List.of(), LoopChecker.check(new TradeConfig(BigDecimal.ZERO, Map.of("Stone", 10L, "Brick", 10L)), List.of(BRICK)));
        assertEquals(List.of(), LoopChecker.check(new TradeConfig(BigDecimal.ZERO, Map.of("Brick", 99L)), List.of(BRICK)));
    }
}
