package io.github.obut.tradestation.station;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;


/**
 * The Trade_Station item and how it carries a balance.
 * <p>
 * Uses Hytale's built-in "BlockHolder" item metadata: a saved copy of a block's components. When a
 * player places an item that has it, the server restores those components onto the new block
 * (BlockPlaceUtils), so placing needs no code of ours. We store just our {@link StationComponent}
 * in it, encoded by its own codec.
 */
public final class StationItem {

    /** Item ID, from the asset file name Server/Item/Items/Trade_Station.json. */
    public static final String ITEM_ID = "Trade_Station";

    private StationItem() {
    }

    /**
     * A station item carrying the station's data (balance and unlock progress). A fresh station
     * (nothing to keep) becomes a plain item, identical to a newly crafted one.
     */
    public static ItemStack create(StationComponent station) {
        ItemStack item = new ItemStack(ITEM_ID, 1);
        if (station.isFresh()) {
            return item;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(StationComponent.getComponentType(), (StationComponent) station.clone());
        return item.withMetadata(ItemStack.Metadata.BLOCK_HOLDER, ChunkStore.REGISTRY.serialize(holder));
    }
}
