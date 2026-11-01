package io.github.olvrbu.transmutationchest.config;

import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a {@link TradeConfig} as tradeables.json, in the same format the price generator produces
 * and {@link TradeConfigParser} reads: unlocks first, then items (a plain number when the item needs
 * no unlock, otherwise {@code {"price": n, "requires": [...]}}).
 */
public final class TradeConfigWriter {

    private TradeConfigWriter() {
    }

    public static String write(TradeConfig config) {
        StringWriter out = new StringWriter();
        try (JsonWriter json = new JsonWriter(out)) {
            json.setIndent("  ");
            json.beginObject();
            json.name("spread").jsonValue(config.spread().toPlainString());
            if (!config.unlocks().isEmpty()) {
                json.name("unlocks").beginObject();
                for (Map.Entry<String, TradeConfig.Unlock> unlock : config.unlocks().entrySet()) {
                    json.name(unlock.getKey()).beginObject();
                    json.name("amount").value(unlock.getValue().amount());
                    json.name("accepts").beginArray();
                    for (String item : unlock.getValue().accepts()) {
                        json.value(item);
                    }
                    json.endArray();
                    json.endObject();
                }
                json.endObject();
            }
            json.name("items").beginObject();
            for (Map.Entry<String, Long> item : config.basePrices().entrySet()) {
                List<Set<String>> needed = config.requirementsOf(item.getKey());
                if (needed.isEmpty()) {
                    json.name(item.getKey()).value(item.getValue());
                    continue;
                }
                json.name(item.getKey()).beginObject();
                json.name("price").value(item.getValue());
                json.name("requires").beginArray();
                for (Set<String> anyOf : needed) {
                    if (anyOf.size() == 1) {
                        json.value(anyOf.iterator().next());
                    } else {
                        json.beginArray();
                        for (String unlock : anyOf) {
                            json.value(unlock);
                        }
                        json.endArray();
                    }
                }
                json.endArray();
                json.endObject();
            }
            json.endObject();
            json.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // StringWriter doesn't actually throw
        }
        return out + "\n";
    }
}
