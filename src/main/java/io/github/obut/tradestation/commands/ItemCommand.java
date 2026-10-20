package io.github.obut.tradestation.commands;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.obut.tradestation.config.Requirements;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricegen.LoopChecker;
import io.github.obut.tradestation.pricegen.PriceGenerationService;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * {@code /tradestation item info|add|remove|price|requires}: edit tradeables.json in game. Every change
 * is validated, written to the file, reloaded, pushed to open station pages and loop-checked.
 * Operators only; works from the console.
 */
public class ItemCommand extends AbstractCommandCollection {

    public ItemCommand(TradeConfigManager configManager, PriceGenerationService priceGeneration, StationViewers viewers) {
        super("item", Texts.key("commands.tradestation.item.desc"));
        Editor editor = new Editor(configManager, priceGeneration, viewers);
        addSubCommand(new Info(editor));
        addSubCommand(new Add(editor));
        addSubCommand(new Remove(editor));
        addSubCommand(new Price(editor));
        addSubCommand(new Requires(editor));
    }

    /** Shared validate-save-reload-check logic. */
    private record Editor(TradeConfigManager configManager, PriceGenerationService priceGeneration, StationViewers viewers) {

        TradeConfig current() {
            return configManager.current();
        }

        /** Parses a requirement spec and checks every unlock exists; sends an error and returns null if not. */
        List<Set<String>> requirements(CommandContext context, String spec) {
            List<Set<String>> needed;
            try {
                needed = Requirements.parse(spec);
            } catch (IllegalArgumentException e) {
                context.sendMessage(Texts.of("commands.tradestation.item.badRequires").param("error", e.getMessage()));
                return null;
            }
            List<String> unknown = new ArrayList<>();
            needed.forEach(anyOf -> anyOf.stream().filter(u -> !current().unlocks().containsKey(u)).forEach(unknown::add));
            if (!unknown.isEmpty()) {
                context.sendMessage(Texts.of("commands.tradestation.item.unknownUnlocks").param("unlocks", String.join(", ", unknown)));
                return null;
            }
            return needed;
        }

        /** Saves the edited config and reports the result (including loops the change introduced). */
        void apply(CommandContext context, String itemId, UnaryOperator<TradeConfig> edit, String doneKey) {
            TradeConfigManager.ReloadResult result;
            try {
                result = configManager.save(edit.apply(current()));
            } catch (IOException | RuntimeException e) {
                context.sendMessage(Texts.of("commands.tradestation.item.saveFailed").param("error", String.valueOf(e.getMessage())));
                return;
            }
            if (!result.success()) {
                context.sendMessage(Texts.of("commands.tradestation.item.saveFailed").param("error", String.valueOf(result.error())));
                return;
            }
            TradeConfig now = current();
            context.sendMessage(Texts.of(doneKey).param("item", itemId)
                    .param("price", now.basePrice(itemId).orElse(0))
                    .param("requires", Requirements.format(now.requirementsOf(itemId))));
            viewers.refreshEverywhere();
            // Hand-set prices are the one way craft-and-sell loops can get in: say so right away.
            List<LoopChecker.Issue> loops = priceGeneration.loopsInvolving(now, itemId).stream()
                    .filter(LoopChecker.Issue::isLoop).toList();
            if (!loops.isEmpty()) {
                context.sendMessage(Texts.of("commands.tradestation.item.loops").param("count", loops.size())
                        .param("item", itemId).param("example", loops.get(0).conversion().source()));
            }
        }
    }

    static class Info extends CommandBase {
        private final Editor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);

        Info(Editor editor) {
            super("info", Texts.key("commands.tradestation.item.info.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            TradeConfig config = editor.current();
            if (!config.isTradeable(id)) {
                context.sendMessage(Texts.of("commands.tradestation.item.notListed").param("item", id));
                return;
            }
            context.sendMessage(Texts.of("commands.tradestation.item.info").param("item", id)
                    .param("price", config.basePrice(id).getAsLong())
                    .param("requires", Requirements.format(config.requirementsOf(id))));
        }
    }

    static class Add extends CommandBase {
        private final Editor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<Integer> priceArg = withRequiredArg("price", Texts.key("commands.tradestation.item.price.arg"), ArgTypes.INTEGER);
        private final OptionalArg<String> requiresArg = withOptionalArg("requires", Texts.key("commands.tradestation.item.requires.arg"), ArgTypes.STRING);

        Add(Editor editor) {
            super("add", Texts.key("commands.tradestation.item.add.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            int price = priceArg.get(context);
            if (editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.tradestation.item.alreadyListed").param("item", id));
                return;
            }
            if (price <= 0) {
                context.sendMessage(Texts.of("commands.tradestation.item.badPrice"));
                return;
            }
            List<Set<String>> needed = editor.requirements(context, requiresArg.provided(context) ? requiresArg.get(context) : "none");
            if (needed != null) {
                editor.apply(context, id, config -> config.withItem(id, price, needed), "commands.tradestation.item.added");
            }
        }
    }

    static class Remove extends CommandBase {
        private final Editor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);

        Remove(Editor editor) {
            super("remove", Texts.key("commands.tradestation.item.remove.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.tradestation.item.notListed").param("item", id));
                return;
            }
            editor.apply(context, id, config -> config.withoutItem(id), "commands.tradestation.item.removed");
        }
    }

    static class Price extends CommandBase {
        private final Editor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<Integer> priceArg = withRequiredArg("price", Texts.key("commands.tradestation.item.price.arg"), ArgTypes.INTEGER);

        Price(Editor editor) {
            super("price", Texts.key("commands.tradestation.item.price.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            int price = priceArg.get(context);
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.tradestation.item.notListed").param("item", id));
                return;
            }
            if (price <= 0) {
                context.sendMessage(Texts.of("commands.tradestation.item.badPrice"));
                return;
            }
            editor.apply(context, id, config -> config.withItem(id, price, config.requirementsOf(id)),
                    "commands.tradestation.item.updated");
        }
    }

    static class Requires extends CommandBase {
        private final Editor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<String> specArg = withRequiredArg("requires", Texts.key("commands.tradestation.item.requires.arg"), ArgTypes.STRING);

        Requires(Editor editor) {
            super("requires", Texts.key("commands.tradestation.item.requires.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.tradestation.item.notListed").param("item", id));
                return;
            }
            List<Set<String>> needed = editor.requirements(context, specArg.get(context));
            if (needed != null) {
                editor.apply(context, id, config -> config.withItem(id, config.basePrice(id).getAsLong(), needed),
                        "commands.tradestation.item.updated");
            }
        }
    }
}
