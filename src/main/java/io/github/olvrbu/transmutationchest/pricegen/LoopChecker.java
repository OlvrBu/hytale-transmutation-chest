package io.github.olvrbu.transmutationchest.pricegen;

import io.github.olvrbu.transmutationchest.config.TradeConfig;
import io.github.olvrbu.transmutationchest.pricing.Pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds craft-and-sell loops in an active price list: conversions where buying the inputs from a
 * chest and selling the outputs back earns more than it cost.
 * <p>
 * Only conversions whose every input can be bought (has a tradeable candidate) matter: anything else
 * needs gathered materials, which is normal income, not a loop.
 */
public final class LoopChecker {

    /**
     * @param revenue  what selling the outputs earns
     * @param buyCost  what buying the inputs costs (with spread); a real loop if revenue exceeds it
     * @param baseCost inputs at base price; revenue above this is a loop that only the spread prevents
     */
    public record Issue(Conversion conversion, long revenue, BigDecimal buyCost, BigDecimal baseCost) {
        public boolean isLoop() {
            return BigDecimal.valueOf(revenue).compareTo(buyCost) > 0;
        }
    }

    private LoopChecker() {
    }

    public static List<Issue> check(TradeConfig config, List<Conversion> conversions) {
        List<Issue> issues = new ArrayList<>();
        for (Conversion conversion : conversions) {
            long revenue = 0;
            for (var output : conversion.outputs().entrySet()) {
                revenue += config.basePrice(output.getKey()).orElse(0) * output.getValue();
            }
            if (revenue == 0) {
                continue;
            }
            BigDecimal buyCost = BigDecimal.ZERO;
            BigDecimal baseCost = BigDecimal.ZERO;
            boolean buyable = true;
            for (Conversion.Ingredient ingredient : conversion.inputs()) {
                BigDecimal bestBuy = null;
                BigDecimal bestBase = null;
                for (var candidate : ingredient.unitsPerItem().entrySet()) {
                    var base = config.basePrice(candidate.getKey());
                    if (base.isEmpty() || candidate.getValue() <= 0) {
                        continue;
                    }
                    BigDecimal units = BigDecimal.valueOf(candidate.getValue());
                    BigDecimal buy = BigDecimal.valueOf(Pricing.buyPrice(base.getAsLong(), config.spread()))
                            .divide(units, 12, RoundingMode.DOWN);
                    BigDecimal plain = BigDecimal.valueOf(base.getAsLong()).divide(units, 12, RoundingMode.DOWN);
                    bestBuy = bestBuy == null || buy.compareTo(bestBuy) < 0 ? buy : bestBuy;
                    bestBase = bestBase == null || plain.compareTo(bestBase) < 0 ? plain : bestBase;
                }
                if (bestBuy == null) {
                    buyable = false;
                    break;
                }
                BigDecimal quantity = BigDecimal.valueOf(ingredient.quantity());
                buyCost = buyCost.add(bestBuy.multiply(quantity));
                baseCost = baseCost.add(bestBase.multiply(quantity));
            }
            if (buyable && BigDecimal.valueOf(revenue).compareTo(baseCost) > 0) {
                issues.add(new Issue(conversion, revenue, buyCost, baseCost));
            }
        }
        return issues;
    }
}
