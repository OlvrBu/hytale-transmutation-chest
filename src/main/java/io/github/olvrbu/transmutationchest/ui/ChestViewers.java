package io.github.olvrbu.transmutationchest.ui;

import com.hypixel.hytale.server.core.universe.world.World;
import io.github.olvrbu.transmutationchest.chest.ChestLid;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players currently have which chest's page open, so every open view of a chest can be
 * refreshed after a trade (or closed when the chest disappears).
 * <p>
 * Keyed by world + position rather than by the chest's ECS {@code Ref}: the block entity is
 * replaced when a chest is placed from an item, and positions stay meaningful across that.
 * Concurrent collections because different worlds run on different threads.
 */
public final class ChestViewers {

    private record Key(String world, int x, int y, int z) {
        static Key of(World world, Vector3i pos) {
            return new Key(world.getName(), pos.x, pos.y, pos.z);
        }
    }

    private final Map<Key, Set<ChestPage>> pages = new ConcurrentHashMap<>();

    /** Registers an open page; the first viewer opens the chest's lid. Call on the world thread. */
    void add(World world, Vector3i pos, ChestPage page) {
        Set<ChestPage> set = pages.computeIfAbsent(Key.of(world, pos), k -> ConcurrentHashMap.newKeySet());
        if (set.add(page) && set.size() == 1) {
            ChestLid.open(world, pos);
        }
    }

    /** Unregisters a closed page; the last viewer closes the chest's lid. Call on the world thread. */
    void remove(World world, Vector3i pos, ChestPage page) {
        boolean[] wasLast = {false}; // lambdas can only write to captured arrays, not local variables
        // computeIfPresent returning null removes the entry, so empty sets don't pile up.
        pages.computeIfPresent(Key.of(world, pos), (k, set) -> {
            wasLast[0] = set.remove(page) && set.isEmpty();
            return set.isEmpty() ? null : set;
        });
        if (wasLast[0]) {
            ChestLid.close(world, pos);
        }
    }

    /** Whether anyone has this chest's page open. */
    public boolean isViewed(World world, Vector3i pos) {
        return pages.containsKey(Key.of(world, pos));
    }

    private List<ChestPage> viewersOf(World world, Vector3i pos) {
        Set<ChestPage> set = pages.get(Key.of(world, pos));
        return set == null ? List.of() : List.copyOf(set); // copy: refreshing may close/remove pages
    }

    /** Refreshes every open page of this chest. Call on the chest's world thread. */
    public void refreshAll(World world, Vector3i pos) {
        for (ChestPage page : viewersOf(world, pos)) {
            page.refresh();
        }
    }

    /** Refreshes every open chest page, each on its own world's thread (e.g. after a config reload). */
    public void refreshEverywhere() {
        for (Set<ChestPage> set : pages.values()) {
            for (ChestPage page : List.copyOf(set)) {
                page.world().execute(page::refresh);
            }
        }
    }

    /** Closes every open page of this chest, e.g. because it was removed. Call on its world thread. */
    public void closeAll(World world, Vector3i pos) {
        for (ChestPage page : viewersOf(world, pos)) {
            page.closeBecauseChestGone();
        }
    }
}
