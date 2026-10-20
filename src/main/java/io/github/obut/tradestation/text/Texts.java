package io.github.obut.tradestation.text;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

/**
 * Player-facing text. All of it lives in Server/Languages/&lt;language&gt;/tradestation.lang; the file
 * name becomes the key prefix, so the line {@code trade.sold = ...} is the key
 * {@code tradestation.trade.sold}. The client translates keys into the player's language, so adding
 * a language only needs another .lang file, no code.
 * <p>
 * Placeholders use ICU syntax, e.g. {@code {count, number}} or plurals; fill them with
 * {@link Message#param}.
 */
public final class Texts {

    public static final String PREFIX = "tradestation.";

    private Texts() {
    }

    /** Translated message for a key from tradestation.lang (without the "tradestation." prefix). */
    public static Message of(String key) {
        return Message.translation(PREFIX + key);
    }

    /** Full key, for places that take a key string (command descriptions). */
    public static String key(String key) {
        return PREFIX + key;
    }

    /**
     * An item's display name translated on the server into {@code language}, as plain text. Use this
     * for message parameters: a nested translated Message as a parameter is not filled in when the
     * text is shown in a custom UI page (it shows "{item}"), plain text always works.
     */
    public static String itemNameText(String itemId, String language) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return itemId;
        }
        String name = I18nModule.get().getMessage(language, item.getTranslationKey());
        return name == null ? itemId : name;
    }

    /** An item's display name as a Message the client translates (fine as a whole Label text). */
    public static Message itemName(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? Message.raw(itemId) : Message.translation(item.getTranslationKey());
    }
}
