package io.github.obut.tradestation.config;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Owns the active {@link TradeConfig}: loads tradeables.json from the plugin's data directory,
 * copying the bundled default there first if the file doesn't exist yet.
 * <p>
 * A failed load never throws and never replaces a working config: it logs the problem and keeps
 * the previous config (initially {@link TradeConfig#EMPTY}, i.e. nothing is tradeable).
 */
public final class TradeConfigManager {

    public static final String FILE_NAME = "tradeables.json";

    private final Path file;
    private final HytaleLogger logger;
    private final Predicate<String> itemIdKnown;

    // volatile: written by whichever thread reloads, read by world threads running commands.
    private volatile TradeConfig current = TradeConfig.EMPTY;

    public TradeConfigManager(Path dataDirectory, HytaleLogger logger, Predicate<String> itemIdKnown) {
        this.file = dataDirectory.resolve(FILE_NAME);
        this.logger = logger;
        this.itemIdKnown = itemIdKnown;
    }

    public TradeConfig current() {
        return current;
    }

    /**
     * Outcome of a reload: on success the new config is active; on failure the previous one stays.
     * {@code error} is set only on failure; {@code warnings} counts skipped entries.
     */
    public record ReloadResult(boolean success, TradeConfig config, int warnings, String error) {
    }

    /** (Re)loads the file. Never throws: on failure, logs and keeps the previous config. */
    public ReloadResult reload() {
        try {
            copyDefaultIfMissing();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            TradeConfigParser.Result result = TradeConfigParser.parse(json, itemIdKnown);
            for (String warning : result.warnings()) {
                logger.at(Level.WARNING).log("%s: %s", FILE_NAME, warning);
            }
            current = result.config();
            logger.at(Level.INFO).log("Loaded %s: %d tradeable item(s), spread %s",
                    file, current.basePrices().size(), current.spread().toPlainString());
            return new ReloadResult(true, current, result.warnings().size(), null);
        } catch (TradeConfigException | IOException e) {
            logger.at(Level.SEVERE).log("Could not load %s, keeping previous config (%d tradeable item(s)): %s",
                    file, current.basePrices().size(), e.getMessage());
            return new ReloadResult(false, current, 0, e.getMessage());
        }
    }

    private void copyDefaultIfMissing() throws IOException {
        if (Files.exists(file)) {
            return;
        }
        // try-with-resources: closes the stream on exit, like a Python "with" block.
        try (InputStream in = TradeConfigManager.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in == null) {
                throw new IOException("Bundled default " + FILE_NAME + " is missing from the plugin jar");
            }
            Files.createDirectories(file.getParent());
            Files.copy(in, file);
        }
        logger.at(Level.INFO).log("Created default %s", file);
    }
}
