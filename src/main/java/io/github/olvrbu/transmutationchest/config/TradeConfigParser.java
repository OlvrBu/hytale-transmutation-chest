package io.github.olvrbu.transmutationchest.config;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.olvrbu.transmutationchest.pricing.Pricing;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Parses and validates tradeables.json. No server dependencies (Gson ships inside the server jar),
 * so it is unit-testable.
 * <p>
 * Problems that make the whole file unusable (malformed JSON, missing/negative spread, missing items)
 * throw {@link TradeConfigException}. Problems with a single item entry only skip that entry and are
 * reported as warnings, so one typo doesn't disable trading altogether.
 * <p>
 * Uses Gson's streaming reader rather than its tree model so that duplicate keys can be detected
 * (the tree model silently keeps the last one).
 */
public final class TradeConfigParser {

    /** Parsed config plus human-readable warnings about skipped or suspicious entries. */
    public record Result(TradeConfig config, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    private TradeConfigParser() {
    }

    /**
     * @param json        file contents
     * @param itemIdKnown returns whether an item ID exists in the game's assets; entries for unknown
     *                    IDs are skipped with a warning
     */
    public static Result parse(String json, Predicate<String> itemIdKnown) throws TradeConfigException {
        List<String> warnings = new ArrayList<>();
        BigDecimal spread = null;
        Map<String, Long> prices = null;
        Map<String, List<Set<String>>> requirements = new LinkedHashMap<>();
        Map<String, TradeConfig.Unlock> unlocks = null;

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new TradeConfigException("Top level must be a JSON object");
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "spread" -> {
                        if (spread != null) {
                            throw new TradeConfigException("Duplicate key \"spread\"");
                        }
                        spread = readSpread(reader);
                    }
                    case "items" -> {
                        if (prices != null) {
                            throw new TradeConfigException("Duplicate key \"items\"");
                        }
                        prices = readItems(reader, itemIdKnown, warnings, requirements);
                    }
                    case "unlocks" -> {
                        if (unlocks != null) {
                            throw new TradeConfigException("Duplicate key \"unlocks\"");
                        }
                        unlocks = readUnlocks(reader, itemIdKnown, warnings);
                    }
                    default -> {
                        warnings.add("Unknown key \"" + key + "\" ignored");
                        reader.skipValue();
                    }
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new TradeConfigException("Unexpected content after the top-level object");
            }
        } catch (IOException | IllegalStateException e) {
            // Gson reports syntax errors as MalformedJsonException (an IOException) and
            // wrong-token-type errors as IllegalStateException.
            throw new TradeConfigException("Malformed JSON: " + e.getMessage(), e);
        }

        if (spread == null) {
            throw new TradeConfigException("Missing \"spread\"");
        }
        if (prices == null) {
            throw new TradeConfigException("Missing \"items\"");
        }
        if (unlocks == null) {
            unlocks = Map.of();
        }
        // Undefined unlocks can never complete: drop them from "any of" choices; an item left with a
        // requirement nothing can satisfy could never be traded, so skip it loudly.
        for (var it = requirements.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            List<Set<String>> cleaned = new ArrayList<>();
            for (Set<String> anyOf : entry.getValue()) {
                Set<String> known = new java.util.LinkedHashSet<>();
                for (String unlock : anyOf) {
                    if (unlocks.containsKey(unlock)) {
                        known.add(unlock);
                    } else {
                        warnings.add("Item \"" + entry.getKey() + "\": requires undefined unlock \"" + unlock + "\"");
                    }
                }
                cleaned.add(known);
            }
            if (cleaned.stream().anyMatch(Set::isEmpty)) {
                warnings.add("Item \"" + entry.getKey() + "\": a requirement can't be met, entry skipped");
                prices.remove(entry.getKey());
                it.remove();
            } else {
                entry.setValue(cleaned);
            }
        }
        requirements.keySet().retainAll(prices.keySet());
        // Make sure every buy price is representable; drop entries that would overflow.
        for (var it = prices.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            try {
                Pricing.buyPrice(entry.getValue(), spread);
            } catch (ArithmeticException e) {
                warnings.add("Item \"" + entry.getKey() + "\": buy price overflows with this spread, entry skipped");
                it.remove();
            }
        }
        requirements.keySet().retainAll(prices.keySet());
        return new Result(new TradeConfig(spread, prices, unlocks, requirements), warnings);
    }

    private static BigDecimal readSpread(JsonReader reader) throws IOException, TradeConfigException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw new TradeConfigException("\"spread\" must be a number");
        }
        // nextString() on a NUMBER token returns the literal text, so BigDecimal keeps it exact.
        BigDecimal spread = new BigDecimal(reader.nextString());
        if (spread.signum() < 0) {
            throw new TradeConfigException("\"spread\" must be >= 0, got " + spread.toPlainString());
        }
        return spread;
    }

    /**
     * Each entry is either a price ({@code "Id": 10}) or an object with requirements
     * ({@code "Id": { "price": 10, "requires": ["Rock_Stone_Cobble"] }}).
     */
    private static Map<String, Long> readItems(JsonReader reader, Predicate<String> itemIdKnown, List<String> warnings,
                                               Map<String, List<Set<String>>> requirements)
            throws IOException, TradeConfigException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new TradeConfigException("\"items\" must be an object mapping item IDs to prices");
        }
        // LinkedHashMap keeps file order, which makes logs easier to follow.
        Map<String, Long> prices = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String itemId = reader.nextName();
            if (seen.contains(itemId)) {
                warnings.add("Item \"" + itemId + "\" listed more than once, all entries skipped");
                prices.remove(itemId);
                reader.skipValue();
                continue;
            }
            seen.add(itemId);

            String literal = null;
            List<Set<String>> required = new ArrayList<>();
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String field = reader.nextName();
                    if (field.equals("price") && reader.peek() == JsonToken.NUMBER) {
                        literal = reader.nextString();
                    } else if (field.equals("requires") && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        // Each element: "Unlock" (needed) or ["A", "B"] (any one of them).
                        reader.beginArray();
                        while (reader.hasNext()) {
                            Set<String> anyOf = new java.util.LinkedHashSet<>();
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray();
                                while (reader.hasNext()) {
                                    anyOf.add(reader.nextString());
                                }
                                reader.endArray();
                            } else {
                                anyOf.add(reader.nextString());
                            }
                            required.add(anyOf);
                        }
                        reader.endArray();
                    } else {
                        warnings.add("Item \"" + itemId + "\": unknown or invalid field \"" + field + "\" ignored");
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (reader.peek() == JsonToken.NUMBER) {
                literal = reader.nextString();
            } else {
                reader.skipValue();
            }
            if (literal == null) {
                warnings.add("Item \"" + itemId + "\": price must be a number, entry skipped");
                continue;
            }
            Long price = parsePrice(literal);
            if (price == null) {
                warnings.add("Item \"" + itemId + "\": price must be a positive integer, got " + literal + ", entry skipped");
                continue;
            }
            if (!itemIdKnown.test(itemId)) {
                warnings.add("Item \"" + itemId + "\": unknown item ID, entry skipped");
                continue;
            }
            prices.put(itemId, price);
            if (!required.isEmpty()) {
                requirements.put(itemId, required);
            }
        }
        reader.endObject();
        // A duplicated item was removed from prices; drop its requirements too.
        requirements.keySet().retainAll(prices.keySet());
        return prices;
    }

    /** {@code "unlocks": { "Clay": { "amount": 500, "accepts": ["Soil_Clay_Red", ...] } }} */
    private static Map<String, TradeConfig.Unlock> readUnlocks(JsonReader reader, Predicate<String> itemIdKnown,
                                                               List<String> warnings)
            throws IOException, TradeConfigException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new TradeConfigException("\"unlocks\" must be an object");
        }
        Map<String, TradeConfig.Unlock> unlocks = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            Long amount = null;
            List<String> accepts = new ArrayList<>();
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                warnings.add("Unlock \"" + id + "\": must be an object, skipped");
                reader.skipValue();
                continue;
            }
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (field.equals("amount") && reader.peek() == JsonToken.NUMBER) {
                    amount = parsePrice(reader.nextString());
                } else if (field.equals("accepts") && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String item = reader.nextString();
                        if (itemIdKnown.test(item)) {
                            accepts.add(item);
                        } else {
                            warnings.add("Unlock \"" + id + "\": unknown item \"" + item + "\" ignored");
                        }
                    }
                    reader.endArray();
                } else {
                    warnings.add("Unlock \"" + id + "\": unknown or invalid field \"" + field + "\" ignored");
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (amount == null) {
                warnings.add("Unlock \"" + id + "\": amount must be a positive integer, skipped");
            } else if (accepts.isEmpty()) {
                warnings.add("Unlock \"" + id + "\": accepts no known items, skipped");
            } else if (unlocks.put(id, new TradeConfig.Unlock(amount, accepts)) != null) {
                warnings.add("Unlock \"" + id + "\" listed more than once, last one used");
            }
        }
        reader.endObject();
        return unlocks;
    }

    /** Returns the price if {@code literal} is a positive integer that fits in a long (2.0 counts), else null. */
    private static Long parsePrice(String literal) {
        try {
            BigDecimal value = new BigDecimal(literal).stripTrailingZeros();
            if (value.signum() <= 0 || value.scale() > 0) {
                return null;
            }
            return value.longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
