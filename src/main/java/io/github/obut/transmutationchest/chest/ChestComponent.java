package io.github.obut.transmutationchest.chest;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.obut.transmutationchest.TransmutationChestPlugin;
import io.github.obut.transmutationchest.unlock.UnlockProgress;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;

/**
 * Data attached to a placed Transmutation Chest block: its shared balance and unlock progress.
 * <p>
 * This is a {@code Component<ChunkStore>}: block data lives in the chunk's ECS store and is saved
 * with the chunk using {@link #CODEC}. The Transmutation_Chest block JSON lists this component under
 * {@code BlockEntity.Components}, so every newly placed chest gets one with balance 0.
 */
public class ChestComponent implements Component<ChunkStore> {

    /** Name used in block JSON and in saved data. Changing it would orphan existing saves. */
    public static final String ID = "TransmutationChest_Data";

    public static final BuilderCodec<ChestComponent> CODEC = BuilderCodec.builder(ChestComponent.class, ChestComponent::new)
            .append(new KeyedCodec<>("Balance", Codec.LONG),
                    (chest, balance) -> chest.balance = Math.max(0, balance), // never trust saved data to be sane
                    chest -> chest.balance)
            .add()
            // Unlock progress: donations so far per unlock, and the (permanent) completed unlocks.
            // Chests saved before unlocks existed have neither key and start fully locked.
            .append(new KeyedCodec<>("Donated", new MapCodec<Long, HashMap<String, Long>>(Codec.LONG, HashMap::new)),
                    (chest, donated) -> chest.unlocks = new UnlockProgress(donated, chest.unlocks.unlocked()),
                    chest -> new HashMap<>(chest.unlocks.donated()))
            .add()
            .append(new KeyedCodec<>("Unlocked", Codec.STRING_ARRAY),
                    (chest, ids) -> chest.unlocks = new UnlockProgress(chest.unlocks.donated(), new LinkedHashSet<>(Arrays.asList(ids))),
                    chest -> chest.unlocks.unlocked().toArray(String[]::new))
            .add()
            .build();

    private long balance;
    private UnlockProgress unlocks = new UnlockProgress();

    public ChestComponent() {
    }

    public ChestComponent(long balance) {
        setBalance(balance);
    }

    public static ComponentType<ChunkStore, ChestComponent> getComponentType() {
        return TransmutationChestPlugin.get().getChestComponentType();
    }

    public long getBalance() {
        return balance;
    }

    /**
     * Callers that change a placed chest must also mark its block as needing saving
     * (see {@link ChestAccess#markNeedsSaving}), or the change may not be written to disk.
     */
    public void setBalance(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must be >= 0, got " + balance);
        }
        this.balance = balance;
    }

    /** True for a chest with nothing worth keeping (no balance, no unlock progress). */
    public boolean isFresh() {
        return balance == 0 && unlocks.donated().isEmpty() && unlocks.unlocked().isEmpty();
    }

    /** Live unlock state; changes must also call {@link ChestAccess#markNeedsSaving}. */
    public UnlockProgress getUnlocks() {
        return unlocks;
    }

    @Override
    public Component<ChunkStore> clone() {
        ChestComponent copy = new ChestComponent(balance);
        copy.unlocks = unlocks.copy();
        return copy;
    }
}
