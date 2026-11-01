package io.github.olvrbu.transmutationchest.chest;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.olvrbu.transmutationchest.ui.ChestViewers;
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Watches chest block entities being created and destroyed, and makes a destroyed chest drop
 * an item carrying its balance.
 * <p>
 * An ECS "system" is code the server runs for every entity matching its query. A {@link RefSystem}
 * is called when a matching entity is added to or removed from the store. Hooking removal here,
 * instead of handling one specific "player broke a block" event, also catches explosions, physics,
 * commands and builder tools. The block's normal drop is switched off in its JSON
 * ("Gathering.Breaking.Quantity": 0) so only this drop happens.
 */
public class ChestLifecycleSystem extends RefSystem<ChunkStore> {

    private final HytaleLogger logger;
    private final ChestViewers viewers;
    private final Query<ChunkStore> query;

    public ChestLifecycleSystem(HytaleLogger logger, ChestViewers viewers) {
        this.logger = logger;
        this.viewers = viewers;
        // Chest blocks always have BlockStateInfo too (it records where the block is).
        this.query = Query.and(ChestComponent.getComponentType(), BlockModule.BlockStateInfo.getComponentType());
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == AddReason.LOAD) {
            // An existing chest's chunk was loaded from disk. Its lid state is saved with the block,
            // so a chest saved while open would load open: close it. Not inside the chunk store's
            // update, so as a separate task on the world thread.
            Vector3i pos = positionOf(ref, commandBuffer);
            World world = store.getExternalData().getWorld();
            if (pos != null) {
                world.execute(() -> {
                    if (!viewers.isViewed(world, pos)) {
                        ChestLid.resetIfOpen(world, pos);
                    }
                });
            }
            return;
        }
        if (reason != AddReason.SPAWN) {
            return;
        }
        ChestComponent chest = commandBuffer.getComponent(ref, ChestComponent.getComponentType());
        Vector3i pos = positionOf(ref, commandBuffer);
        if (chest != null && pos != null) {
            logger.at(Level.INFO).log("Chest created at %s with balance %d", format(pos), chest.getBalance());
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == RemoveReason.UNLOAD) {
            return; // chunk unloaded; the chest is saved, not destroyed
        }
        ChestComponent chest = commandBuffer.getComponent(ref, ChestComponent.getComponentType());
        if (chest == null) {
            return;
        }
        long balance = chest.getBalance();
        ChestComponent snapshot = (ChestComponent) chest.clone(); // the original goes away with the entity
        Vector3i pos = positionOf(ref, commandBuffer);
        if (pos == null) {
            logger.at(Level.WARNING).log("Chest removed (%s) at an unknown position; balance %d LOST", reason, balance);
            return;
        }
        World world = store.getExternalData().getWorld();
        // We're inside the chunk store's update. Look at the result and spawn the drop afterwards,
        // as a separate task on the world thread.
        world.execute(() -> afterRemoval(world, pos, snapshot, reason));
    }

    private void afterRemoval(World world, Vector3i pos, ChestComponent chest, RemoveReason reason) {
        long balance = chest.getBalance();
        // The block entity is also replaced (removed + re-added) when the block itself stays,
        // e.g. when placing a chest item restores its saved data onto the fresh block.
        if (ChestAccess.findChest(world, pos.x, pos.y, pos.z) != null) {
            if (!chest.isFresh()) {
                logger.at(Level.WARNING).log("Chest data at %s was replaced (%s); previous balance %d and %d unlock(s) not carried over",
                        format(pos), reason, balance, chest.getUnlocks().unlocked().size());
            }
            return;
        }
        viewers.closeAll(world, pos); // nobody can keep trading with a chest that's gone
        String nowThere = ChestAccess.blockTypeId(world, pos.x, pos.y, pos.z);
        Store<EntityStore> entities = world.getEntityStore().getStore();
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(entities, List.of(ChestItem.create(chest)),
                new Vector3d(pos.x + 0.5, pos.y, pos.z + 0.5), Rotation3f.IDENTITY);
        entities.addEntities(drops, AddReason.SPAWN);
        logger.at(Level.INFO).log("Chest at %s removed (%s, block now %s); dropped chest item with balance %d, unlocks %s",
                format(pos), reason, nowThere, balance, chest.getUnlocks().unlocked());
    }

    private static Vector3i positionOf(Ref<ChunkStore> ref, CommandBuffer<ChunkStore> commandBuffer) {
        BlockModule.BlockStateInfo info = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        Vector3i pos = new Vector3i();
        return info != null && info.fillWorldPos(commandBuffer, pos) ? pos : null;
    }

    private static String format(Vector3i pos) {
        return "(" + pos.x + ", " + pos.y + ", " + pos.z + ")";
    }
}
