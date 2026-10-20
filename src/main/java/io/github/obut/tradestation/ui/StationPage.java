package io.github.obut.tradestation.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricing.Pricing;
import io.github.obut.tradestation.station.StationAccess;
import io.github.obut.tradestation.station.StationComponent;
import io.github.obut.tradestation.station.TradeService;
import io.github.obut.tradestation.text.Texts;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * The Trade Station screen: balance, plus one row per tradeable item with sell and buy buttons.
 * <p>
 * How Hytale custom pages work: {@link #build} sends the client a layout file
 * (Common/UI/Custom/Pages/TradeStation/StationPage.ui) plus commands that fill in values
 * ({@code set("#Balance.Text", ...)}) and event bindings. When a bound button is pressed, the client
 * sends the bound key/values back; the server decodes them with {@link Event#CODEC} and calls
 * {@link #handleDataEvent} on the world thread. {@code sendUpdate} changes an open page in place.
 * <p>
 * The page only displays; every trade is validated again by {@link TradeService}.
 */
public class StationPage extends InteractiveCustomUIPage<StationPage.Event> {

    private static final String LAYOUT = "Pages/TradeStation/StationPage.ui";
    private static final String ROW = "Pages/TradeStation/StationRow.ui";

    /** What the client sends back when a button is pressed. Values are strings we bound in build(). */
    public static class Event {
        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("Item", Codec.STRING), (e, s) -> e.itemId = s, e -> e.itemId).add()
                .append(new KeyedCodec<>("Quantity", Codec.STRING), (e, s) -> e.quantity = s, e -> e.quantity).add()
                // "@" keys hold a UI element's current value, read by the client when the event fires.
                .append(new KeyedCodec<>("@Search", Codec.STRING), (e, s) -> e.search = s, e -> e.search).add()
                .append(new KeyedCodec<>("@OwnedOnly", Codec.BOOLEAN), (e, b) -> e.ownedOnly = b, e -> e.ownedOnly).add()
                .build();

        String action;
        String itemId;
        String quantity;
        String search;
        Boolean ownedOnly; // boxed: null when this event didn't come from the checkbox
    }

    private final World world;
    private final Vector3i stationPos;
    private final TradeConfigManager configManager;
    private final TradeService tradeService;
    private final StationViewers viewers;

    /** Item IDs in the order their rows were built; a config reload that changes them forces a rebuild. */
    private List<String> shownItems = List.of();
    /** Translated display names, parallel to shownItems, for searching. */
    private List<String> displayNames = List.of();
    private ItemFilter filter = ItemFilter.NONE;
    /** Result of this player's last trade; null until they trade. */
    private Message status;

    public StationPage(PlayerRef playerRef, World world, Vector3i stationPos, TradeConfigManager configManager,
                       TradeService tradeService, StationViewers viewers) {
        // CanDismissOrCloseThroughInteraction: Esc closes it, and so does using another block.
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, Event.CODEC);
        this.world = world;
        this.stationPos = new Vector3i(stationPos);
        this.configManager = configManager;
        this.tradeService = tradeService;
        this.viewers = viewers;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        viewers.add(world, stationPos, this);
        ui.append(LAYOUT);

        // Filters: the client sends the field's / checkbox's new value whenever it changes.
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#SearchInput",
                EventData.of("@Search", "#SearchInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#OwnedOnly #CheckBox",
                EventData.of("@OwnedOnly", "#OwnedOnly #CheckBox.Value"), false);

        TradeConfig config = configManager.current();
        shownItems = new ArrayList<>(config.basePrices().keySet());
        List<String> names = new ArrayList<>();
        for (int i = 0; i < shownItems.size(); i++) {
            String itemId = shownItems.get(i);
            String row = rowSelector(i);
            ui.append("#Rows", ROW); // becomes #Rows[i]
            ui.set(row + " #Icon.ItemId", itemId);
            Item item = Item.getAssetMap().getAsset(itemId);
            ui.set(row + " #Name.Text", Texts.itemName(itemId));
            names.add(item == null ? itemId : translate(item.getTranslationKey()));
            ui.set(row + " #BuyStack.Text", Texts.of("ui.buyStack").param("count", buyStackSize(itemId)));

            bind(events, row + " #Sell1", "Sell", itemId, "1");
            bind(events, row + " #SellAll", "Sell", itemId, "All");
            bind(events, row + " #Buy1", "Buy", itemId, "1");
            bind(events, row + " #BuyStack", "Buy", itemId, String.valueOf(buyStackSize(itemId)));
        }
        displayNames = names;
        fillValues(ui, store, ref, config);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event event) {
        if (event.search != null || event.ownedOnly != null) {
            if (event.search != null) {
                filter = filter.withQuery(event.search);
            }
            if (event.ownedOnly != null) {
                filter = filter.withOwnedOnly(event.ownedOnly);
            }
            refresh();
            return;
        }
        if (event.action == null || event.itemId == null || event.quantity == null) {
            return;
        }
        TradeService.Result result;
        try {
            if (event.action.equals("Sell")) {
                int quantity = event.quantity.equals("All") ? TradeService.ALL : Integer.parseInt(event.quantity);
                result = tradeService.sell(world, stationPos, store, ref, event.itemId, quantity);
            } else if (event.action.equals("Buy")) {
                result = tradeService.buy(world, stationPos, store, ref, event.itemId, Integer.parseInt(event.quantity));
            } else {
                return;
            }
        } catch (NumberFormatException e) {
            return; // not something our page sends
        }
        status = result.message();
        if (result.success()) {
            viewers.refreshAll(world, stationPos); // includes this page
        } else {
            refresh();
        }
    }

    /** Updates balance, counts and button states in place (keeps the scroll position). */
    void refresh() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (!isCurrentPageOf(ref)) {
            viewers.remove(world, stationPos, this); // player closed it or opened something else
            return;
        }
        TradeConfig config = configManager.current();
        if (!shownItems.equals(new ArrayList<>(config.basePrices().keySet()))) {
            rebuild(); // the set of tradeable items changed (config reload)
            return;
        }
        UICommandBuilder ui = new UICommandBuilder();
        fillValues(ui, ref.getStore(), ref, config);
        sendUpdate(ui);
    }

    World world() {
        return world;
    }

    void closeBecauseStationGone() {
        if (isCurrentPageOf(playerRef.getReference())) {
            close();
        }
        viewers.remove(world, stationPos, this);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        viewers.remove(world, stationPos, this);
    }

    /** Sets everything that changes after a trade: balance, status, counts, enabled buttons. */
    private void fillValues(UICommandBuilder ui, Store<EntityStore> store, Ref<EntityStore> ref, TradeConfig config) {
        Ref<ChunkStore> stationRef = StationAccess.findStation(world, stationPos.x, stationPos.y, stationPos.z);
        long balance = stationRef == null ? 0
                : stationRef.getStore().getComponent(stationRef, StationComponent.getComponentType()).getBalance();
        ui.set("#Balance.Text", Texts.of("ui.balance").param("balance", balance));
        if (status == null) {
            ui.set("#Status.Text", ""); // the client rejects an empty Message here (disconnects!), use a plain string
        } else {
            ui.set("#Status.Text", status);
        }

        CombinedItemContainer inventory = TradeService.inventoryOf(store, ref);
        int visible = 0;
        for (int i = 0; i < shownItems.size(); i++) {
            String itemId = shownItems.get(i);
            String row = rowSelector(i);
            long base = config.basePrice(itemId).orElse(0);
            long buyPrice = base > 0 ? Pricing.buyPrice(base, config.spread()) : 0;
            int owned = TradeService.countOwned(inventory, itemId);
            boolean show = filter.matches(itemId, i < displayNames.size() ? displayNames.get(i) : null, owned);
            ui.set(row + ".Visible", show);
            visible += show ? 1 : 0;

            ui.set(row + " #Have.Text", Texts.of("ui.have").param("count", owned));
            ui.set(row + " #SellPrice.Text", Texts.of("ui.sellPrice").param("price", base));
            ui.set(row + " #BuyPrice.Text", Texts.of("ui.buyPrice").param("price", buyPrice));
            // Convenience only: TradeService re-checks everything when a button is pressed.
            ui.set(row + " #Sell1.Disabled", owned < 1);
            ui.set(row + " #SellAll.Disabled", owned < 1);
            ui.set(row + " #Buy1.Disabled", buyPrice > balance);
            ui.set(row + " #BuyStack.Disabled", !canAfford(balance, base, config, buyStackSize(itemId)));
        }
        ui.set("#NoMatches.Visible", visible == 0);
    }

    /** The item name in this player's language, for search; falls back to the key if untranslated. */
    private String translate(String key) {
        String text = I18nModule.get().getMessage(playerRef.getLanguage(), key);
        return text == null ? key : text;
    }

    private boolean isCurrentPageOf(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return false;
        }
        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        return player != null && player.getPageManager().getCustomPage() == this;
    }

    private static boolean canAfford(long balance, long base, TradeConfig config, int quantity) {
        try {
            return base > 0 && Pricing.buyCost(base, config.spread(), quantity) <= balance;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    /** "Buy stack" buys one full stack of the item. */
    private static int buyStackSize(String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        return item == null ? 1 : Math.max(1, item.getMaxStack());
    }

    private static String rowSelector(int index) {
        return "#Rows[" + index + "]";
    }

    private static void bind(UIEventBuilder events, String selector, String action, String itemId, String quantity) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Item", itemId).append("Quantity", quantity), false);
    }
}
