package io.github.obut.transmutationchest.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.transmutationchest.chest.ChestAccess;
import io.github.obut.transmutationchest.chest.TradeService;
import io.github.obut.transmutationchest.text.Texts;
import io.github.obut.transmutationchest.ui.ChestViewers;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * {@code /transmutationchest sell}: sells the stack in your hand to the Transmutation Chest you're looking at.
 * Same rules and checks as the chest page (it uses {@link TradeService}); refuses without a chest.
 * Operators only (automatic permission node {@code obut.transmutationchest.command.transmutationchest.sell}).
 */
public class SellCommand extends AbstractPlayerCommand {

    private final TradeService tradeService;
    private final ChestViewers viewers;

    public SellCommand(TradeService tradeService, ChestViewers viewers) {
        super("sell", Texts.key("commands.transmutationchest.sell.desc"));
        this.tradeService = tradeService;
        this.viewers = viewers;
    }

    // AbstractPlayerCommand runs this on the player's world thread, like all trades.
    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i chest = ChestAccess.targetedChest(ref, store, world);
        if (chest == null) {
            context.sendMessage(Texts.of("commands.transmutationchest.noTarget").param("reach", (int) ChestAccess.TARGET_REACH));
            return;
        }
        // getItemInHand() can also return the tool-belt item; only hotbar items can be sold.
        InventoryComponent.Tool tools = store.getComponent(ref, InventoryComponent.Tool.getComponentType());
        if (tools != null && tools.isUsingToolsItem()) {
            context.sendMessage(Texts.of("commands.transmutationchest.sell.selectHotbar"));
            return;
        }
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        ItemStack held = hotbar == null || hotbar.getActiveSlot() == InventoryComponent.INACTIVE_SLOT_INDEX
                ? null : hotbar.getActiveItem();
        if (ItemStack.isEmpty(held)) {
            context.sendMessage(Texts.of("commands.transmutationchest.sell.nothingHeld"));
            return;
        }

        // Sells as many of this item as the held stack holds (taken from anywhere in the inventory).
        TradeService.Result result = tradeService.sell(world, chest, store, ref, held.getItemId(), held.getQuantity());
        context.sendMessage(result.message());
        if (result.success()) {
            viewers.refreshAll(world, chest);
        }
    }
}
