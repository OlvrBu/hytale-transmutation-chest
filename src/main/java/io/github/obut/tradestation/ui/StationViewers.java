package io.github.obut.tradestation.ui;

import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which players currently have which station's page open, so every open view of a station can be
 * refreshed after a trade (or closed when the station disappears).
 * <p>
 * Keyed by world + position rather than by the station's ECS {@code Ref}: the block entity is
 * replaced when a station is placed from an item, and positions stay meaningful across that.
 * Concurrent collections because different worlds run on different threads.
 */
public final class StationViewers {

    private record Key(String world, int x, int y, int z) {
        static Key of(World world, Vector3i pos) {
            return new Key(world.getName(), pos.x, pos.y, pos.z);
        }
    }

    private final Map<Key, Set<StationPage>> pages = new ConcurrentHashMap<>();

    void add(World world, Vector3i pos, StationPage page) {
        pages.computeIfAbsent(Key.of(world, pos), k -> ConcurrentHashMap.newKeySet()).add(page);
    }

    void remove(World world, Vector3i pos, StationPage page) {
        // computeIfPresent returning null removes the entry, so empty sets don't pile up.
        pages.computeIfPresent(Key.of(world, pos), (k, set) -> {
            set.remove(page);
            return set.isEmpty() ? null : set;
        });
    }

    private List<StationPage> viewersOf(World world, Vector3i pos) {
        Set<StationPage> set = pages.get(Key.of(world, pos));
        return set == null ? List.of() : List.copyOf(set); // copy: refreshing may close/remove pages
    }

    /** Refreshes every open page of this station. Call on the station's world thread. */
    public void refreshAll(World world, Vector3i pos) {
        for (StationPage page : viewersOf(world, pos)) {
            page.refresh();
        }
    }

    /** Closes every open page of this station, e.g. because it was removed. Call on its world thread. */
    public void closeAll(World world, Vector3i pos) {
        for (StationPage page : viewersOf(world, pos)) {
            page.closeBecauseStationGone();
        }
    }
}
