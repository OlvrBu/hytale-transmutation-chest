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

    /**
     * Price and what determined it: {@code via} is the conversion that set it, or null for a seed.
     */
    public record Price(long value, String reason, Conversion via) {
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
            prices.put(id, new Price(value, "seed", null));
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
                        prices.put(output, new Price(candidate, conversion.source(), conversion));
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

    /**
     * Which seeds each priced item's cheapest path starts from, as requirements: a list of "any of"
     * sets that must all be satisfied. Stone brick stairs give [[Rock_Stone_Cobble]]; hardwood planks,
     * made from "any hardwood log" where all logs cost the same, give [[Oak, Apple, Ash, ...]].
     * <p>
     * For each input of the conversion that set an item's price, every equally cheap candidate is a
     * valid way to make the item, so their requirements are offered as alternatives. That's exact when
     * each candidate has a single requirement (the common case: raw materials); otherwise the first
     * candidate by ID is used. Recomputed in rounds until stable, which also handles cycles such as
     * block-group rings. Requirements implied by a stricter one are dropped (e.g. [[A], [A, B]] is [[A]]).
     */
    public static Map<String, List<java.util.Set<String>>> seedDependencies(Result result) {
        Map<String, List<java.util.Set<String>>> deps = new HashMap<>();
        result.prices().forEach((id, p) -> {
            if (p.via() == null) {
                deps.put(id, List.of(java.util.Set.of(id)));
            }
        });
        // Pass 1 waits until all inputs are known. Pass 2 breaks cycles (e.g. sticks whose cheapest
        // price comes from salvaging something made with sticks): an input still unknown is skipped,
        // as long as some other input of the conversion grounds the requirements.
        for (boolean breakCycles : new boolean[] {false, true}) {
            relaxDependencies(result, deps, breakCycles);
        }
        return deps;
    }

    private static void relaxDependencies(Result result, Map<String, List<java.util.Set<String>>> deps, boolean breakCycles) {
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < MAX_ROUNDS) {
            changed = false;
            for (Map.Entry<String, Price> entry : result.prices().entrySet()) {
                Conversion via = entry.getValue().via();
                if (via == null || (breakCycles && deps.containsKey(entry.getKey()))) {
                    continue; // seeds; and in pass 2 only the items pass 1 couldn't resolve
                }
                List<java.util.Set<String>> combined = new ArrayList<>();
                boolean known = true;
                int grounded = 0;
                for (Conversion.Ingredient ingredient : via.inputs()) {
                    List<String> tied = cheapestCandidates(ingredient, result.prices());
                    tied.removeIf(id -> !deps.containsKey(id)); // not worked out yet
                    if (tied.isEmpty()) {
                        if (breakCycles) {
                            continue;
                        }
                        known = false;
                        break;
                    }
                    grounded++;
                    if (tied.stream().allMatch(id -> deps.get(id).size() == 1)) {
                        java.util.Set<String> anyOf = new java.util.TreeSet<>();
                        tied.forEach(id -> anyOf.addAll(deps.get(id).get(0)));
                        combined.add(anyOf);
                    } else {
                        combined.addAll(deps.get(tied.get(0)));
                    }
                }
                if (!known || grounded == 0) {
                    continue;
                }
                List<java.util.Set<String>> simplified = simplify(combined);
                if (!simplified.equals(deps.get(entry.getKey()))) {
                    deps.put(entry.getKey(), simplified);
                    changed = true;
                }
            }
        }
    }

    /** Removes duplicate and implied requirements and sorts, so equal requirements compare equal. */
    static List<java.util.Set<String>> simplify(List<java.util.Set<String>> requirements) {
        // Duplicates first (sets with equal contents are equal), then anything a stricter one implies.
        List<java.util.Set<String>> unique = new ArrayList<>();
        for (java.util.Set<String> anyOf : requirements) {
            java.util.Set<String> sorted = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(anyOf));
            if (!unique.contains(sorted)) {
                unique.add(sorted);
            }
        }
        List<java.util.Set<String>> result = new ArrayList<>();
        for (java.util.Set<String> anyOf : unique) {
            boolean implied = unique.stream().anyMatch(other -> other.size() < anyOf.size() && anyOf.containsAll(other));
            if (!implied) {
                result.add(anyOf);
            }
        }
        result.sort(java.util.Comparator.comparing(Object::toString));
        return List.copyOf(result);
    }

    /** All candidates sharing the cheapest unit price, sorted by ID. */
    static List<String> cheapestCandidates(Conversion.Ingredient ingredient, Map<String, Price> prices) {
        List<String> best = new ArrayList<>();
        BigDecimal bestUnit = null;
        for (String id : new java.util.TreeSet<>(ingredient.unitsPerItem().keySet())) {
            Price price = prices.get(id);
            int units = ingredient.unitsPerItem().get(id);
            if (price == null || units <= 0) {
                continue;
            }
            BigDecimal unit = BigDecimal.valueOf(price.value())
                    .divide(BigDecimal.valueOf(units), DIVISION_SCALE, RoundingMode.DOWN);
            int cmp = bestUnit == null ? -1 : unit.compareTo(bestUnit);
            if (cmp < 0) {
                bestUnit = unit;
                best.clear();
            }
            if (cmp <= 0) {
                best.add(id);
            }
        }
        return best;
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
