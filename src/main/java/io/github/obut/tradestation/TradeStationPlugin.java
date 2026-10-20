package io.github.obut.tradestation;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import io.github.obut.tradestation.commands.SellCommand;
import io.github.obut.tradestation.config.TradeConfigManager;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Plugin entry point. The server constructs this class (named by "Main" in manifest.json),
 * then calls setup() -> start() on load and shutdown() on unload.
 */
public class TradeStationPlugin extends JavaPlugin {

    private TradeConfigManager configManager;

    public TradeStationPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // getLogger() is already named after the plugin ("tradestation|P"), so log lines are prefixed.
        getLogger().at(Level.INFO).log("Trade Station %s setting up", getManifest().getVersion());

        configManager = new TradeConfigManager(getDataDirectory(), getLogger(),
                itemId -> Item.getAssetMap().getAsset(itemId) != null);
        getCommandRegistry().registerCommand(new SellCommand(configManager, getLogger()));

        // Item assets aren't loaded yet during setup(), and validating item IDs needs them.
        // BootEvent fires once the server has fully started, before any player can join.
        getEventRegistry().register(BootEvent.class, event -> configManager.reload());
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
