package io.github.olvrbu.transmutationchest.chest;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;


/**
 * The Transmutation_Chest item and how it carries a balance.
 * <p>
 * Uses Hytale's built-in "BlockHolder" item metadata: a saved copy of a block's components. When a
 * player places an item that has it, the server restores those components onto the new block
 * (BlockPlaceUtils), so placing needs no code of ours. We store just our {@link ChestComponent}
 * in it, encoded by its own codec.
 */
public final class ChestItem {

    /** Item ID, from the asset file name Server/Item/Items/Transmutation_Chest.json. */
    public static final String ITEM_ID = "Transmutation_Chest";

    private ChestItem() {
    }

    /**
     * A chest item carrying the chest's data (balance and unlock progress). A fresh chest
     * (nothing to keep) becomes a plain item, identical to a newly crafted one.
     */
    public static ItemStack create(ChestComponent chest) {
        ItemStack item = new ItemStack(ITEM_ID, 1);
        if (chest.isFresh()) {
            return item;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(ChestComponent.getComponentType(), (ChestComponent) chest.clone());
        return item.withMetadata(ItemStack.Metadata.BLOCK_HOLDER, ChunkStore.REGISTRY.serialize(holder));
    }
}
