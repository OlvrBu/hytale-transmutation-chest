package io.github.obut.tradestation.station;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.obut.tradestation.TradeStationPlugin;

/**
 * Data attached to a placed Trade Station block: its shared balance.
 * <p>
 * This is a {@code Component<ChunkStore>}: block data lives in the chunk's ECS store and is saved
 * with the chunk using {@link #CODEC}. The Trade_Station block JSON lists this component under
 * {@code BlockEntity.Components}, so every newly placed station gets one with balance 0.
 */
public class StationComponent implements Component<ChunkStore> {

    /** Name used in block JSON and in saved data. Changing it would orphan existing saves. */
    public static final String ID = "TradeStation_Station";

    public static final BuilderCodec<StationComponent> CODEC = BuilderCodec.builder(StationComponent.class, StationComponent::new)
            .append(new KeyedCodec<>("Balance", Codec.LONG),
                    (station, balance) -> station.balance = Math.max(0, balance), // never trust saved data to be sane
                    station -> station.balance)
            .add()
            .build();

    private long balance;

    public StationComponent() {
    }

    public StationComponent(long balance) {
        setBalance(balance);
    }

    public static ComponentType<ChunkStore, StationComponent> getComponentType() {
        return TradeStationPlugin.get().getStationComponentType();
    }

    public long getBalance() {
        return balance;
    }

    /**
     * Callers that change a placed station must also mark its block as needing saving
     * (see {@link StationAccess#markNeedsSaving}), or the change may not be written to disk.
     */
    public void setBalance(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must be >= 0, got " + balance);
        }
        this.balance = balance;
    }

    @Override
    public Component<ChunkStore> clone() {
        return new StationComponent(balance);
    }
}
