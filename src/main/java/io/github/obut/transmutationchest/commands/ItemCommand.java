package io.github.obut.transmutationchest.commands;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.obut.transmutationchest.config.Requirements;
import io.github.obut.transmutationchest.config.TradeConfig;
import io.github.obut.transmutationchest.text.Texts;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * {@code /transmutationchest item info|add|remove|price|requires}: edit tradeables.json in game. Every change
 * is validated, written to the file, reloaded, pushed to open chest pages and loop-checked.
 * Operators only; works from the console.
 */
public class ItemCommand extends AbstractCommandCollection {

    ItemCommand(ConfigEditor editor) {
        super("item", Texts.key("commands.transmutationchest.item.desc"));
        addSubCommand(new Info(editor));
        addSubCommand(new Add(editor));
        addSubCommand(new Remove(editor));
        addSubCommand(new Price(editor));
        addSubCommand(new Requires(editor));
    }

    static class Info extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.transmutationchest.item.item.desc"), ArgTypes.ITEM_ASSET);

        Info(ConfigEditor editor) {
            super("info", Texts.key("commands.transmutationchest.item.info.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            TradeConfig config = editor.current();
            if (!config.isTradeable(id)) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.notListed").param("item", id));
                return;
            }
            context.sendMessage(Texts.of("commands.transmutationchest.item.info").param("item", id)
                    .param("price", config.basePrice(id).getAsLong())
                    .param("requires", Requirements.format(config.requirementsOf(id))));
        }
    }

    static class Add extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.transmutationchest.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<Integer> priceArg = withRequiredArg("price", Texts.key("commands.transmutationchest.item.price.arg"), ArgTypes.INTEGER);
        private final OptionalArg<String> requiresArg = withOptionalArg("requires", Texts.key("commands.transmutationchest.item.requires.arg"), ArgTypes.STRING);

        Add(ConfigEditor editor) {
            super("add", Texts.key("commands.transmutationchest.item.add.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            int price = priceArg.get(context);
            if (editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.alreadyListed").param("item", id));
                return;
            }
            if (price <= 0) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.badPrice"));
                return;
            }
            List<Set<String>> needed = editor.requirements(context, requiresArg.provided(context) ? requiresArg.get(context) : "none");
            if (needed != null) {
                editor.apply(context, config -> config.withItem(id, price, needed), now -> ConfigEditor.itemSummary("commands.transmutationchest.item.added", now, id), id);
            }
        }
    }

    static class Remove extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.transmutationchest.item.item.desc"), ArgTypes.ITEM_ASSET);

        Remove(ConfigEditor editor) {
            super("remove", Texts.key("commands.transmutationchest.item.remove.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.notListed").param("item", id));
                return;
            }
            editor.apply(context, config -> config.withoutItem(id), now -> ConfigEditor.itemSummary("commands.transmutationchest.item.removed", now, id), id);
        }
    }

    static class Price extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.transmutationchest.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<Integer> priceArg = withRequiredArg("price", Texts.key("commands.transmutationchest.item.price.arg"), ArgTypes.INTEGER);

        Price(ConfigEditor editor) {
            super("price", Texts.key("commands.transmutationchest.item.price.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            int price = priceArg.get(context);
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.notListed").param("item", id));
                return;
            }
            if (price <= 0) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.badPrice"));
                return;
            }
            editor.apply(context, config -> config.withItem(id, price, config.requirementsOf(id)),
                    now -> ConfigEditor.itemSummary("commands.transmutationchest.item.updated", now, id), id);
        }
    }

    static class Requires extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.transmutationchest.item.item.desc"), ArgTypes.ITEM_ASSET);
        private final RequiredArg<String> specArg = withRequiredArg("requires", Texts.key("commands.transmutationchest.item.requires.arg"), ArgTypes.STRING);

        Requires(ConfigEditor editor) {
            super("requires", Texts.key("commands.transmutationchest.item.requires.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = itemArg.get(context).getId();
            if (!editor.current().isTradeable(id)) {
                context.sendMessage(Texts.of("commands.transmutationchest.item.notListed").param("item", id));
                return;
            }
            List<Set<String>> needed = editor.requirements(context, specArg.get(context));
            if (needed != null) {
                editor.apply(context, config -> config.withItem(id, config.basePrice(id).getAsLong(), needed),
                        now -> ConfigEditor.itemSummary("commands.transmutationchest.item.updated", now, id), id);
            }
        }
    }
}
