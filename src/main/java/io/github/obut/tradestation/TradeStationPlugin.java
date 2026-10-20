package io.github.obut.tradestation;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.obut.tradestation.commands.TradeStationCommand;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricegen.PriceGenerationService;
import io.github.obut.tradestation.station.StationComponent;
import io.github.obut.tradestation.station.StationLifecycleSystem;
import io.github.obut.tradestation.station.StationUseInteraction;
import io.github.obut.tradestation.station.TradeService;
import io.github.obut.tradestation.ui.StationViewers;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Plugin entry point. The server constructs this class (named by "Main" in manifest.json),
 * then calls setup() -> start() on load and shutdown() on unload.
 */
public class TradeStationPlugin extends JavaPlugin {

    private static TradeStationPlugin instance;

    private TradeConfigManager configManager;
    private ComponentType<ChunkStore, StationComponent> stationComponentType;
    private TradeService tradeService;
    private PriceGenerationService priceGeneration;
    private final StationViewers stationViewers = new StationViewers();

    public TradeStationPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /** For code the server instantiates itself (e.g. interactions built from JSON), which can't be handed the plugin. */
    public static TradeStationPlugin get() {
        return instance;
    }

    public ComponentType<ChunkStore, StationComponent> getStationComponentType() {
        return stationComponentType;
    }

    public TradeConfigManager getConfigManager() {
        return configManager;
    }

    public TradeService getTradeService() {
        return tradeService;
    }

    public StationViewers getStationViewers() {
        return stationViewers;
    }

    @Override
    protected void setup() {
        // getLogger() is already named after the plugin ("tradestation|P"), so log lines are prefixed.
        getLogger().at(Level.INFO).log("Trade Station %s setting up", getManifest().getVersion());

        configManager = new TradeConfigManager(getDataDirectory(), getLogger(),
                itemId -> Item.getAssetMap().getAsset(itemId) != null);
        tradeService = new TradeService(configManager, getLogger());
        // /tradestation reload | sell | buy | setbalance | generateprices | checkprices (operators only)
        priceGeneration = new PriceGenerationService(getDataDirectory());
        getCommandRegistry().registerCommand(new TradeStationCommand(configManager, tradeService, stationViewers,
                priceGeneration, getLogger()));

        // Block data: register our component under the name the block JSON and saved chunks use,
        // and the system that turns a destroyed station into an item carrying its balance.
        stationComponentType = getChunkStoreRegistry()
                .registerComponent(StationComponent.class, StationComponent.ID, StationComponent.CODEC);
        getChunkStoreRegistry().registerSystem(new StationLifecycleSystem(getLogger(), stationViewers));

        // Make "Type": "TradeStation_Use" usable in asset JSON. Must happen in setup(), before
        // the server loads the assets that refer to it (our Trade_Station block).
        getCodecRegistry(Interaction.CODEC)
                .register(StationUseInteraction.TYPE, StationUseInteraction.class, StationUseInteraction.CODEC);

        // Item assets aren't loaded yet during setup(), and validating item IDs needs them.
        // BootEvent fires once the server has fully started, before any player can join.
        getEventRegistry().register(BootEvent.class, event -> {
            generateConfigIfMissing();
            configManager.reload();
        });
    }

    /**
     * Fresh server: build tradeables.json from price_seeds.json and this server's recipes, so the
     * list fits the game version and mods actually installed. An existing file is never touched;
     * if generation fails, TradeConfigManager falls back to the default bundled in the jar.
     */
    private void generateConfigIfMissing() {
        if (Files.exists(configManager.file())) {
            return;
        }
        long start = System.nanoTime();
        try {
            PriceGenerationService.GenerateSummary summary = priceGeneration.generateInitialConfig(configManager.file());
            getLogger().at(Level.INFO).log("First launch: generated %s with %d tradeable items in %d ms (report: %s)",
                    configManager.file(), summary.tradeable(), (System.nanoTime() - start) / 1_000_000, summary.report());
        } catch (Exception e) {
            // Any failure here must not stop the server; the bundled default will be used instead.
            getLogger().at(Level.WARNING).log("First launch: could not generate %s (%s); using the bundled default",
                    configManager.file(), e);
        }
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("Trade Station started");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("Trade Station shutting down");
    }
}
