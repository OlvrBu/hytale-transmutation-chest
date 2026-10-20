package io.github.obut.tradestation.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.obut.tradestation.config.TradeConfigManager;
import io.github.obut.tradestation.station.StationAccess;
import io.github.obut.tradestation.station.StationComponent;
import io.github.obut.tradestation.text.Texts;
import io.github.obut.tradestation.ui.StationViewers;
import io.github.obut.tradestation.unlock.UnlockProgress;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Admin/testing: {@code /tradestation unlock <id|all>} and {@code /tradestation lock <id|all>} on the
 * Trade Station you're looking at, without donating. Operators only.
 */
public class UnlockCommand extends AbstractPlayerCommand {

    private final boolean lock;
    private final TradeConfigManager configManager;
    private final StationViewers viewers;
    private final HytaleLogger logger;
    private final RequiredArg<String> idArg;

    public UnlockCommand(boolean lock, TradeConfigManager configManager, StationViewers viewers, HytaleLogger logger) {
        super(lock ? "lock" : "unlock", Texts.key(lock ? "commands.tradestation.lock.desc" : "commands.tradestation.unlock.desc"));
        this.lock = lock;
        this.configManager = configManager;
        this.viewers = viewers;
        this.logger = logger;
        this.idArg = withRequiredArg("id", Texts.key("commands.tradestation.unlock.id.desc"), ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i target = StationAccess.targetedStation(ref, store, world);
        Ref<ChunkStore> stationRef = target == null ? null : StationAccess.findStation(world, target.x, target.y, target.z);
        if (stationRef == null) {
            context.sendMessage(Texts.of("commands.tradestation.noTarget").param("reach", (int) StationAccess.TARGET_REACH));
            return;
        }
        String id = idArg.get(context);
        List<String> ids = id.equalsIgnoreCase("all") ? List.copyOf(configManager.current().unlocks().keySet()) : List.of(id);
        if (!id.equalsIgnoreCase("all") && !configManager.current().unlocks().containsKey(id)) {
            context.sendMessage(Texts.of("unlock.unknown").param("unlock", id));
            return;
        }
        UnlockProgress progress = stationRef.getStore().getComponent(stationRef, StationComponent.getComponentType()).getUnlocks();
        for (String unlockId : ids) {
            if (lock) {
                progress.forceLock(unlockId);
            } else {
                progress.forceUnlock(unlockId);
            }
        }
        StationAccess.markNeedsSaving(stationRef);
        viewers.refreshAll(world, target);
        logger.at(Level.INFO).log("[%s] %s %s %s at station (%d, %d, %d)", getName(), playerRef.getUsername(),
                lock ? "locked" : "unlocked", ids, target.x, target.y, target.z);
        context.sendMessage(Texts.of(lock ? "commands.tradestation.lock.done" : "commands.tradestation.unlock.done")
                .param("count", ids.size()));
    }
}
