package io.github.obut.tradestation.config;

/**
 * tradeables.json is unusable as a whole. A checked exception (extends {@link Exception}, not
 * {@link RuntimeException}), so the compiler forces callers to handle it, which is the point:
 * a bad config must never crash the server.
 */
public class TradeConfigException extends Exception {

    public TradeConfigException(String message) {
        super(message);
    }

    public TradeConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
