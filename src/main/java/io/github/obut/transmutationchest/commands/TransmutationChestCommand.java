package io.github.obut.transmutationchest.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.obut.transmutationchest.config.TradeConfigException;
import io.github.obut.transmutationchest.config.TradeConfigManager;
import io.github.obut.transmutationchest.pricegen.PriceGenerationService;
import io.github.obut.transmutationchest.chest.TradeService;
import io.github.obut.transmutationchest.text.Texts;
import io.github.obut.transmutationchest.ui.ChestViewers;

import javax.annotation.Nonnull;

/**
 * {@code /transmutationchest <subcommand>} (alias {@code /tchest}): admin commands. Run without a subcommand it prints usage.
 * Operators only (the server gives plugin commands a permission node normal players don't have);
 * also works from the server console.
 */
public class TransmutationChestCommand extends AbstractCommandCollection {

    public TransmutationChestCommand(TradeConfigManager configManager, TradeService tradeService, ChestViewers viewers,
                                     PriceGenerationService priceGeneration, HytaleLogger logger) {
        super("transmutationchest", Texts.key("commands.transmutationchest.desc"));
        addAliases("tchest");
        addSubCommand(new Reload(configManager, viewers));
        addSubCommand(new SellCommand(tradeService, viewers));
        addSubCommand(new BuyCommand(tradeService, viewers));
        addSubCommand(new SetBalanceCommand(logger, viewers));
        addSubCommand(new UnlockCommand(false, configManager, viewers, logger));
        addSubCommand(new UnlockCommand(true, configManager, viewers, logger));
        addSubCommand(new GeneratePrices(priceGeneration));
        addSubCommand(new CheckPrices(priceGeneration, configManager));
        ConfigEditor editor = new ConfigEditor(configManager, priceGeneration, viewers);
        addSubCommand(new ItemCommand(editor));
        addSubCommand(new UnlockDefCommand(editor));
        addSubCommand(new Spread(editor));
    }

    /** {@code /transmutationchest reload}: re-reads tradeables.json; a broken file keeps the old config. */
    static class Reload extends CommandBase {

        private final TradeConfigManager configManager;
        private final ChestViewers viewers;

        Reload(TradeConfigManager configManager, ChestViewers viewers) {
            super("reload", Texts.key("commands.transmutationchest.reload.desc"));
            this.configManager = configManager;
            this.viewers = viewers;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            TradeConfigManager.ReloadResult result = configManager.reload();
            if (!result.success()) {
                context.sendMessage(Texts.of("commands.transmutationchest.reload.failed").param("error", result.error()));
                return;
            }
            context.sendMessage(Texts.of("commands.transmutationchest.reload.success")
                    .param("count", result.config().basePrices().size())
                    .param("spread", result.config().spread().toPlainString()));
            if (result.warnings() > 0) {
                context.sendMessage(Texts.of("commands.transmutationchest.reload.warnings").param("count", result.warnings()));
            }
            viewers.refreshEverywhere(); // open pages show new prices, or rebuild if the item list changed
        }
    }

    /**
     * {@code /transmutationchest generateprices}: derives prices from price_seeds.json and the server's
     * recipes into generated/tradeables.generated.json (+ report). Doesn't change the active list:
     * review it, copy it over tradeables.json, then /transmutationchest reload.
     */
    static class GeneratePrices extends CommandBase {

        private final PriceGenerationService service;

        GeneratePrices(PriceGenerationService service) {
            super("generateprices", Texts.key("commands.transmutationchest.generateprices.desc"));
            this.service = service;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            try {
                PriceGenerationService.GenerateSummary s = service.generate();
                context.sendMessage(Texts.of("commands.transmutationchest.generateprices.done")
                        .param("count", s.tradeable()).param("lowered", s.loweredSeeds())
                        .param("raw", s.unpricedRaw()).param("other", s.unpricedOther())
                        .param("output", s.output().toString()).param("report", s.report().toString()));
            } catch (TradeConfigException | java.io.IOException e) {
                context.sendMessage(Texts.of("commands.transmutationchest.generateprices.failed").param("error", String.valueOf(e.getMessage())));
            }
        }
    }

    /** {@code /transmutationchest checkprices}: looks for craft-and-sell loops in the active price list. */
    static class CheckPrices extends CommandBase {

        private final PriceGenerationService service;
        private final TradeConfigManager configManager;

        CheckPrices(PriceGenerationService service, TradeConfigManager configManager) {
            super("checkprices", Texts.key("commands.transmutationchest.checkprices.desc"));
            this.service = service;
            this.configManager = configManager;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            try {
                PriceGenerationService.CheckSummary s = service.check(configManager.current());
                context.sendMessage(Texts.of("commands.transmutationchest.checkprices.done")
                        .param("loops", s.loops()).param("spreadOnly", s.spreadOnly()).param("report", s.report().toString()));
            } catch (java.io.IOException e) {
                context.sendMessage(Texts.of("commands.transmutationchest.generateprices.failed").param("error", String.valueOf(e.getMessage())));
            }
        }
    }

    /** {@code /transmutationchest spread <value>}: buy price = ceil(base x (1 + spread)); 0 = fully reversible trades. */
    static class Spread extends CommandBase {

        private final ConfigEditor editor;
        private final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> valueArg =
                withRequiredArg("value", Texts.key("commands.transmutationchest.spread.arg"),
                        com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes.STRING);

        Spread(ConfigEditor editor) {
            super("spread", Texts.key("commands.transmutationchest.spread.desc"));
            this.editor = editor;
        }

        @Override
        protected void executeSync(@Nonnull CommandContext context) {
            java.math.BigDecimal value;
            try {
                // Read as text so 0.25 stays exactly 0.25 (no floating point).
                value = new java.math.BigDecimal(valueArg.get(context).trim());
            } catch (NumberFormatException e) {
                value = null;
            }
            if (value == null || value.signum() < 0) {
                context.sendMessage(Texts.of("commands.transmutationchest.spread.bad"));
                return;
            }
            java.math.BigDecimal spread = value;
            editor.apply(context, config -> config.withSpread(spread),
                    now -> Texts.of("commands.transmutationchest.spread.done").param("spread", now.spread().toPlainString()), null);
        }
    }
}
