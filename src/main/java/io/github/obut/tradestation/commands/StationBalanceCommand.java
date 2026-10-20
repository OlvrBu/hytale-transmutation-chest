package io.github.obut.tradestation.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.obut.tradestation.station.StationAccess;
import io.github.obut.tradestation.station.StationComponent;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Dev-only {@code /stationbalance <amount>}: sets the balance of the station you're looking at.
 * For testing persistence before the trading UI exists. Operators only (automatic permission node).
 */
public class StationBalanceCommand extends AbstractPlayerCommand {

    private static final double MAX_REACH = 8.0;

    private final HytaleLogger logger;
    private final RequiredArg<Integer> amountArg =
            withRequiredArg("amount", "New balance (>= 0)", ArgTypes.INTEGER);

    public StationBalanceCommand(HytaleLogger logger) {
        super("stationbalance", "Dev: set the balance of the Trade Station you're looking at");
        this.logger = logger;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int amount = amountArg.get(context);
        if (amount < 0) {
            context.sendMessage(Message.raw("Balance can't be negative."));
            return;
        }
        Vector3i target = TargetUtil.getTargetBlock(ref, MAX_REACH, store);
        if (target != null) {
            target = StationAccess.resolveMainBlock(world, target.x, target.y, target.z);
        }
        Ref<ChunkStore> stationRef = target == null ? null : StationAccess.findStation(world, target.x, target.y, target.z);
        if (stationRef == null) {
            context.sendMessage(Message.raw("Look at a Trade Station (within " + (int) MAX_REACH + " blocks)."));
            return;
        }
        StationComponent station = stationRef.getStore().getComponent(stationRef, StationComponent.getComponentType());
        long old = station.getBalance();
        station.setBalance(amount);
        StationAccess.markNeedsSaving(stationRef);

        logger.at(Level.INFO).log("[stationbalance] %s set station at (%d, %d, %d) from %d to %d",
                playerRef.getUsername(), target.x, target.y, target.z, old, amount);
        context.sendMessage(Message.raw("Station balance: " + old + " -> " + amount));
    }
}
