package io.github.obut.tradestation.config;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.obut.tradestation.pricing.Pricing;

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
                        prices = readItems(reader, itemIdKnown, warnings);
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
        return new Result(new TradeConfig(spread, prices), warnings);
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

    private static Map<String, Long> readItems(JsonReader reader, Predicate<String> itemIdKnown, List<String> warnings)
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

            if (reader.peek() != JsonToken.NUMBER) {
                warnings.add("Item \"" + itemId + "\": price must be a number, entry skipped");
                reader.skipValue();
                continue;
            }
            String literal = reader.nextString();
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
        }
        reader.endObject();
        return prices;
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
