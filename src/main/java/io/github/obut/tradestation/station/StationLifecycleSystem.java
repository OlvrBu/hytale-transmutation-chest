package io.github.obut.tradestation.station;

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
import org.joml.Vector3d;
import org.joml.Vector3i;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Watches station block entities being created and destroyed, and makes a destroyed station drop
 * an item carrying its balance.
 * <p>
 * An ECS "system" is code the server runs for every entity matching its query. A {@link RefSystem}
 * is called when a matching entity is added to or removed from the store. Hooking removal here,
 * instead of handling one specific "player broke a block" event, also catches explosions, physics,
 * commands and builder tools. The block's normal drop is switched off in its JSON
 * ("Gathering.Breaking.Quantity": 0) so only this drop happens.
 */
public class StationLifecycleSystem extends RefSystem<ChunkStore> {

    private final HytaleLogger logger;
    private final Query<ChunkStore> query;

    public StationLifecycleSystem(HytaleLogger logger) {
        this.logger = logger;
        // Station blocks always have BlockStateInfo too (it records where the block is).
        this.query = Query.and(StationComponent.getComponentType(), BlockModule.BlockStateInfo.getComponentType());
    }

    @Nonnull
    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason != AddReason.SPAWN) {
            return; // LOAD = an existing station's chunk was loaded from disk
        }
        StationComponent station = commandBuffer.getComponent(ref, StationComponent.getComponentType());
        Vector3i pos = positionOf(ref, commandBuffer);
        if (station != null && pos != null) {
            logger.at(Level.INFO).log("Station created at %s with balance %d", format(pos), station.getBalance());
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == RemoveReason.UNLOAD) {
            return; // chunk unloaded; the station is saved, not destroyed
        }
        StationComponent station = commandBuffer.getComponent(ref, StationComponent.getComponentType());
        if (station == null) {
            return;
        }
        long balance = station.getBalance();
        Vector3i pos = positionOf(ref, commandBuffer);
        if (pos == null) {
            logger.at(Level.WARNING).log("Station removed (%s) at an unknown position; balance %d LOST", reason, balance);
            return;
        }
        World world = store.getExternalData().getWorld();
        // We're inside the chunk store's update. Look at the result and spawn the drop afterwards,
        // as a separate task on the world thread.
        world.execute(() -> afterRemoval(world, pos, balance, reason));
    }

    private void afterRemoval(World world, Vector3i pos, long balance, RemoveReason reason) {
        // The block entity is also replaced (removed + re-added) when the block itself stays,
        // e.g. when placing a station item restores its saved data onto the fresh block.
        if (StationAccess.findStation(world, pos.x, pos.y, pos.z) != null) {
            if (balance != 0) {
                logger.at(Level.WARNING).log("Station data at %s was replaced (%s); previous balance %d not carried over",
                        format(pos), reason, balance);
            }
            return;
        }
        String nowThere = StationAccess.blockTypeId(world, pos.x, pos.y, pos.z);
        Store<EntityStore> entities = world.getEntityStore().getStore();
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(entities, List.of(StationItem.create(balance)),
                new Vector3d(pos.x + 0.5, pos.y, pos.z + 0.5), Rotation3f.IDENTITY);
        entities.addEntities(drops, AddReason.SPAWN);
        logger.at(Level.INFO).log("Station at %s removed (%s, block now %s); dropped station item with balance %d",
                format(pos), reason, nowThere, balance);
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
