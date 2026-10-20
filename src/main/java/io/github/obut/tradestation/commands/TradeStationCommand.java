package io.github.obut.tradestation.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;

import javax.annotation.Nonnull;

/**
 * {@code /tradestation <subcommand>}: admin commands. Run without a subcommand it prints usage.
 * Operators only (the server gives plugin commands a permission node normal players don't have);
 * also works from the server console.
 */
public class TradeStationCommand extends AbstractCommandCollection {

    public TradeStationCommand(TradeConfigManager configManager, StationViewers viewers) {
        super("tradestation", Texts.key("commands.tradestation.desc"));
        addSubCommand(new Reload(configManager, viewers));
    }

    /** {@code /tradestation reload}: re-reads tradeables.json; a broken file keeps the old config. */
    static class Reload extends CommandBase {

        private final TradeConfigManager configManager;
        private final StationViewers viewers;

        Reload(TradeConfigManager configManager, StationViewers viewers) {
            super("reload", Texts.key("commands.tradestation.reload.desc"));
            this.configManager = configManager;
            this.viewers = viewers;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            TradeConfigManager.ReloadResult result = configManager.reload();
            if (!result.success()) {
                context.sendMessage(Texts.of("commands.tradestation.reload.failed").param("error", result.error()));
                return;
            }
            context.sendMessage(Texts.of("commands.tradestation.reload.success")
                    .param("count", result.config().basePrices().size())
                    .param("spread", result.config().spread().toPlainString()));
            if (result.warnings() > 0) {
                context.sendMessage(Texts.of("commands.tradestation.reload.warnings").param("count", result.warnings()));
            }
            viewers.refreshEverywhere(); // open pages show new prices, or rebuild if the item list changed
        }
    }
}
