package io.github.obut.tradestation.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.config.TradeConfig;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.pricing.Pricing;
import io.github.obut.tradestation.text.Texts;

import javax.annotation.Nonnull;
import java.util.OptionalLong;
import java.util.logging.Level;

/**
 * Dev-only {@code /sell}: sells the stack in the active hotbar slot if it is tradeable and logs its value.
 * There is no station (and so no balance) yet, so the items are simply removed.
 * <p>
 * Only operators can run it: the server gives every plugin command a permission node
 * ({@code obut.tradestation.command.sell}) that normal players don't have.
 */
public class SellCommand extends AbstractPlayerCommand {

    private final TradeConfigManager configManager;
    private final HytaleLogger logger;

    public SellCommand(TradeConfigManager configManager, HytaleLogger logger) {
        super("sell", Texts.key("commands.sell.desc"));
        this.configManager = configManager;
        this.logger = logger;
    }

    // AbstractPlayerCommand runs this on the player's world thread, so reading and changing
    // the player's components here is safe.
    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // getItemInHand() can also return the tool-belt item; only hotbar items can be sold.
        InventoryComponent.Tool tools = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        if (tools != null && tools.isUsingToolsItem()) {
            context.sendMessage(Texts.of("commands.sell.selectHotbar"));
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        byte slot = hotbar == null ? InventoryComponent.INACTIVE_SLOT_INDEX : hotbar.getActiveSlot();
        ItemStack stack = slot == InventoryComponent.INACTIVE_SLOT_INDEX ? null : hotbar.getActiveItem();
        if (ItemStack.isEmpty(stack)) {
            context.sendMessage(Texts.of("commands.sell.nothingHeld"));
            return;
        }

        String itemId = stack.getItemId();
        TradeConfig config = configManager.current();
        OptionalLong basePrice = config.basePrice(itemId);
        if (basePrice.isEmpty()) {
            context.sendMessage(Texts.of("trade.notTradeable").param("item", Texts.itemNameText(itemId, playerRef.getLanguage())));
            return;
        }

        int quantity = stack.getQuantity();
        long value;
        try {
            value = Pricing.sellValue(basePrice.getAsLong(), quantity);
        } catch (ArithmeticException e) {
            context.sendMessage(Texts.of("commands.sell.tooValuable"));
            return;
        }

        // Remove exactly the stack we priced: with allOrNothing=true the server only removes items
        // if the slot still holds a matching stack with at least that many items, atomically.
        ItemStackSlotTransaction removal = hotbar.getInventory()
                .removeItemStackFromSlot(slot, stack, quantity, true, true);
        if (!removal.succeeded()) {
            context.sendMessage(Texts.of("commands.sell.inventoryChanged"));
            return;
        }

        logger.at(Level.INFO).log("[sell] %s sold %dx %s for %d (base %d)",
                playerRef.getUsername(), quantity, itemId, value, basePrice.getAsLong());
        context.sendMessage(Texts.of("commands.sell.sold")
                .param("count", quantity).param("item", Texts.itemNameText(itemId, playerRef.getLanguage())).param("value", value));
    }
}
