package io.github.obut.tradestation.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.text.Texts;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /tradestation unlockdef list|info|add|remove|amount|accept|unaccept}: edit the unlock
 * definitions in tradeables.json in game (which items count as donations, and how many are needed).
 * Which items need which unlock is set with {@code /tradestation item requires}.
 * Stations store progress by unlock ID, so removing and re-adding an ID keeps progress.
 */
public class UnlockDefCommand extends AbstractCommandCollection {

    UnlockDefCommand(ConfigEditor editor) {
        super("unlockdef", Texts.key("commands.tradestation.unlockdef.desc"));
        addSubCommand(new ListCmd(editor));
        addSubCommand(new Info(editor));
        addSubCommand(new Add(editor));
        addSubCommand(new Remove(editor));
        addSubCommand(new Amount(editor));
        addSubCommand(new Accept(editor, true));
        addSubCommand(new Accept(editor, false));
    }

    static Message summary(TradeConfig config, String unlockId) {
        TradeConfig.Unlock unlock = config.unlocks().get(unlockId);
        return Texts.of("commands.tradestation.unlockdef.info").param("unlock", unlockId)
                .param("amount", unlock.amount()).param("accepts", String.join("+", unlock.accepts()))
                .param("items", config.itemsUsing(unlockId).size());
    }

    private static boolean exists(CommandContext context, ConfigEditor editor, String unlockId) {
        if (!editor.current().unlocks().containsKey(unlockId)) {
            context.sendMessage(Texts.of("unlock.unknown").param("unlock", unlockId));
            return false;
        }
        return true;
    }

    private static boolean positive(CommandContext context, int amount) {
        if (amount <= 0) {
            context.sendMessage(Texts.of("commands.tradestation.unlockdef.badAmount"));
            return false;
        }
        return true;
    }

    static class ListCmd extends CommandBase {
        private final ConfigEditor editor;

        ListCmd(ConfigEditor editor) {
            super("list", Texts.key("commands.tradestation.unlockdef.list.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            TradeConfig config = editor.current();
            context.sendMessage(Texts.of("commands.tradestation.unlockdef.listHeader").param("count", config.unlocks().size()));
            for (String unlockId : config.unlocks().keySet()) {
                context.sendMessage(summary(config, unlockId));
            }
        }
    }

    static class Info extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<String> idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlockdef.id.desc"), ArgTypes.STRING);

        Info(ConfigEditor editor) {
            super("info", Texts.key("commands.tradestation.unlockdef.info.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            if (exists(context, editor, id)) {
                context.sendMessage(summary(editor.current(), id));
            }
        }
    }

    static class Add extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<String> idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlockdef.id.desc"), ArgTypes.STRING);
        private final RequiredArg<Integer> amountArg = withRequiredArg("amount", Texts.key("commands.tradestation.unlockdef.amount.arg"), ArgTypes.INTEGER);
        private final RequiredArg<String> itemsArg = withRequiredArg("items", Texts.key("commands.tradestation.unlockdef.items.arg"), ArgTypes.STRING);

        Add(ConfigEditor editor) {
            super("add", Texts.key("commands.tradestation.unlockdef.add.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            int amount = amountArg.get(context);
            if (editor.current().unlocks().containsKey(id)) {
                context.sendMessage(Texts.of("commands.tradestation.unlockdef.exists").param("unlock", id));
                return;
            }
            if (!id.matches("[A-Za-z0-9_]+")) {
                context.sendMessage(Texts.of("commands.tradestation.unlockdef.badId"));
                return;
            }
            if (!positive(context, amount)) {
                return;
            }
            List<String> items = editor.items(context, itemsArg.get(context));
            if (items != null) {
                editor.apply(context, config -> config.withUnlock(id, new TradeConfig.Unlock(amount, items)),
                        now -> summary(now, id), null);
            }
        }
    }

    static class Remove extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<String> idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlockdef.id.desc"), ArgTypes.STRING);
        private final com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg forceArg =
                withFlagArg("force", Texts.key("commands.tradestation.unlockdef.force.desc"));

        Remove(ConfigEditor editor) {
            super("remove", Texts.key("commands.tradestation.unlockdef.remove.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            if (!exists(context, editor, id)) {
                return;
            }
            List<String> using = editor.current().itemsUsing(id);
            if (!using.isEmpty() && !Boolean.TRUE.equals(forceArg.get(context))) {
                context.sendMessage(Texts.of("commands.tradestation.unlockdef.inUse").param("unlock", id)
                        .param("count", using.size()).param("examples", String.join(", ", using.subList(0, Math.min(5, using.size())))));
                return;
            }
            editor.apply(context, config -> config.withoutUnlock(id),
                    now -> Texts.of("commands.tradestation.unlockdef.removed").param("unlock", id).param("count", using.size()), null);
        }
    }

    static class Amount extends CommandBase {
        private final ConfigEditor editor;
        private final RequiredArg<String> idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlockdef.id.desc"), ArgTypes.STRING);
        private final RequiredArg<Integer> amountArg = withRequiredArg("amount", Texts.key("commands.tradestation.unlockdef.amount.arg"), ArgTypes.INTEGER);

        Amount(ConfigEditor editor) {
            super("amount", Texts.key("commands.tradestation.unlockdef.amount.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            int amount = amountArg.get(context);
            if (!exists(context, editor, id) || !positive(context, amount)) {
                return;
            }
            List<String> accepts = editor.current().unlocks().get(id).accepts();
            editor.apply(context, config -> config.withUnlock(id, new TradeConfig.Unlock(amount, accepts)),
                    now -> summary(now, id), null);
        }
    }

    /** {@code accept <id> <item>} adds an item that counts as a donation; {@code unaccept} removes one. */
    static class Accept extends CommandBase {
        private final ConfigEditor editor;
        private final boolean add;
        private final RequiredArg<String> idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlockdef.id.desc"), ArgTypes.STRING);
        private final RequiredArg<Item> itemArg = withRequiredArg("item", Texts.key("commands.tradestation.item.item.desc"), ArgTypes.ITEM_ASSET);

        Accept(ConfigEditor editor, boolean add) {
            super(add ? "accept" : "unaccept",
                    Texts.key(add ? "commands.tradestation.unlockdef.accept.desc" : "commands.tradestation.unlockdef.unaccept.desc"));
            this.editor = editor;
            this.add = add;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            String id = idArg.get(context);
            String item = itemArg.get(context).getId();
            if (!exists(context, editor, id)) {
                return;
            }
            TradeConfig.Unlock unlock = editor.current().unlocks().get(id);
            List<String> accepts = new ArrayList<>(unlock.accepts());
            if (add ? accepts.contains(item) : !accepts.contains(item)) {
                context.sendMessage(summary(editor.current(), id)); // nothing to change
                return;
            }
            if (add) {
                accepts.add(item);
            } else {
                accepts.remove(item);
                if (accepts.isEmpty()) {
                    context.sendMessage(Texts.of("commands.tradestation.unlockdef.lastItem"));
                    return;
                }
            }
            editor.apply(context, config -> config.withUnlock(id, new TradeConfig.Unlock(unlock.amount(), accepts)),
                    now -> summary(now, id), null);
        }
    }
}
