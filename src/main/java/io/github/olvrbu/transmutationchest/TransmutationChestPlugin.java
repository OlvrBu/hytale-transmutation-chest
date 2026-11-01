package io.github.olvrbu.transmutationchest;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.olvrbu.transmutationchest.commands.TransmutationChestCommand;
import io.github.olvrbu.transmutationchest.config.TradeConfigManager;
import io.github.olvrbu.transmutationchest.pricegen.PriceGenerationService;
import io.github.olvrbu.transmutationchest.chest.ChestComponent;
import io.github.olvrbu.transmutationchest.chest.ChestLifecycleSystem;
import io.github.olvrbu.transmutationchest.chest.ChestUseInteraction;
import io.github.olvrbu.transmutationchest.chest.TradeService;
import io.github.olvrbu.transmutationchest.ui.ChestViewers;
import io.github.olvrbu.transmutationchest.ui.RecentItems;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Plugin entry point. The server constructs this class (named by "Main" in manifest.json),
 * then calls setup() -> start() on load and shutdown() on unload.
 */
public class TransmutationChestPlugin extends JavaPlugin {

    private static TransmutationChestPlugin instance;

    private TradeConfigManager configManager;
    private ComponentType<ChunkStore, ChestComponent> chestComponentType;
    private TradeService tradeService;
    private PriceGenerationService priceGeneration;
    private final ChestViewers chestViewers = new ChestViewers();
    private final RecentItems recentItems = new RecentItems();

    public TransmutationChestPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /** For code the server instantiates itself (e.g. interactions built from JSON), which can't be handed the plugin. */
    public static TransmutationChestPlugin get() {
        return instance;
    }

    public ComponentType<ChunkStore, ChestComponent> getChestComponentType() {
        return chestComponentType;
    }

    public TradeConfigManager getConfigManager() {
        return configManager;
    }

    public TradeService getTradeService() {
        return tradeService;
    }

    public ChestViewers getChestViewers() {
        return chestViewers;
    }

    public RecentItems getRecentItems() {
        return recentItems;
    }

    @Override
    protected void setup() {
        // getLogger() is already named after the plugin ("transmutationchest|P"), so log lines are prefixed.
        getLogger().at(Level.INFO).log("Transmutation Chest %s setting up", getManifest().getVersion());

        configManager = new TradeConfigManager(getDataDirectory(), getLogger(),
                itemId -> Item.getAssetMap().getAsset(itemId) != null);
        tradeService = new TradeService(configManager, getLogger());
        // /transmutationchest reload | sell | buy | setbalance | generateprices | checkprices (operators only)
        priceGeneration = new PriceGenerationService(getDataDirectory());
        getCommandRegistry().registerCommand(new TransmutationChestCommand(configManager, tradeService, chestViewers,
                priceGeneration, getLogger()));

        // Block data: register our component under the name the block JSON and saved chunks use,
        // and the system that turns a destroyed chest into an item carrying its balance.
        chestComponentType = getChunkStoreRegistry()
                .registerComponent(ChestComponent.class, ChestComponent.ID, ChestComponent.CODEC);
        getChunkStoreRegistry().registerSystem(new ChestLifecycleSystem(getLogger(), chestViewers));

        // Make "Type": "TransmutationChest_Use" usable in asset JSON. Must happen in setup(), before
        // the server loads the assets that refer to it (our Transmutation_Chest block).
        getCodecRegistry(Interaction.CODEC)
                .register(ChestUseInteraction.TYPE, ChestUseInteraction.class, ChestUseInteraction.CODEC);

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
        getLogger().at(Level.INFO).log("Transmutation Chest started");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("Transmutation Chest shutting down");
    }
}
