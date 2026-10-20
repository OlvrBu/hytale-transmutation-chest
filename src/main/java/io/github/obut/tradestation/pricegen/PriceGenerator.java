package io.github.obut.tradestation.pricegen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives prices for everything that can be made from seed-priced items.
 * <p>
 * Rule: an item's price is the cheapest way to obtain it from priced inputs, per output item, rounded
 * down: {@code price(out) = min over conversions of floor(cost(inputs) / outputs)}, and never more than
 * its seed. Because no conversion ever produces more value than its inputs are worth, buying inputs,
 * converting and selling can't make a profit, whatever the spread.
 * <p>
 * Solved by repeated relaxation (like Bellman-Ford): prices only ever go down or become known, so it
 * terminates. Price 0 means "obtainable for free"; such items must not be sold for anything.
 */
public final class PriceGenerator {

    /** Price and, for reports, what determined it. */
    public record Price(long value, String reason) {
    }

    public record Result(Map<String, Price> prices, List<String> loweredSeeds, boolean converged) {
    }

    private static final int MAX_ROUNDS = 10_000;
    private static final int DIVISION_SCALE = 12;

    private PriceGenerator() {
    }

    public static Result generate(Map<String, Long> seeds, List<Conversion> conversions) {
        Map<String, Price> prices = new HashMap<>();
        seeds.forEach((id, value) -> {
            if (value < 0) {
                throw new IllegalArgumentException("seed price for " + id + " is negative");
            }
            prices.put(id, new Price(value, "seed"));
        });

        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < MAX_ROUNDS) {
            changed = false;
            for (Conversion conversion : conversions) {
                BigDecimal cost = cost(conversion, prices);
                int produced = conversion.outputs().values().stream().mapToInt(Integer::intValue).sum();
                if (cost == null || produced <= 0) {
                    continue;
                }
                long candidate = cost.divide(BigDecimal.valueOf(produced), 0, RoundingMode.FLOOR).longValueExact();
                for (String output : conversion.outputs().keySet()) {
                    Price current = prices.get(output);
                    if (current == null || candidate < current.value()) {
                        prices.put(output, new Price(candidate, conversion.source()));
                        changed = true;
                    }
                }
            }
        }

        List<String> lowered = new ArrayList<>();
        seeds.forEach((id, value) -> {
            Price p = prices.get(id);
            if (p.value() < value) {
                lowered.add(id + ": seed " + value + " -> " + p.value() + " (" + p.reason() + ")");
            }
        });
        return new Result(prices, lowered, !changed);
    }

    /** Cheapest total cost of the inputs, or null if some input has no priced candidate. */
    static BigDecimal cost(Conversion conversion, Map<String, Price> prices) {
        BigDecimal total = BigDecimal.ZERO;
        for (Conversion.Ingredient ingredient : conversion.inputs()) {
            BigDecimal unit = cheapestUnit(ingredient, prices);
            if (unit == null) {
                return null;
            }
            total = total.add(unit.multiply(BigDecimal.valueOf(ingredient.quantity())));
        }
        return total;
    }

    /** Price of one unit of this ingredient using its cheapest priced candidate (rounded down). */
    static BigDecimal cheapestUnit(Conversion.Ingredient ingredient, Map<String, Price> prices) {
        BigDecimal best = null;
        for (Map.Entry<String, Integer> candidate : ingredient.unitsPerItem().entrySet()) {
            Price price = prices.get(candidate.getKey());
            if (price == null || candidate.getValue() <= 0) {
                continue;
            }
            BigDecimal unit = BigDecimal.valueOf(price.value())
                    .divide(BigDecimal.valueOf(candidate.getValue()), DIVISION_SCALE, RoundingMode.DOWN);
            if (best == null || unit.compareTo(best) < 0) {
                best = unit;
            }
        }
        return best;
    }
}
