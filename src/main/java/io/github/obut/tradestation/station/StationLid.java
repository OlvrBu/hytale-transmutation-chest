package io.github.obut.tradestation.station;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Opens and closes the station's lid, the way chests do. Must run on the world's thread.
 * <p>
 * Hytale block animations are block states: the block JSON's "State.Definitions" declares variants
 * ("OpenWindow", "CloseWindow") with a CustomModelAnimation, and switching the block to a variant
 * makes clients play it. Vanilla chests do this in OpenContainerInteraction, which only works for
 * blocks with an item container, so we switch the state ourselves with the same server call.
 * That call swaps the block type but keeps the block entity, so the StationComponent stays.
 */
public final class StationLid {

    // State names from Trade_Station.json; the same ones vanilla chests use.
    private static final String OPEN = "OpenWindow";
    private static final String CLOSE = "CloseWindow";
    private static final String DEFAULT = "default";

    private StationLid() {
    }

    /** Plays the open animation and sound. */
    public static void open(World world, Vector3i pos) {
        setState(world, pos, OPEN, true);
    }

    /** Plays the close animation and sound. */
    public static void close(World world, Vector3i pos) {
        setState(world, pos, CLOSE, true);
    }

    /**
     * Puts a station left open (e.g. saved while someone had it open) back into its plain closed
     * state, without animation or sound. Does nothing if it isn't open.
     */
    public static void resetIfOpen(World world, Vector3i pos) {
        BlockType current = blockTypeAt(world, pos);
        if (current != null && OPEN.equals(current.getCurrentInteractionState())) {
            setState(world, pos, DEFAULT, false);
        }
    }

    private static void setState(World world, Vector3i pos, String state, boolean sound) {
        // The station may be gone (closing pages after it was broken); never touch another block.
        if (StationAccess.findStation(world, pos.x, pos.y, pos.z) == null) {
            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> section = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        BlockSection blocks = chunkStore.getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return;
        }
        BlockType current = BlockType.getAssetMap().getAsset(blocks.get(pos.x, pos.y, pos.z));
        if (current == null) {
            return;
        }
        BlockOperations.setBlockInteractionState(chunkStore, section, pos.x, pos.y, pos.z, current, state, false);
        if (!sound) {
            return;
        }
        // The sound is not part of the state switch; like OpenContainerInteraction, play it at the block's centre.
        BlockType target = current.getBlockForState(state);
        if (target == null || target.getInteractionSoundEventIndex() == 0) {
            return;
        }
        Vector3d center = new Vector3d();
        current.getBlockCenter(blocks.getRotationIndex(pos.x, pos.y, pos.z), center);
        center.add(pos.x, pos.y, pos.z);
        SoundUtil.playSoundEvent3d(null, target.getInteractionSoundEventIndex(), center, false,
                world.getEntityStore().getStore());
    }

    private static BlockType blockTypeAt(World world, Vector3i pos) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (section == null || !section.isValid()) {
            return null;
        }
        BlockSection blocks = world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        return blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(pos.x, pos.y, pos.z));
    }
}
