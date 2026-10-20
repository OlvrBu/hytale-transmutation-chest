package io.github.obut.tradestation.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingTest {

    @Test
    void sellValueIsBaseTimesQuantity() {
        assertEquals(0, Pricing.sellValue(3, 0));
        assertEquals(3, Pricing.sellValue(3, 1));
        assertEquals(300, Pricing.sellValue(3, 100));
    }

    @Test
    void sellValueRejectsOverflow() {
        assertThrows(ArithmeticException.class, () -> Pricing.sellValue(Long.MAX_VALUE / 2, 3));
    }

    @Test
    void sellValueRejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> Pricing.sellValue(0, 1));
        assertThrows(IllegalArgumentException.class, () -> Pricing.sellValue(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Pricing.sellValue(1, -1));
    }

    @Test
    void buyPriceIsExactWhenNoRoundingNeeded() {
        assertEquals(5, Pricing.buyPrice(4, new BigDecimal("0.25")));
        assertEquals(7, Pricing.buyPrice(7, BigDecimal.ZERO));
    }

    @Test
    void buyPriceRoundsUp() {
        // 1 * 1.25 = 1.25 -> 2, 3 * 1.25 = 3.75 -> 4
        assertEquals(2, Pricing.buyPrice(1, new BigDecimal("0.25")));
        assertEquals(4, Pricing.buyPrice(3, new BigDecimal("0.25")));
        // Tiny spreads still round up, so the spread is never lost to rounding.
        assertEquals(2, Pricing.buyPrice(1, new BigDecimal("0.0001")));
    }

    @Test
    void buyPriceHasNoFloatingPointError() {
        // With doubles, 100 * 1.1 = 110.00000000000001, which would round up to 111.
        assertEquals(110, Pricing.buyPrice(100, new BigDecimal("0.1")));
        assertEquals(330, Pricing.buyPrice(300, new BigDecimal("0.1")));
    }

    @Test
    void buyPriceRejectsNegativeSpreadAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> Pricing.buyPrice(1, new BigDecimal("-0.1")));
        assertThrows(ArithmeticException.class, () -> Pricing.buyPrice(Long.MAX_VALUE, new BigDecimal("0.5")));
    }

    @Test
    void buyCostIsPerItemPriceTimesQuantity() {
        // Rounding happens per item: 3 items at ceil(1.25) = 2 each cost 6, not ceil(3.75) = 4.
        assertEquals(6, Pricing.buyCost(1, new BigDecimal("0.25"), 3));
        assertThrows(ArithmeticException.class, () -> Pricing.buyCost(Long.MAX_VALUE / 2, BigDecimal.ZERO, 3));
    }
}
