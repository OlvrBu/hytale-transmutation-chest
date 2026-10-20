package io.github.obut.tradestation.config;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Validated contents of tradeables.json: an item is tradeable iff it has an entry in {@code basePrices}
 * (and, at a given station, all of its required unlocks are complete there).
 * <p>
 * A Java {@code record} is an immutable data class, like a Python frozen dataclass: the compiler
 * generates the constructor, accessors ({@code spread()}, {@code basePrices()}), equals and hashCode.
 *
 * @param unlocks      unlock ID to definition, in file order
 * @param requirements item ID to what it needs: every entry (an "any of" set of unlock IDs) must have
 *                     at least one completed unlock. E.g. [[Rock_Stone_Cobble], [Oak, Apple, Ash]]
 *                     means "stone cobble AND any one of these logs". Items without an entry need none.
 */
public record TradeConfig(BigDecimal spread, Map<String, Long> basePrices, Map<String, Unlock> unlocks,
                          Map<String, List<Set<String>>> requirements) {

    /**
     * Something stations unlock by donation, e.g. "Rock_Stone_Cobble" or a group like "Clay".
     *
     * @param amount  how many accepted items must be donated
     * @param accepts item IDs that count as donations (each item counts 1); first one is the icon
     */
    public record Unlock(long amount, List<String> accepts) {
        public Unlock {
            accepts = List.copyOf(accepts);
        }
    }

    public static final TradeConfig EMPTY = new TradeConfig(BigDecimal.ZERO, Map.of());

    public TradeConfig {
        // Compact constructor: runs before the fields are assigned. Read-only copies that keep the
        // file's order (Map.copyOf would lose it), so the UI lists items as the admin wrote them.
        basePrices = Collections.unmodifiableMap(new LinkedHashMap<>(basePrices));
        unlocks = Collections.unmodifiableMap(new LinkedHashMap<>(unlocks));
        LinkedHashMap<String, List<Set<String>>> copy = new LinkedHashMap<>();
        requirements.forEach((item, needed) -> copy.put(item, needed.stream().map(Set::copyOf).toList()));
        requirements = Collections.unmodifiableMap(copy);
    }

    /** A config without unlocks: every listed item is tradeable everywhere. */
    public TradeConfig(BigDecimal spread, Map<String, Long> basePrices) {
        this(spread, basePrices, Map.of(), Map.of());
    }

    public boolean isTradeable(String itemId) {
        return basePrices.containsKey(itemId);
    }

    /** Base (sell) price of one item, or empty if the item isn't tradeable. */
    public OptionalLong basePrice(String itemId) {
        Long price = basePrices.get(itemId);
        return price == null ? OptionalLong.empty() : OptionalLong.of(price);
    }

    /** What this item needs: each set is "any one of these unlocks" (empty list if nothing). */
    public List<Set<String>> requirementsOf(String itemId) {
        return requirements.getOrDefault(itemId, List.of());
    }

    /** Whether the unlock appears anywhere in the item's requirements. */
    public boolean mentions(String itemId, String unlockId) {
        for (Set<String> anyOf : requirementsOf(itemId)) {
            if (anyOf.contains(unlockId)) {
                return true;
            }
        }
        return false;
    }
}
