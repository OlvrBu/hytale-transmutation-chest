package io.github.olvrbu.transmutationchest.pricegen;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.olvrbu.transmutationchest.config.TradeConfigException;

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
 *   "excludeItems": ["Transmutation_Chest", "Bench_*"],   // exact IDs, or prefixes ending in *
 *   "seeds": { "Rock_Stone_Cobble": 10 },   // raw prices, positive integers
 *   "unlocks": {                            // optional; without it nothing needs unlocking
 *     "defaultAmount": 500,                 // items to donate per unlock
 *     "amounts": { "Clay": 300 },           // per-unlock overrides
 *     "groups": { "Clay": ["Soil_Clay_Red", "Soil_Clay_Blue"] }  // several seeds, one unlock
 *   }
 * }
 * </pre>
 * Each seed is its own unlock (ID = seed item ID) unless it belongs to a group (ID = group name).
 */
public record PriceSeeds(BigDecimal spread, Map<String, Long> seeds, List<String> excludeCategories,
                         List<String> excludeItems, Long unlockDefaultAmount, Map<String, Long> unlockAmounts,
                         Map<String, List<String>> unlockGroups) {

    public PriceSeeds {
        seeds = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(seeds));
        excludeCategories = List.copyOf(excludeCategories);
        excludeItems = List.copyOf(excludeItems);
        unlockAmounts = Map.copyOf(unlockAmounts);
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
        unlockGroups.forEach((id, members) -> groups.put(id, List.copyOf(members)));
        unlockGroups = java.util.Collections.unmodifiableMap(groups);
    }

    public PriceSeeds(BigDecimal spread, Map<String, Long> seeds, List<String> excludeCategories, List<String> excludeItems) {
        this(spread, seeds, excludeCategories, excludeItems, null, Map.of(), Map.of());
    }

    /** Whether items need unlocking at all. */
    public boolean unlocksEnabled() {
        return unlockDefaultAmount != null;
    }

    /** The unlock a seed belongs to: its group's name, or the seed's own ID. */
    public String unlockIdFor(String seedId) {
        for (var group : unlockGroups.entrySet()) {
            if (group.getValue().contains(seedId)) {
                return group.getKey();
            }
        }
        return seedId;
    }

    /** Items that count as donations for an unlock: the group's members, or the seed itself. */
    public List<String> acceptedBy(String unlockId) {
        return unlockGroups.getOrDefault(unlockId, List.of(unlockId));
    }

    public long amountFor(String unlockId) {
        return unlockAmounts.getOrDefault(unlockId, unlockDefaultAmount);
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
        Long defaultAmount = null;
        Map<String, Long> amounts = new LinkedHashMap<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();
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
                    case "unlocks" -> {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String field = reader.nextName();
                            switch (field) {
                                case "defaultAmount" -> defaultAmount = readPositive(reader, "unlocks.defaultAmount");
                                case "amounts" -> {
                                    reader.beginObject();
                                    while (reader.hasNext()) {
                                        String id = reader.nextName();
                                        amounts.put(id, readPositive(reader, "unlocks.amounts." + id));
                                    }
                                    reader.endObject();
                                }
                                case "groups" -> {
                                    reader.beginObject();
                                    while (reader.hasNext()) {
                                        String id = reader.nextName();
                                        List<String> members = new ArrayList<>();
                                        readStrings(reader, members);
                                        groups.put(id, members);
                                    }
                                    reader.endObject();
                                }
                                default -> throw new TradeConfigException("Unknown key \"unlocks." + field + "\"");
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
        if (!groups.isEmpty() && defaultAmount == null) {
            throw new TradeConfigException("\"unlocks.defaultAmount\" is required when unlocks are configured");
        }
        java.util.Set<String> grouped = new java.util.HashSet<>();
        for (var group : groups.entrySet()) {
            if (seeds.containsKey(group.getKey())) {
                throw new TradeConfigException("unlock group \"" + group.getKey() + "\" has the same name as a seed");
            }
            for (String member : group.getValue()) {
                if (!seeds.containsKey(member)) {
                    throw new TradeConfigException("unlock group \"" + group.getKey() + "\": \"" + member + "\" is not a seed");
                }
                if (!grouped.add(member)) {
                    throw new TradeConfigException("seed \"" + member + "\" is in more than one unlock group");
                }
            }
        }
        return new PriceSeeds(spread, seeds, excludeCategories, excludeItems, defaultAmount, amounts, groups);
    }

    private static long readPositive(JsonReader reader, String name) throws IOException, TradeConfigException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw new TradeConfigException("\"" + name + "\" must be a number");
        }
        try {
            long value = new BigDecimal(reader.nextString()).longValueExact();
            if (value <= 0) {
                throw new TradeConfigException("\"" + name + "\" must be positive");
            }
            return value;
        } catch (ArithmeticException e) {
            throw new TradeConfigException("\"" + name + "\" must be a whole number");
        }
    }

    private static void readStrings(JsonReader reader, List<String> into) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            into.add(reader.nextString());
        }
        reader.endArray();
    }
}
