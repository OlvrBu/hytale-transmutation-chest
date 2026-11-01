package io.github.olvrbu.transmutationchest.chest;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3i;

import javax.annotation.Nullable;

/**
 * Lookups from a world position to the block there. Must run on the world's thread.
 * <p>
 * Blocks are stored per chunk section (a 32-block cube): the section's {@code BlockSection}
 * holds block type IDs, and its {@code BlockComponentSection} maps a block index to the ECS
 * entity carrying that block's components (only blocks with a "BlockEntity" have one).
 */
public final class ChestAccess {

    private ChestAccess() {
    }

    /** The block entity of the Transmutation Chest at this position, or null if there isn't one. */
    @Nullable
    public static Ref<ChunkStore> findChest(World world, int x, int y, int z) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return null; // chunk not loaded
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        BlockComponentSection components = store.getComponent(section, BlockComponentSection.getComponentType());
        if (components == null) {
            return null;
        }
        Ref<ChunkStore> block = components.getBlockReference(ChunkUtil.indexBlock(x, y, z));
        if (block == null || !block.isValid() || store.getComponent(block, ChestComponent.getComponentType()) == null) {
            return null;
        }
        return block;
    }

    /** How far away a chest can be targeted by looking at it (commands). */
    public static final double TARGET_REACH = 8.0;

    /** Position of the Transmutation Chest the player is looking at, or null if they aren't looking at one. */
    @Nullable
    public static Vector3i targetedChest(Ref<EntityStore> player, Store<EntityStore> store, World world) {
        Vector3i target = TargetUtil.getTargetBlock(player, TARGET_REACH, store);
        if (target == null) {
            return null;
        }
        Vector3i main = resolveMainBlock(world, target.x, target.y, target.z);
        return findChest(world, main.x, main.y, main.z) == null ? null : main;
    }

    /**
     * Multi-block models (like the bench model) occupy extra "filler" cells that point back to the
     * main block, which is the one with the block entity. Returns the main block's position.
     */
    public static Vector3i resolveMainBlock(World world, int x, int y, int z) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        BlockSection blocks = section == null || !section.isValid() ? null
                : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        int filler = blocks == null ? 0 : blocks.getFiller(x, y, z);
        return new Vector3i(x - FillerBlockUtil.unpackX(filler), y - FillerBlockUtil.unpackY(filler),
                z - FillerBlockUtil.unpackZ(filler));
    }

    /** ID of the block type at this position, or null if the chunk isn't loaded. */
    @Nullable
    public static String blockTypeId(World world, int x, int y, int z) {
        Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (section == null || !section.isValid()) {
            return null;
        }
        BlockSection blocks = world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return null;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        return type == null ? null : type.getId();
    }

    /** Tells the chunk saver this block's components changed, so the change is written to disk. */
    public static void markNeedsSaving(Ref<ChunkStore> chest) {
        BlockModule.BlockStateInfo info = chest.getStore().getComponent(chest, BlockModule.BlockStateInfo.getComponentType());
        if (info != null) {
            info.markNeedsSaving();
        }
    }
}
