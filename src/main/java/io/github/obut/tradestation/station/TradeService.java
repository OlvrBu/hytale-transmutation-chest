package io.github.obut.tradestation.station;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricing.Pricing;
import io.github.obut.tradestation.text.Texts;
import com.hypixel.hytale.server.core.Message;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.OptionalLong;
import java.util.logging.Level;

/**
 * Executes buy and sell transactions between a player and a station.
 * <p>
 * Rules (from CLAUDE.md): everything is re-checked here at the moment of the transaction, never
 * taken from what a UI showed; a trade is all-or-nothing. Callers must run on the world thread,
 * which is also where every other change to this player and station happens, so nothing can
 * change between our checks and our writes.
 */
public final class TradeService {

    /** Outcome shown to the player. */
    public record Result(boolean success, Message message) {
        static Result fail(Message message) {
            return new Result(false, message);
        }
    }

    /** Pass as quantity to sell everything of that item the player has. */
    public static final int ALL = Integer.MAX_VALUE;

    /** How far (in blocks, from the station's centre) a player may be to trade. */
    private static final double MAX_DISTANCE = 8.0;

    private final TradeConfigManager configManager;
    private final HytaleLogger logger;

    public TradeService(TradeConfigManager configManager, HytaleLogger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    /** Player's inventory sections that trades take from and give to. */
    public static CombinedItemContainer inventoryOf(Store<EntityStore> store, Ref<EntityStore> player) {
        return InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_STORAGE_BACKPACK);
    }

    /** How many plain (no metadata, undamaged) items of this type the player has. */
    public static int countOwned(CombinedItemContainer inventory, String itemId) {
        ItemStack plain = new ItemStack(itemId, 1);
        return inventory.countItemStacks(stack -> stack.isStackableWith(plain));
    }

    public Result sell(World world, Vector3i stationPos, Store<EntityStore> store, Ref<EntityStore> player,
                       String itemId, int requested) {
        Checked c = check(world, stationPos, store, player, itemId);
        if (c.error != null) {
            return c.error;
        }
        CombinedItemContainer inventory = inventoryOf(store, player);
        int quantity = Math.min(requested, countOwned(inventory, itemId));
        if (quantity <= 0) {
            return Result.fail(Texts.of("trade.noneToSell").param("item", itemName(store, player, itemId)));
        }
        long value;
        long newBalance;
        try {
            value = Pricing.sellValue(c.basePrice, quantity);
            newBalance = Pricing.credit(c.station.getBalance(), value);
        } catch (ArithmeticException e) {
            return Result.fail(Texts.of("trade.balanceFull"));
        }
        // allOrNothing=true: removes exactly `quantity` matching items, or nothing at all.
        if (!inventory.removeItemStack(new ItemStack(itemId, quantity), true, true).succeeded()) {
            return Result.fail(Texts.of("trade.removeFailed"));
        }
        c.station.setBalance(newBalance);
        StationAccess.markNeedsSaving(c.stationRef);
        log(store, player, "sold", quantity, itemId, value, stationPos, newBalance);
        return new Result(true, Texts.of("trade.sold")
                .param("count", quantity).param("item", itemName(store, player, itemId)).param("value", value));
    }

    public Result buy(World world, Vector3i stationPos, Store<EntityStore> store, Ref<EntityStore> player,
                      String itemId, int quantity) {
        if (quantity <= 0) {
            return Result.fail(Texts.of("trade.invalidQuantity"));
        }
        Checked c = check(world, stationPos, store, player, itemId);
        if (c.error != null) {
            return c.error;
        }
        long cost;
        try {
            cost = Pricing.buyCost(c.basePrice, configManager.current().spread(), quantity);
        } catch (ArithmeticException e) {
            return Result.fail(Texts.of("trade.costOverflow"));
        }
        long newBalance = Pricing.debit(c.station.getBalance(), cost);
        if (newBalance < 0) {
            return Result.fail(Texts.of("trade.insufficientBalance")
                    .param("balance", c.station.getBalance()).param("cost", cost));
        }
        // allOrNothing=true: if the items don't all fit, nothing is added and we don't debit.
        CombinedItemContainer inventory = inventoryOf(store, player);
        if (!inventory.addItemStack(new ItemStack(itemId, quantity), true, false, true).succeeded()) {
            return Result.fail(Texts.of("trade.noRoom"));
        }
        c.station.setBalance(newBalance);
        StationAccess.markNeedsSaving(c.stationRef);
        log(store, player, "bought", quantity, itemId, cost, stationPos, newBalance);
        return new Result(true, Texts.of("trade.bought")
                .param("count", quantity).param("item", itemName(store, player, itemId)).param("cost", cost));
    }

    /** Result of the checks shared by buy and sell: either an error, or the station and price. */
    private record Checked(Result error, Ref<ChunkStore> stationRef, StationComponent station, long basePrice) {
        static Checked error(Message message) {
            return new Checked(Result.fail(message), null, null, 0);
        }
    }

    private Checked check(World world, Vector3i stationPos, Store<EntityStore> store, Ref<EntityStore> player, String itemId) {
        if (store.getExternalData().getWorld() != world) {
            return Checked.error(Texts.of("trade.wrongWorld"));
        }
        TransformComponent transform = store.getComponent(player, TransformComponent.getComponentType());
        Vector3d center = new Vector3d(stationPos.x + 0.5, stationPos.y + 0.5, stationPos.z + 0.5);
        if (transform == null || transform.getPosition().distance(center) > MAX_DISTANCE) {
            return Checked.error(Texts.of("trade.tooFar"));
        }
        Ref<ChunkStore> stationRef = StationAccess.findStation(world, stationPos.x, stationPos.y, stationPos.z);
        if (stationRef == null) {
            return Checked.error(Texts.of("trade.stationGone"));
        }
        TradeConfig config = configManager.current();
        OptionalLong basePrice = config.basePrice(itemId);
        if (basePrice.isEmpty()) {
            return Checked.error(Texts.of("trade.notTradeable").param("item", itemName(store, player, itemId)));
        }
        StationComponent station = stationRef.getStore().getComponent(stationRef, StationComponent.getComponentType());
        return new Checked(null, stationRef, station, basePrice.getAsLong());
    }

    private static String itemName(Store<EntityStore> store, Ref<EntityStore> player, String itemId) {
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        return Texts.itemNameText(itemId, playerRef == null ? null : playerRef.getLanguage());
    }

    private void log(Store<EntityStore> store, Ref<EntityStore> player, String verb, int quantity, String itemId,
                     long amount, Vector3i pos, long newBalance) {
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        logger.at(Level.INFO).log("[trade] %s %s %dx %s for %d at station (%d, %d, %d), balance now %d",
                playerRef == null ? "?" : playerRef.getUsername(), verb, quantity, itemId, amount,
                pos.x, pos.y, pos.z, newBalance);
    }
}
