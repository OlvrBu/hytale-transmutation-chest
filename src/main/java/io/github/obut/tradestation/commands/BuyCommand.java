package io.github.obut.tradestation.commands;

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
import io.github.obut.tradestation.station.StationAccess;
import io.github.obut.tradestation.station.TradeService;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;
import org.joml.Vector3i;

import javax.annotation.Nonnull;

/**
 * {@code /tradestation buy <item> [quantity]}: buys from the Trade Station you're looking at, paid from
 * its balance. Same rules and checks as the station page; refuses without a station.
 * Operators only (automatic permission node {@code obut.tradestation.command.tradestation.buy}).
 */
public class BuyCommand extends AbstractPlayerCommand {

    private final TradeService tradeService;
    private final StationViewers viewers;
    // ITEM_ASSET parses an item ID (with tab completion) into the Item asset.
    private final RequiredArg<Item> itemArg =
            withRequiredArg("item", Texts.key("commands.tradestation.buy.item.desc"), ArgTypes.ITEM_ASSET);
    private final DefaultArg<Integer> quantityArg =
            withDefaultArg("quantity", Texts.key("commands.tradestation.buy.quantity.desc"), ArgTypes.INTEGER, 1, "1");

    public BuyCommand(TradeService tradeService, StationViewers viewers) {
        super("buy", Texts.key("commands.tradestation.buy.desc"));
        this.tradeService = tradeService;
        this.viewers = viewers;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i station = StationAccess.targetedStation(ref, store, world);
        if (station == null) {
            context.sendMessage(Texts.of("commands.tradestation.noTarget").param("reach", (int) StationAccess.TARGET_REACH));
            return;
        }
        TradeService.Result result = tradeService.buy(world, station, store, ref,
                itemArg.get(context).getId(), quantityArg.get(context));
        context.sendMessage(result.message());
        if (result.success()) {
            viewers.refreshAll(world, station);
        }
    }
}
