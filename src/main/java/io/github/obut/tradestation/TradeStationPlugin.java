package io.github.obut.tradestation;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Plugin entry point. The server constructs this class (named by "Main" in manifest.json),
 * then calls setup() -> start() on load and shutdown() on unload.
 */
public class TradeStationPlugin extends JavaPlugin {

    public TradeStationPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // getLogger() is already named after the plugin ("tradestation|P"), so log lines are prefixed.
        getLogger().at(Level.INFO).log("Trade Station %s setting up", getManifest().getVersion());
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
