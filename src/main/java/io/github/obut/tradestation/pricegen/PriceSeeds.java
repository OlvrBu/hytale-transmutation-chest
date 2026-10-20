package io.github.obut.tradestation.pricegen;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.obut.tradestation.config.TradeConfigException;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input of the price generator (price_seeds.json): hand-set prices of raw materials, plus which
 * blocks must not become tradeable even if they can be priced.
 *
 * <pre>
 * {
 *   "spread": 0.25,                         // copied into the generated tradeables.json
 *   "excludeCategories": ["Furniture.Benches", "Tool."],  // prefix match on item categories
 *   "excludeItems": ["Trade_Station", "Bench_*"],   // exact IDs, or prefixes ending in *
 *   "seeds": { "Rock_Stone_Cobble": 10 }    // raw prices, positive integers
 * }
 * </pre>
 */
public record PriceSeeds(BigDecimal spread, Map<String, Long> seeds, List<String> excludeCategories,
                         List<String> excludeItems) {

    public PriceSeeds {
        seeds = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(seeds));
        excludeCategories = List.copyOf(excludeCategories);
        excludeItems = List.copyOf(excludeItems);
    }

    /** Whether a priced item may be put in the generated list. */
    public boolean includes(AssetConversions.ItemInfo item) {
        if (!item.placeable()) {
            return false;
        }
        for (String excluded : excludeItems) {
            // "Bench_*" excludes every item ID starting with "Bench_"; anything else is an exact ID.
            boolean match = excluded.endsWith("*")
                    ? item.id().startsWith(excluded.substring(0, excluded.length() - 1))
                    : item.id().equals(excluded);
            if (match) {
                return false;
            }
        }
        for (String category : item.categories()) {
            for (String prefix : excludeCategories) {
                if (category.startsWith(prefix)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static PriceSeeds parse(String json) throws TradeConfigException {
        BigDecimal spread = null;
        Map<String, Long> seeds = null;
        List<String> excludeCategories = new ArrayList<>();
        List<String> excludeItems = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "spread" -> {
                        if (reader.peek() != JsonToken.NUMBER) {
                            throw new TradeConfigException("\"spread\" must be a number");
                        }
                        spread = new BigDecimal(reader.nextString());
                        if (spread.signum() < 0) {
                            throw new TradeConfigException("\"spread\" must be >= 0");
                        }
                    }
                    case "excludeCategories" -> readStrings(reader, excludeCategories);
                    case "excludeItems" -> readStrings(reader, excludeItems);
                    case "seeds" -> {
                        seeds = new LinkedHashMap<>();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String id = reader.nextName();
                            if (reader.peek() != JsonToken.NUMBER) {
                                throw new TradeConfigException("seed \"" + id + "\" must be a number");
                            }
                            long value;
                            try {
                                value = new BigDecimal(reader.nextString()).longValueExact();
                            } catch (ArithmeticException e) {
                                throw new TradeConfigException("seed \"" + id + "\" must be a whole number");
                            }
                            if (value <= 0) {
                                throw new TradeConfigException("seed \"" + id + "\" must be positive");
                            }
                            if (seeds.put(id, value) != null) {
                                throw new TradeConfigException("seed \"" + id + "\" listed twice");
                            }
                        }
                        reader.endObject();
                    }
                    default -> throw new TradeConfigException("Unknown key \"" + key + "\"");
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new TradeConfigException("Unexpected content after the top-level object");
            }
        } catch (IOException | IllegalStateException e) {
            throw new TradeConfigException("Malformed JSON: " + e.getMessage(), e);
        }
        if (spread == null || seeds == null) {
            throw new TradeConfigException("\"spread\" and \"seeds\" are required");
        }
        return new PriceSeeds(spread, seeds, excludeCategories, excludeItems);
    }

    private static void readStrings(JsonReader reader, List<String> into) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            into.add(reader.nextString());
        }
        reader.endArray();
    }
}
