package io.github.obut.transmutationchest.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.obut.transmutationchest.chest.ChestAccess;
import io.github.obut.transmutationchest.chest.ChestComponent;
import io.github.obut.transmutationchest.text.Texts;
import io.github.obut.transmutationchest.ui.ChestViewers;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Dev-only {@code /transmutationchest setbalance <amount>}: sets the balance of the chest you're looking at.
 * For testing persistence before the trading UI exists. Operators only (automatic permission node).
 */
public class SetBalanceCommand extends AbstractPlayerCommand {

    private final HytaleLogger logger;
    private final ChestViewers viewers;
    private final RequiredArg<Integer> amountArg =
            withRequiredArg("amount", Texts.key("commands.transmutationchest.setbalance.amount.desc"), ArgTypes.INTEGER);

    public SetBalanceCommand(HytaleLogger logger, ChestViewers viewers) {
        super("setbalance", Texts.key("commands.transmutationchest.setbalance.desc"));
        this.logger = logger;
        this.viewers = viewers;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int amount = amountArg.get(context);
        if (amount < 0) {
            context.sendMessage(Texts.of("commands.transmutationchest.setbalance.negative"));
            return;
        }
        Vector3i target = ChestAccess.targetedChest(ref, store, world);
        Ref<ChunkStore> chestRef = target == null ? null : ChestAccess.findChest(world, target.x, target.y, target.z);
        if (chestRef == null) {
            context.sendMessage(Texts.of("commands.transmutationchest.noTarget").param("reach", (int) ChestAccess.TARGET_REACH));
            return;
        }
        ChestComponent chest = chestRef.getStore().getComponent(chestRef, ChestComponent.getComponentType());
        long old = chest.getBalance();
        chest.setBalance(amount);
        ChestAccess.markNeedsSaving(chestRef);
        viewers.refreshAll(world, target);

        logger.at(Level.INFO).log("[setbalance] %s set chest at (%d, %d, %d) from %d to %d",
                playerRef.getUsername(), target.x, target.y, target.z, old, amount);
        context.sendMessage(Texts.of("commands.transmutationchest.setbalance.changed").param("old", old).param("new", amount));
    }
}
