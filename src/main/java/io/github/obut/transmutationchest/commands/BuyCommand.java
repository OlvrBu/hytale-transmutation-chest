package io.github.obut.transmutationchest.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
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
 * {@code /transmutationchest buy <item> [quantity]}: buys from the Transmutation Chest you're looking at, paid from
 * its balance. Same rules and checks as the chest page; refuses without a chest.
 * Operators only (automatic permission node {@code obut.transmutationchest.command.transmutationchest.buy}).
 */
public class BuyCommand extends AbstractPlayerCommand {

    private final TradeService tradeService;
    private final ChestViewers viewers;
    // ITEM_ASSET parses an item ID (with tab completion) into the Item asset.
    private final RequiredArg<Item> itemArg =
            withRequiredArg("item", Texts.key("commands.transmutationchest.buy.item.desc"), ArgTypes.ITEM_ASSET);
    private final DefaultArg<Integer> quantityArg =
            withDefaultArg("quantity", Texts.key("commands.transmutationchest.buy.quantity.desc"), ArgTypes.INTEGER, 1, "1");

    public BuyCommand(TradeService tradeService, ChestViewers viewers) {
        super("buy", Texts.key("commands.transmutationchest.buy.desc"));
        this.tradeService = tradeService;
        this.viewers = viewers;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i chest = ChestAccess.targetedChest(ref, store, world);
        if (chest == null) {
            context.sendMessage(Texts.of("commands.transmutationchest.noTarget").param("reach", (int) ChestAccess.TARGET_REACH));
            return;
        }
        TradeService.Result result = tradeService.buy(world, chest, store, ref,
                itemArg.get(context).getId(), quantityArg.get(context));
        context.sendMessage(result.message());
        if (result.success()) {
            viewers.refreshAll(world, chest);
        }
    }
}
