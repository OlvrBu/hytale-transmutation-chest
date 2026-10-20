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
import io.github.obut.tradestation.unlock.UnlockProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private static final String SELL_ROW = "Pages/TradeStation/StationSellRow.ui";
    private static final String BUY_ROW = "Pages/TradeStation/StationBuyRow.ui";
    private static final String UNLOCK_ROW = "Pages/TradeStation/StationUnlockRow.ui";

    /** Tabs; {@code id} matches the TabButton Ids in StationPage.ui. */
    enum Tab {
        SELL("Sell", "ui.titleSell"), BUY("Buy", "ui.titleBuy"), UNLOCK("Unlock", "ui.titleUnlock");

        final String id;
        final String titleKey;

        Tab(String id, String titleKey) {
            this.id = id;
            this.titleKey = titleKey;
        }

        static Tab byId(String id) {
            for (Tab tab : values()) {
                if (tab.id.equals(id)) {
                    return tab;
                }
            }
            return null;
        }
    }

    /** What the client sends back when a button is pressed. Values are strings we bound in build(). */
    public static class Event {
        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action).add()
                .append(new KeyedCodec<>("Item", Codec.STRING), (e, s) -> e.itemId = s, e -> e.itemId).add()
                .append(new KeyedCodec<>("Quantity", Codec.STRING), (e, s) -> e.quantity = s, e -> e.quantity).add()
                // "@" keys hold a UI element's current value, read by the client when the event fires.
                .append(new KeyedCodec<>("@Search", Codec.STRING), (e, s) -> e.search = s, e -> e.search).add()
                .append(new KeyedCodec<>("@Tab", Codec.STRING), (e, s) -> e.tab = s, e -> e.tab).add()
                .append(new KeyedCodec<>("@HideUnlocked", Codec.BOOLEAN), (e, b) -> e.hideUnlocked = b, e -> e.hideUnlocked).add()
                .append(new KeyedCodec<>("@CanDonate", Codec.BOOLEAN), (e, b) -> e.canDonate = b, e -> e.canDonate).add()
                .build();

        String action;
        String itemId;
        String quantity;
        String search;
        String tab;
        // Boxed: null when the event didn't come from that checkbox.
        Boolean hideUnlocked;
        Boolean canDonate;
    }

    private final World world;
    private final Vector3i stationPos;
    private final TradeConfigManager configManager;
    private final TradeService tradeService;
    private final StationViewers viewers;

    /** Rows per page: the client freezes noticeably when sent hundreds of rows at once. */
    static final int PAGE_SIZE = 30;

    /** All tradeable item IDs (config order) and their translated names, for filtering. */
    private List<String> allItems = List.of();
    private List<String> displayNames = List.of();
    private ItemFilter filter = ItemFilter.NONE;
    private Tab tab = Tab.SELL;
    /** Unlock-tab checkboxes; on by default (must match "@Checked = true" in StationPage.ui). */
    private boolean hideUnlocked = true;
    private boolean canDonateOnly = true;
    /** False until the first render picked the starting tab (or the player picked one). */
    private boolean tabChosen = false;
    private int page = 0;
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
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HideUnlocked #CheckBox",
                EventData.of("@HideUnlocked", "#HideUnlocked #CheckBox.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#CanDonate #CheckBox",
                EventData.of("@CanDonate", "#CanDonate #CheckBox.Value"), false);
        // Built-in tab bar: reports the newly selected tab's Id.
        events.addEventBinding(CustomUIEventBindingType.SelectedTabChanged, "#Tabs",
                EventData.of("@Tab", "#Tabs.SelectedTab"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPage",
                EventData.of("Action", "Page").append("Quantity", "-1"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextPage",
                EventData.of("Action", "Page").append("Quantity", "1"), false);

        render(ui, events, store, ref);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Event event) {
        if (event.tab != null) {
            Tab selected = Tab.byId(event.tab);
            if (selected != null && selected != tab) {
                tab = selected;
                tabChosen = true;
                page = 0;
                refresh();
            }
            return;
        }
        if (event.hideUnlocked != null || event.canDonate != null) {
            if (event.hideUnlocked != null) {
                hideUnlocked = event.hideUnlocked;
            }
            if (event.canDonate != null) {
                canDonateOnly = event.canDonate;
            }
            page = 0;
            refresh();
            return;
        }
        if (event.search != null) {
            filter = filter.withQuery(event.search);
            page = 0;
            refresh();
            return;
        }
        if ("Page".equals(event.action) && event.quantity != null) {
            page += event.quantity.equals("-1") ? -1 : 1; // clamped when rendering
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
            } else if (event.action.equals("Donate")) {
                // For donations the "Item" field carries the unlock ID.
                int quantity = event.quantity.equals("All") ? Integer.MAX_VALUE : Integer.parseInt(event.quantity);
                result = tradeService.donate(world, stationPos, store, ref, event.itemId, quantity);
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

    /** Re-sends the header and the current page's rows (after trades, filter or page changes, reloads). */
    void refresh() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (!isCurrentPageOf(ref)) {
            viewers.remove(world, stationPos, this); // player closed it or opened something else
            return;
        }
        UICommandBuilder ui = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        render(ui, events, ref.getStore(), ref);
        sendUpdate(ui, events, false); // new rows come with new button bindings
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

    /**
     * Filters the whole item list, then (re)creates only the current page's rows. Rows are rebuilt
     * rather than reused so each button's event carries its item ID: a click can never act on a
     * different item than the one shown, even if the page changed in between.
     */
    private void render(UICommandBuilder ui, UIEventBuilder events, Store<EntityStore> store, Ref<EntityStore> ref) {
        TradeConfig config = configManager.current();
        List<String> configured = new ArrayList<>(config.basePrices().keySet());
        if (!configured.equals(allItems)) { // first render, or the config was reloaded
            allItems = configured;
            List<String> names = new ArrayList<>(allItems.size());
            for (String itemId : allItems) {
                Item item = Item.getAssetMap().getAsset(itemId);
                names.add(item == null ? itemId : translate(item.getTranslationKey()));
            }
            displayNames = names;
        }

        Ref<ChunkStore> stationRef = StationAccess.findStation(world, stationPos.x, stationPos.y, stationPos.z);
        StationComponent station = stationRef == null ? null
                : stationRef.getStore().getComponent(stationRef, StationComponent.getComponentType());
        UnlockProgress unlocks = station == null ? new UnlockProgress() : station.getUnlocks();
        long balance = station == null ? 0 : station.getBalance();

        // A station that has unlocked nothing yet opens on the Unlock tab (Sell/Buy would be empty).
        if (!tabChosen) {
            tabChosen = true;
            boolean anyTradeable = false;
            for (String itemId : allItems) {
                if (unlocks.canTrade(itemId, config)) {
                    anyTradeable = true;
                    break;
                }
            }
            if (!anyTradeable && !config.unlocks().isEmpty()) {
                tab = Tab.UNLOCK;
            }
        }

        ui.set("#Balance.Text", Texts.of("ui.balance").param("balance", balance));
        if (status == null) {
            ui.set("#Status.Text", ""); // the client rejects an empty Message here (disconnects!), use a plain string
        } else {
            ui.set("#Status.Text", status);
        }
        ui.set("#Tabs.SelectedTab", tab.id); // keep the client's tab bar in sync (e.g. after a rebuild)
        ui.set("#TabTitle.Text", Texts.of(tab.titleKey));
        ui.set("#UnlockFilters.Visible", tab == Tab.UNLOCK);

        CombinedItemContainer inventory = TradeService.inventoryOf(store, ref);
        ui.clear("#Rows");
        int matches = tab == Tab.UNLOCK
                ? renderUnlocks(ui, events, inventory, config, unlocks)
                : renderItems(ui, events, inventory, config, unlocks, balance);

        int pages = Paging.pageCount(matches, PAGE_SIZE);
        ui.set("#PageLabel.Text", Texts.of("ui.page").param("page", page + 1).param("pages", pages)
                .param("count", matches));
        ui.set("#PrevPage.Disabled", page == 0);
        ui.set("#NextPage.Disabled", page >= pages - 1);
        ui.set("#NoMatches.Visible", matches == 0);
    }

    /** Sell/Buy tabs: items this station has unlocked (Sell: only those you have). Returns the match count. */
    private int renderItems(UICommandBuilder ui, UIEventBuilder events, CombinedItemContainer inventory,
                            TradeConfig config, UnlockProgress unlocks, long balance) {
        ItemFilter filter = this.filter.withOwnedOnly(tab == Tab.SELL);
        List<String> matching = new ArrayList<>();
        boolean anyUnlocked = false;
        for (int i = 0; i < allItems.size(); i++) {
            String itemId = allItems.get(i);
            if (!unlocks.canTrade(itemId, config)) {
                continue;
            }
            anyUnlocked = true;
            // Counting is only needed for the Sell tab's "only items you have" rule.
            int owned = filter.ownedOnly() ? TradeService.countOwned(inventory, itemId) : 1;
            if (filter.matches(itemId, displayNames.get(i), owned)) {
                matching.add(itemId);
            }
        }
        ui.set("#NoMatches.Text", Texts.of(!anyUnlocked ? "ui.nothingUnlocked"
                : !filter.words().isEmpty() ? "ui.noMatches"
                : tab == Tab.SELL ? "ui.nothingToSell" : "ui.nothingToBuy"));

        page = Paging.clamp(page, matching.size(), PAGE_SIZE);
        List<String> shown = Paging.slice(matching, page, PAGE_SIZE);
        for (int i = 0; i < shown.size(); i++) {
            String itemId = shown.get(i);
            String row = "#Rows[" + i + "]";
            long base = config.basePrice(itemId).orElse(0);
            long buyPrice = base > 0 ? Pricing.buyPrice(base, config.spread()) : 0;
            int owned = TradeService.countOwned(inventory, itemId);
            int stack = buyStackSize(itemId);

            ui.append("#Rows", tab == Tab.SELL ? SELL_ROW : BUY_ROW);
            ui.set(row + " #Icon.ItemId", itemId);
            ui.set(row + " #Name.Text", Texts.itemName(itemId));
            ui.set(row + " #Have.Text", Texts.of("ui.have").param("count", owned));
            // Buttons are convenience only: TradeService re-checks everything when one is pressed.
            if (tab == Tab.SELL) {
                ui.set(row + " #Price.Text", Texts.of("ui.sellPrice").param("price", base));
                bind(events, row + " #Sell1", "Sell", itemId, "1");
                bind(events, row + " #SellAll", "Sell", itemId, "All");
            } else {
                ui.set(row + " #Price.Text", Texts.of("ui.buyPrice").param("price", buyPrice));
                ui.set(row + " #BuyStack.Text", Texts.of("ui.buyStack").param("count", stack));
                ui.set(row + " #Buy1.Disabled", buyPrice > balance);
                ui.set(row + " #BuyStack.Disabled", !canAfford(balance, base, config, stack));
                bind(events, row + " #Buy1", "Buy", itemId, "1");
                bind(events, row + " #BuyStack", "Buy", itemId, String.valueOf(stack));
            }
        }
        return matching.size();
    }

    /** Unlock tab: every configured unlock with progress and donate buttons. Returns the match count. */
    private int renderUnlocks(UICommandBuilder ui, UIEventBuilder events, CombinedItemContainer inventory,
                              TradeConfig config, UnlockProgress unlocks) {
        // How many items each unlock is needed for, for the "unlocks N items" line.
        Map<String, Integer> itemCounts = new HashMap<>();
        config.requirements().values().forEach(needed -> needed.stream().flatMap(Set::stream).distinct()
                .forEach(u -> itemCounts.merge(u, 1, Integer::sum)));

        List<String> matching = new ArrayList<>();
        Map<String, Integer> owned = new HashMap<>();
        for (Map.Entry<String, TradeConfig.Unlock> entry : config.unlocks().entrySet()) {
            String unlockId = entry.getKey();
            boolean done = unlocks.isUnlocked(unlockId, config);
            if (done && hideUnlocked) {
                continue;
            }
            int have = 0;
            for (String accepted : entry.getValue().accepts()) {
                have += TradeService.countOwned(inventory, accepted);
            }
            owned.put(unlockId, have);
            // "Only what I can donate": still open, and you have something that counts towards it.
            if (canDonateOnly && (done || have == 0)) {
                continue;
            }
            String name = Texts.unlockNameText(unlockId, playerRef.getLanguage());
            if (filter.withOwnedOnly(false).matches(unlockId, name, 1)) {
                matching.add(unlockId);
            }
        }
        ui.set("#NoMatches.Text", Texts.of(config.unlocks().isEmpty() ? "ui.noUnlocks"
                : !filter.words().isEmpty() ? "ui.noMatches"
                : canDonateOnly ? "ui.nothingToDonate"
                : "ui.allUnlocked"));

        page = Paging.clamp(page, matching.size(), PAGE_SIZE);
        List<String> shown = Paging.slice(matching, page, PAGE_SIZE);
        for (int i = 0; i < shown.size(); i++) {
            String unlockId = shown.get(i);
            TradeConfig.Unlock unlock = config.unlocks().get(unlockId);
            String row = "#Rows[" + i + "]";
            boolean done = unlocks.isUnlocked(unlockId, config);
            int have = owned.get(unlockId);

            ui.append("#Rows", UNLOCK_ROW);
            ui.set(row + " #Icon.ItemId", unlock.accepts().get(0));
            ui.set(row + " #Name.Text", Texts.unlockName(unlockId));
            ui.set(row + " #Details.Text", Texts.of("ui.unlockDetails")
                    .param("items", itemCounts.getOrDefault(unlockId, 0)).param("have", have));
            ui.set(row + " #Progress.Text", done ? Texts.of("ui.progressDone")
                    : Texts.of("ui.progress").param("donated", unlocks.donatedTo(unlockId)).param("amount", unlock.amount()));
            ui.set(row + " #DonateButtons.Visible", !done);
            ui.set(row + " #UnlockedLabel.Visible", done);
            if (!done) {
                ui.set(row + " #Donate1.Disabled", have < 1);
                ui.set(row + " #DonateAll.Disabled", have < 1);
                bind(events, row + " #Donate1", "Donate", unlockId, "1");
                bind(events, row + " #DonateAll", "Donate", unlockId, "All");
            }
        }
        return matching.size();
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

    private static void bind(UIEventBuilder events, String selector, String action, String itemId, String quantity) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action).append("Item", itemId).append("Quantity", quantity), false);
    }
}
