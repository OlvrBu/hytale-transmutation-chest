package io.github.olvrbu.transmutationchest.text;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

/**
 * Player-facing text. All of it lives in Server/Languages/&lt;language&gt;/transmutationchest.lang; the file
 * name becomes the key prefix, so the line {@code trade.sold = ...} is the key
 * {@code transmutationchest.trade.sold}. The client translates keys into the player's language, so adding
 * a language only needs another .lang file, no code.
 * <p>
 * Placeholders use ICU syntax, e.g. {@code {count, number}} or plurals; fill them with
 * {@link Message#param}.
 */
public final class Texts {

    public static final String PREFIX = "transmutationchest.";

    private Texts() {
    }

    /** Translated message for a key from transmutationchest.lang (without the "transmutationchest." prefix). */
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

    /**
     * Display name of an unlock: an unlock named after an item (a single seed) uses that item's name;
     * a group (e.g. "Clay") uses the key {@code unlocks.<id>} from transmutationchest.lang.
     */
    public static Message unlockName(String unlockId) {
        if (Item.getAssetMap().getAsset(unlockId) != null) {
            return itemName(unlockId);
        }
        // Unlocks made by command have no translation: show "Soft_Wood" as "Soft Wood" rather than a raw key.
        if (I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, PREFIX + "unlocks." + unlockId) == null) {
            return Message.raw(unlockId.replace('_', ' '));
        }
        return of("unlocks." + unlockId);
    }

    /** {@link #unlockName} as server-translated plain text, for message parameters. */
    public static String unlockNameText(String unlockId, String language) {
        if (Item.getAssetMap().getAsset(unlockId) != null) {
            return itemNameText(unlockId, language);
        }
        String name = I18nModule.get().getMessage(language, PREFIX + "unlocks." + unlockId);
        return name == null ? unlockId.replace('_', ' ') : name;
    }
}
