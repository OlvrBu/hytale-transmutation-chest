package io.github.obut.tradestation.station;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import javax.annotation.Nullable;

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

    /** A station item. With balance 0 it is a plain item, identical to a freshly obtained one. */
    public static ItemStack create(long balance) {
        ItemStack item = new ItemStack(ITEM_ID, 1);
        if (balance == 0) {
            return item;
        }
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(StationComponent.getComponentType(), new StationComponent(balance));
        return item.withMetadata(ItemStack.Metadata.BLOCK_HOLDER, ChunkStore.REGISTRY.serialize(holder));
    }

    /** Balance carried by a station item: 0 if it carries none, null if it isn't a station item. */
    @Nullable
    public static Long balanceOf(@Nullable ItemStack item) {
        if (item == null || !ITEM_ID.equals(item.getItemId())) {
            return null;
        }
        BsonDocument metadata = item.getMetadata();
        BsonValue saved = metadata == null ? null : metadata.get(ItemStack.Metadata.BLOCK_HOLDER);
        if (saved == null || !saved.isDocument()) {
            return 0L;
        }
        StationComponent station = ChunkStore.REGISTRY.deserialize(saved.asDocument())
                .getComponent(StationComponent.getComponentType());
        return station == null ? 0L : station.getBalance();
    }
}
