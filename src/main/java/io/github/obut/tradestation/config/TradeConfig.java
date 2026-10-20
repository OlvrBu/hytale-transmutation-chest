package io.github.obut.tradestation.config;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Validated contents of tradeables.json: an item is tradeable iff it has an entry in {@code basePrices}.
 * <p>
 * A Java {@code record} is an immutable data class, like a Python frozen dataclass: the compiler
 * generates the constructor, accessors ({@code spread()}, {@code basePrices()}), equals and hashCode.
 */
public record TradeConfig(BigDecimal spread, Map<String, Long> basePrices) {

    public static final TradeConfig EMPTY = new TradeConfig(BigDecimal.ZERO, Map.of());

    public TradeConfig {
        // Compact constructor: runs before the fields are assigned. Read-only copy that keeps the
        // file's item order (Map.copyOf would lose it), so the UI lists items as the admin wrote them.
        basePrices = Collections.unmodifiableMap(new LinkedHashMap<>(basePrices));
    }

    public boolean isTradeable(String itemId) {
        return basePrices.containsKey(itemId);
    }

    /** Base (sell) price of one item, or empty if the item isn't tradeable. */
    public OptionalLong basePrice(String itemId) {
        Long price = basePrices.get(itemId);
        return price == null ? OptionalLong.empty() : OptionalLong.of(price);
    }
}
