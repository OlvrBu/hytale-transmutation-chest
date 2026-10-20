package io.github.obut.tradestation.pricegen;

import com.google.gson.stream.JsonWriter;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.config.TradeConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * File handling around {@link PriceGenerator} and {@link LoopChecker}: reads price_seeds.json from the
 * plugin data directory and writes results into its "generated" subfolder for the admin to review.
 * The generated list is never activated automatically.
 */
public final class PriceGenerationService {

    public static final String SEEDS_FILE = "price_seeds.json";
    public static final String OUTPUT_DIR = "generated";
    public static final String GENERATED_FILE = "tradeables.generated.json";
    public static final String GENERATED_REPORT = "tradeables.generated.report.txt";
    public static final String CHECK_REPORT = "tradeables.check.txt";

    public record GenerateSummary(int tradeable, int loweredSeeds, int unpricedRaw, int unpricedOther,
                                  int zeroPriced, boolean converged, Path output, Path report) {
    }

    public record CheckSummary(int loops, int spreadOnly, Path report) {
    }

    private final Path dataDirectory;

    public PriceGenerationService(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public GenerateSummary generate() throws IOException, TradeConfigException {
        PriceSeeds seeds = PriceSeeds.parse(readSeeds());
        AssetConversions.Graph graph = AssetConversions.build();
        PriceGenerator.Result result = PriceGenerator.generate(seeds.seeds(), graph.conversions());

        // Which items can be made from tradeable-candidate blocks alone (no sticks, fibre, fabric...).
        // Prices still come from ALL conversions above: if a non-block route were cheaper, pricing by
        // the block-only route would let players buy, craft via sticks and sell at a profit.
        java.util.Set<String> candidates = new java.util.HashSet<>();
        graph.items().values().stream().filter(seeds::includes).forEach(i -> candidates.add(i.id()));
        java.util.Set<String> blockOnly = PriceGenerator.generate(seeds.seeds(),
                restrictInputs(graph.conversions(), candidates)).prices().keySet();

        // Group tradeable items by first category for a readable file.
        Comparator<AssetConversions.ItemInfo> order = Comparator
                .comparing((AssetConversions.ItemInfo i) -> i.categories().isEmpty() ? "~" : i.categories().get(0))
                .thenComparing(AssetConversions.ItemInfo::id);
        List<AssetConversions.ItemInfo> tradeable = new ArrayList<>();
        List<AssetConversions.ItemInfo> zeroPriced = new ArrayList<>();
        List<String> needsNonBlocks = new ArrayList<>();
        for (AssetConversions.ItemInfo item : graph.items().values()) {
            PriceGenerator.Price price = result.prices().get(item.id());
            if (price == null || !seeds.includes(item)) {
                continue;
            }
            if (!blockOnly.contains(item.id())) {
                needsNonBlocks.add(item.id());
                continue;
            }
            (price.value() > 0 ? tradeable : zeroPriced).add(item);
        }
        java.util.Collections.sort(needsNonBlocks);
        tradeable.sort(order);

        // Unlocks: an item needs, for each seed requirement of its cheapest path, one matching unlock
        // (a requirement may offer alternatives, e.g. any hardwood log).
        Map<String, List<java.util.Set<String>>> requires = new java.util.HashMap<>();
        Map<String, List<String>> unlockContents = new java.util.LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        if (seeds.unlocksEnabled()) {
            Map<String, List<java.util.Set<String>>> deps = PriceGenerator.seedDependencies(result);
            for (String seed : seeds.seeds().keySet()) { // seed order, so unlocks are listed like the seeds
                unlockContents.putIfAbsent(seeds.unlockIdFor(seed), new ArrayList<>());
            }
            for (var it = tradeable.iterator(); it.hasNext(); ) {
                AssetConversions.ItemInfo item = it.next();
                if (deps.getOrDefault(item.id(), List.of()).isEmpty()) {
                    // Never let "couldn't work it out" mean "needs no unlock".
                    unresolved.add(item.id());
                    it.remove();
                    continue;
                }
                List<java.util.Set<String>> needed = new ArrayList<>();
                for (java.util.Set<String> anyOfSeeds : deps.getOrDefault(item.id(), List.of())) {
                    java.util.Set<String> anyOfUnlocks = new java.util.TreeSet<>();
                    anyOfSeeds.forEach(seed -> anyOfUnlocks.add(seeds.unlockIdFor(seed)));
                    needed.add(anyOfUnlocks);
                }
                needed = PriceGenerator.simplify(needed);
                requires.put(item.id(), needed);
                needed.stream().flatMap(java.util.Set::stream).distinct()
                        .forEach(u -> unlockContents.get(u).add(item.id()));
            }
            unlockContents.values().removeIf(List::isEmpty); // seeds whose items were all excluded
        }

        // Blocks we'd include but couldn't price: either nothing produces them (raw or creative-only:
        // add a seed if players can gather them), or their inputs are unpriced.
        Set<String> produced = new HashSet<>();
        graph.conversions().forEach(c -> produced.addAll(c.outputs().keySet()));
        Map<String, List<String>> unpricedRaw = new TreeMap<>();
        Map<String, List<String>> unpricedOther = new TreeMap<>();
        for (AssetConversions.ItemInfo item : graph.items().values()) {
            if (result.prices().containsKey(item.id()) || !seeds.includes(item)) {
                continue;
            }
            String category = item.categories().isEmpty() ? "(no category)" : item.categories().get(0);
            (produced.contains(item.id()) ? unpricedOther : unpricedRaw)
                    .computeIfAbsent(category, k -> new ArrayList<>()).add(item.id());
        }

        Path dir = Files.createDirectories(dataDirectory.resolve(OUTPUT_DIR));
        Path output = dir.resolve(GENERATED_FILE);
        Files.writeString(output, toJson(seeds, tradeable, result, requires, unlockContents.keySet()), StandardCharsets.UTF_8);

        StringBuilder report = new StringBuilder();
        report.append("Generated ").append(tradeable.size()).append(" tradeable items from ")
                .append(seeds.seeds().size()).append(" seeds.\n");
        if (!result.converged()) {
            report.append("WARNING: price calculation did not converge; results may be incomplete.\n");
        }
        section(report, "Seeds lowered because a cheaper way to make the item exists", result.loweredSeeds());
        List<String> lines = new ArrayList<>();
        for (AssetConversions.ItemInfo item : tradeable) {
            PriceGenerator.Price p = result.prices().get(item.id());
            lines.add(String.format("%-48s %8d  <- %s", item.id(), p.value(), p.reason()));
        }
        section(report, "Tradeable items (price <- what set it)", lines);
        if (seeds.unlocksEnabled()) {
            List<String> unlockLines = new ArrayList<>();
            unlockContents.forEach((id, items) -> unlockLines.add(String.format("%s (donate %d of %s): %d items: %s",
                    id, seeds.amountFor(id), String.join("/", seeds.acceptedBy(id)), items.size(), String.join(" ", items))));
            section(report, "Unlocks (items listed under an unlock need it, or one of its alternatives)", unlockLines);
            List<String> choiceLines = new ArrayList<>();
            for (AssetConversions.ItemInfo item : tradeable) {
                List<java.util.Set<String>> needed = requires.getOrDefault(item.id(), List.of());
                if (needed.stream().anyMatch(anyOf -> anyOf.size() > 1)) {
                    choiceLines.add(item.id() + ": " + needed.stream()
                            .map(anyOf -> anyOf.size() == 1 ? anyOf.iterator().next() : "any of " + anyOf)
                            .collect(java.util.stream.Collectors.joining(" AND ")));
                }
            }
            section(report, "Items whose requirements offer alternatives", choiceLines);
            section(report, "Left out: required unlocks could not be determined", unresolved);
        }
        section(report, "Left out: can't be made from tradeable blocks alone (needs sticks, fibre, fabric, ...)", needsNonBlocks);
        section(report, "Priced at 0 (obtainable for free, left out)", zeroPriced.stream().map(AssetConversions.ItemInfo::id).toList());
        report.append("\n== Unpriced blocks that nothing produces: raw materials (add a seed if players can gather them) or creative-only\n");
        unpricedRaw.forEach((cat, ids) -> report.append("  [").append(cat).append("] ").append(String.join(" ", ids)).append('\n'));
        report.append("\n== Unpriced blocks whose recipes need unpriced inputs (e.g. fabric, metal, plants)\n");
        unpricedOther.forEach((cat, ids) -> report.append("  [").append(cat).append("] ").append(String.join(" ", ids)).append('\n'));
        Path reportPath = dir.resolve(GENERATED_REPORT);
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);

        return new GenerateSummary(tradeable.size(), result.loweredSeeds().size(),
                unpricedRaw.values().stream().mapToInt(List::size).sum(),
                unpricedOther.values().stream().mapToInt(List::size).sum(),
                zeroPriced.size(), result.converged(), output, reportPath);
    }

    /**
     * First launch: generates the price list from this server's assets (including other mods'
     * recipes) and installs it as {@code target}. Never overwrites an existing file.
     */
    public GenerateSummary generateInitialConfig(Path target) throws IOException, TradeConfigException {
        GenerateSummary summary = generate();
        Files.copy(summary.output(), target); // throws if target exists: never clobber an admin's file
        return summary;
    }

    public CheckSummary check(TradeConfig config) throws IOException {
        List<LoopChecker.Issue> issues = LoopChecker.check(config, AssetConversions.build().conversions());
        issues = issues.stream().sorted(Comparator.comparing(LoopChecker.Issue::isLoop).reversed()).toList();
        StringBuilder report = new StringBuilder();
        int loops = 0;
        for (LoopChecker.Issue issue : issues) {
            loops += issue.isLoop() ? 1 : 0;
            report.append(issue.isLoop() ? "LOOP        " : "SPREAD ONLY ")
                    .append(issue.conversion().source()).append(": sells for ").append(issue.revenue())
                    .append(", inputs cost ").append(issue.buyCost().stripTrailingZeros().toPlainString())
                    .append(" to buy (").append(issue.baseCost().stripTrailingZeros().toPlainString())
                    .append(" at base price); outputs ").append(issue.conversion().outputs()).append('\n');
        }
        if (issues.isEmpty()) {
            report.append("No craft-and-sell loops found.\n");
        }
        Path dir = Files.createDirectories(dataDirectory.resolve(OUTPUT_DIR));
        Path reportPath = dir.resolve(CHECK_REPORT);
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        return new CheckSummary(loops, issues.size() - loops, reportPath);
    }

    /** Loops in {@code config} that involve the item (as an input or output), e.g. after a manual edit. */
    public List<LoopChecker.Issue> loopsInvolving(TradeConfig config, String itemId) {
        return LoopChecker.check(config, AssetConversions.build().conversions()).stream()
                .filter(issue -> issue.conversion().outputs().containsKey(itemId)
                        || issue.conversion().inputs().stream().anyMatch(in -> in.unitsPerItem().containsKey(itemId)))
                .toList();
    }

    private String readSeeds() throws IOException {
        Path file = dataDirectory.resolve(SEEDS_FILE);
        if (!Files.exists(file)) {
            try (InputStream in = PriceGenerationService.class.getResourceAsStream("/" + SEEDS_FILE)) {
                if (in == null) {
                    throw new IOException("Bundled default " + SEEDS_FILE + " is missing from the plugin jar");
                }
                Files.createDirectories(dataDirectory);
                Files.copy(in, file);
            }
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String toJson(PriceSeeds seeds, List<AssetConversions.ItemInfo> items, PriceGenerator.Result result,
                                 Map<String, List<java.util.Set<String>>> requires, java.util.Set<String> unlockIds)
            throws IOException {
        StringWriter out = new StringWriter();
        try (JsonWriter json = new JsonWriter(out)) {
            json.setIndent("  ");
            json.beginObject();
            json.name("spread").jsonValue(seeds.spread().toPlainString());
            if (!unlockIds.isEmpty()) {
                json.name("unlocks").beginObject();
                for (String id : unlockIds) {
                    json.name(id).beginObject();
                    json.name("amount").value(seeds.amountFor(id));
                    json.name("accepts").beginArray();
                    for (String accepted : seeds.acceptedBy(id)) {
                        json.value(accepted);
                    }
                    json.endArray();
                    json.endObject();
                }
                json.endObject();
            }
            json.name("items").beginObject();
            for (AssetConversions.ItemInfo item : items) {
                long price = result.prices().get(item.id()).value();
                List<java.util.Set<String>> needed = requires.getOrDefault(item.id(), List.of());
                if (needed.isEmpty()) {
                    json.name(item.id()).value(price);
                } else {
                    json.name(item.id()).beginObject();
                    json.name("price").value(price);
                    json.name("requires").beginArray();
                    for (java.util.Set<String> anyOf : needed) {
                        if (anyOf.size() == 1) {
                            json.value(anyOf.iterator().next()); // a plain string: this unlock is needed
                        } else {
                            json.beginArray(); // a list: any one of these
                            for (String unlock : anyOf) {
                                json.value(unlock);
                            }
                            json.endArray();
                        }
                    }
                    json.endArray();
                    json.endObject();
                }
            }
            json.endObject();
            json.endObject();
        }
        return out + "\n";
    }

    /** Conversions whose inputs may only use the given items (others removed; empty inputs drop the conversion). */
    static List<Conversion> restrictInputs(List<Conversion> conversions, java.util.Set<String> allowed) {
        List<Conversion> restricted = new ArrayList<>();
        for (Conversion conversion : conversions) {
            List<Conversion.Ingredient> inputs = new ArrayList<>();
            for (Conversion.Ingredient ingredient : conversion.inputs()) {
                Map<String, Integer> kept = new java.util.HashMap<>(ingredient.unitsPerItem());
                kept.keySet().retainAll(allowed);
                if (kept.isEmpty()) {
                    inputs = null;
                    break;
                }
                inputs.add(new Conversion.Ingredient(kept, ingredient.quantity()));
            }
            if (inputs != null) {
                restricted.add(new Conversion(conversion.source(), inputs, conversion.outputs()));
            }
        }
        return restricted;
    }

    private static void section(StringBuilder report, String title, List<String> lines) {
        report.append("\n== ").append(title).append(" (").append(lines.size()).append(")\n");
        lines.forEach(line -> report.append("  ").append(line).append('\n'));
    }
}
