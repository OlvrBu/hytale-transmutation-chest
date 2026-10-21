package io.github.obut.transmutationchest.commands;

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
import io.github.obut.transmutationchest.config.TradeConfigManager;
import io.github.obut.transmutationchest.chest.ChestAccess;
import io.github.obut.transmutationchest.chest.ChestComponent;
import io.github.obut.transmutationchest.text.Texts;
import io.github.obut.transmutationchest.ui.ChestViewers;
import io.github.obut.transmutationchest.unlock.UnlockProgress;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Admin/testing: {@code /transmutationchest unlock <id|all>} and {@code /transmutationchest lock <id|all>} on the
 * Transmutation Chest you're looking at, without donating. Operators only.
 */
public class UnlockCommand extends AbstractPlayerCommand {

    private final boolean lock;
    private final TradeConfigManager configManager;
    private final ChestViewers viewers;
    private final HytaleLogger logger;
    private final RequiredArg<String> idArg;

    public UnlockCommand(boolean lock, TradeConfigManager configManager, ChestViewers viewers, HytaleLogger logger) {
        super(lock ? "lock" : "unlock", Texts.key(lock ? "commands.transmutationchest.lock.desc" : "commands.transmutationchest.unlock.desc"));
        this.lock = lock;
        this.configManager = configManager;
        this.viewers = viewers;
        this.logger = logger;
        this.idArg = withRequiredArg("id", Texts.key("commands.transmutationchest.unlock.id.desc"), ArgTypes.STRING);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Vector3i target = ChestAccess.targetedChest(ref, store, world);
        Ref<ChunkStore> chestRef = target == null ? null : ChestAccess.findChest(world, target.x, target.y, target.z);
        if (chestRef == null) {
            context.sendMessage(Texts.of("commands.transmutationchest.noTarget").param("reach", (int) ChestAccess.TARGET_REACH));
            return;
        }
        String id = idArg.get(context);
        List<String> ids = id.equalsIgnoreCase("all") ? List.copyOf(configManager.current().unlocks().keySet()) : List.of(id);
        if (!id.equalsIgnoreCase("all") && !configManager.current().unlocks().containsKey(id)) {
            context.sendMessage(Texts.of("unlock.unknown").param("unlock", id));
            return;
        }
        UnlockProgress progress = chestRef.getStore().getComponent(chestRef, ChestComponent.getComponentType()).getUnlocks();
        for (String unlockId : ids) {
            if (lock) {
                progress.forceLock(unlockId);
            } else {
                progress.forceUnlock(unlockId);
            }
        }
        ChestAccess.markNeedsSaving(chestRef);
        viewers.refreshAll(world, target);
        logger.at(Level.INFO).log("[%s] %s %s %s at chest (%d, %d, %d)", getName(), playerRef.getUsername(),
                lock ? "locked" : "unlocked", ids, target.x, target.y, target.z);
        context.sendMessage(Texts.of(lock ? "commands.transmutationchest.lock.done" : "commands.transmutationchest.unlock.done")
                .param("count", ids.size()));
    }
}
