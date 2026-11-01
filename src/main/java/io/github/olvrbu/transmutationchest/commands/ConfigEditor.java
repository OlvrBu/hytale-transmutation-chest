package io.github.olvrbu.transmutationchest.commands;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import io.github.olvrbu.transmutationchest.config.Requirements;
import io.github.olvrbu.transmutationchest.config.TradeConfig;
import io.github.olvrbu.transmutationchest.config.TradeConfigManager;
import io.github.olvrbu.transmutationchest.pricegen.LoopChecker;
import io.github.olvrbu.transmutationchest.pricegen.PriceGenerationService;
import io.github.olvrbu.transmutationchest.text.Texts;
import io.github.olvrbu.transmutationchest.ui.ChestViewers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Shared by the config-editing commands: validate input, write tradeables.json, reload it (which
 * validates it like a hand-edited file), refresh open chest pages and warn about new loops.
 */
record ConfigEditor(TradeConfigManager configManager, PriceGenerationService priceGeneration, ChestViewers viewers) {

    TradeConfig current() {
        return configManager.current();
    }

    /** Parses a requirement spec and checks every unlock exists; sends an error and returns null if not. */
    List<Set<String>> requirements(CommandContext context, String spec) {
        List<Set<String>> needed;
        try {
            needed = Requirements.parse(spec);
        } catch (IllegalArgumentException e) {
            context.sendMessage(Texts.of("commands.transmutationchest.item.badRequires").param("error", e.getMessage()));
            return null;
        }
        List<String> unknown = new ArrayList<>();
        needed.forEach(anyOf -> anyOf.stream().filter(u -> !current().unlocks().containsKey(u)).forEach(unknown::add));
        if (!unknown.isEmpty()) {
            context.sendMessage(Texts.of("commands.transmutationchest.item.unknownUnlocks").param("unlocks", String.join(", ", unknown)));
            return null;
        }
        return needed;
    }

    /** Parses "A+B+C" into item IDs that exist; sends an error and returns null if one doesn't. */
    List<String> items(CommandContext context, String spec) {
        List<String> ids = new ArrayList<>();
        for (String part : spec.split("[+,]")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (Item.getAssetMap().getAsset(id) == null) {
                context.sendMessage(Texts.of("commands.transmutationchest.unlockdef.unknownItem").param("item", id));
                return null;
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            context.sendMessage(Texts.of("commands.transmutationchest.unlockdef.noItems"));
            return null;
        }
        return ids;
    }

    /**
     * Saves the edited config; on success sends {@code done.get()} (built from the new config) and
     * warns about loops involving {@code itemId} (null: check nothing specific).
     */
    boolean apply(CommandContext context, UnaryOperator<TradeConfig> edit, java.util.function.Function<TradeConfig, com.hypixel.hytale.server.core.Message> done,
                  String itemId) {
        TradeConfigManager.ReloadResult result;
        try {
            result = configManager.save(edit.apply(current()));
        } catch (IOException | RuntimeException e) {
            context.sendMessage(Texts.of("commands.transmutationchest.item.saveFailed").param("error", String.valueOf(e.getMessage())));
            return false;
        }
        if (!result.success()) {
            context.sendMessage(Texts.of("commands.transmutationchest.item.saveFailed").param("error", String.valueOf(result.error())));
            return false;
        }
        context.sendMessage(done.apply(current()));
        viewers.refreshEverywhere();
        if (itemId != null) {
            // Hand-set prices are the one way craft-and-sell loops can get in: say so right away.
            List<LoopChecker.Issue> loops = priceGeneration.loopsInvolving(current(), itemId).stream()
                    .filter(LoopChecker.Issue::isLoop).toList();
            if (!loops.isEmpty()) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.loops").param("count", loops.size())
                        .param("item", itemId).param("example", loops.get(0).conversion().source()));
            }
        }
        return true;
    }

    /** The usual "item: price n, requires x" confirmation. */
    static com.hypixel.hytale.server.core.Message itemSummary(String key, TradeConfig config, String itemId) {
        return Texts.of(key).param("item", itemId).param("price", config.basePrice(itemId).orElse(0))
                .param("requires", Requirements.format(config.requirementsOf(itemId)));
    }
}
