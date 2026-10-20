package io.github.obut.tradestation.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.station.StationAccess;
import io.github.obut.tradestation.station.TradeService;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * {@code /tradestation sell}: sells the stack in your hand to the Trade Station you're looking at.
 * Same rules and checks as the station page (it uses {@link TradeService}); refuses without a station.
 * Operators only (automatic permission node {@code obut.tradestation.command.tradestation.sell}).
 */
public class SellCommand extends AbstractPlayerCommand {

    private final TradeService tradeService;
    private final StationViewers viewers;

    public SellCommand(TradeService tradeService, StationViewers viewers) {
        super("sell", Texts.key("commands.tradestation.sell.desc"));
        this.tradeService = tradeService;
        this.viewers = viewers;
    }

    // AbstractPlayerCommand runs this on the player's world thread, like all trades.
    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i station = StationAccess.targetedStation(ref, store, world);
        if (station == null) {
            context.sendMessage(Texts.of("commands.tradestation.noTarget").param("reach", (int) StationAccess.TARGET_REACH));
            return;
        }
        // getItemInHand() can also return the tool-belt item; only hotbar items can be sold.
        InventoryComponent.Tool tools = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        if (tools != null && tools.isUsingToolsItem()) {
            context.sendMessage(Texts.of("commands.tradestation.sell.selectHotbar"));
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemStack held = hotbar == null || hotbar.getActiveSlot() == InventoryComponent.INACTIVE_SLOT_INDEX
                ? null : hotbar.getActiveItem();
        if (ItemStack.isEmpty(held)) {
            context.sendMessage(Texts.of("commands.tradestation.sell.nothingHeld"));
            return;
        }

        // Sells as many of this item as the held stack holds (taken from anywhere in the inventory).
        TradeService.Result result = tradeService.sell(world, station, store, ref, held.getItemId(), held.getQuantity());
        context.sendMessage(result.message());
        if (result.success()) {
            viewers.refreshAll(world, station);
        }
    }
}
