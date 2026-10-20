package io.github.obut.tradestation.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.obut.tradestation.config.TradeConfigException;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricegen.PriceGenerationService;
import io.github.obut.tradestation.station.TradeService;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;

import javax.annotation.Nonnull;

/**
 * {@code /tradestation <subcommand>}: admin commands. Run without a subcommand it prints usage.
 * Operators only (the server gives plugin commands a permission node normal players don't have);
 * also works from the server console.
 */
public class TradeStationCommand extends AbstractCommandCollection {

    public TradeStationCommand(TradeConfigManager configManager, TradeService tradeService, StationViewers viewers,
                               PriceGenerationService priceGeneration, HytaleLogger logger) {
        super("tradestation", Texts.key("commands.tradestation.desc"));
        addSubCommand(new Reload(configManager, viewers));
        addSubCommand(new SellCommand(tradeService, viewers));
        addSubCommand(new BuyCommand(tradeService, viewers));
        addSubCommand(new SetBalanceCommand(logger, viewers));
        addSubCommand(new GeneratePrices(priceGeneration));
        addSubCommand(new CheckPrices(priceGeneration, configManager));
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

    /**
     * {@code /tradestation generateprices}: derives prices from price_seeds.json and the server's
     * recipes into generated/tradeables.generated.json (+ report). Doesn't change the active list:
     * review it, copy it over tradeables.json, then /tradestation reload.
     */
    static class GeneratePrices extends CommandBase {

        private final PriceGenerationService service;

        GeneratePrices(PriceGenerationService service) {
            super("generateprices", Texts.key("commands.tradestation.generateprices.desc"));
            this.service = service;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            try {
                PriceGenerationService.GenerateSummary s = service.generate();
                context.sendMessage(Texts.of("commands.tradestation.generateprices.done")
                        .param("count", s.tradeable()).param("lowered", s.loweredSeeds())
                        .param("raw", s.unpricedRaw()).param("other", s.unpricedOther())
                        .param("output", s.output().toString()).param("report", s.report().toString()));
            } catch (TradeConfigException | java.io.IOException e) {
                context.sendMessage(Texts.of("commands.tradestation.generateprices.failed").param("error", String.valueOf(e.getMessage())));
            }
        }
    }

    /** {@code /tradestation checkprices}: looks for craft-and-sell loops in the active price list. */
    static class CheckPrices extends CommandBase {

        private final PriceGenerationService service;
        private final TradeConfigManager configManager;

        CheckPrices(PriceGenerationService service, TradeConfigManager configManager) {
            super("checkprices", Texts.key("commands.tradestation.checkprices.desc"));
            this.service = service;
            this.configManager = configManager;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            try {
                PriceGenerationService.CheckSummary s = service.check(configManager.current());
                context.sendMessage(Texts.of("commands.tradestation.checkprices.done")
                        .param("loops", s.loops()).param("spreadOnly", s.spreadOnly()).param("report", s.report().toString()));
            } catch (java.io.IOException e) {
                context.sendMessage(Texts.of("commands.tradestation.generateprices.failed").param("error", String.valueOf(e.getMessage())));
            }
        }
    }
}
