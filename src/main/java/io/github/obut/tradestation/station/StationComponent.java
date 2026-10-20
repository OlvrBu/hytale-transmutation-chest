package io.github.obut.tradestation.station;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.obut.tradestation.TradeStationPlugin;
import io.github.obut.tradestation.unlock.UnlockProgress;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;

/**
 * Data attached to a placed Trade Station block: its shared balance and unlock progress.
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
            // Unlock progress: donations so far per unlock, and the (permanent) completed unlocks.
            // Stations saved before unlocks existed have neither key and start fully locked.
            .append(new KeyedCodec<>("Donated", new MapCodec<Long, HashMap<String, Long>>(Codec.LONG, HashMap::new)),
                    (station, donated) -> station.unlocks = new UnlockProgress(donated, station.unlocks.unlocked()),
                    station -> new HashMap<>(station.unlocks.donated()))
            .add()
            .append(new KeyedCodec<>("Unlocked", Codec.STRING_ARRAY),
                    (station, ids) -> station.unlocks = new UnlockProgress(station.unlocks.donated(), new LinkedHashSet<>(Arrays.asList(ids))),
                    station -> station.unlocks.unlocked().toArray(String[]::new))
            .add()
            .build();

    private long balance;
    private UnlockProgress unlocks = new UnlockProgress();

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

    /** True for a station with nothing worth keeping (no balance, no unlock progress). */
    public boolean isFresh() {
        return balance == 0 && unlocks.donated().isEmpty() && unlocks.unlocked().isEmpty();
    }

    /** Live unlock state; changes must also call {@link StationAccess#markNeedsSaving}. */
    public UnlockProgress getUnlocks() {
        return unlocks;
    }

    @Override
    public Component<ChunkStore> clone() {
        StationComponent copy = new StationComponent(balance);
        copy.unlocks = unlocks.copy();
        return copy;
    }
}
