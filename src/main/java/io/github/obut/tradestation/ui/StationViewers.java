package io.github.obut.tradestation.ui;

import com.hypixel.hytale.server.core.universe.world.World;
import io.github.obut.tradestation.station.StationLid;
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

    /** Registers an open page; the first viewer opens the station's lid. Call on the world thread. */
    void add(World world, Vector3i pos, StationPage page) {
        Set<StationPage> set = pages.computeIfAbsent(Key.of(world, pos), k -> ConcurrentHashMap.newKeySet());
        if (set.add(page) && set.size() == 1) {
            StationLid.open(world, pos);
        }
    }

    /** Unregisters a closed page; the last viewer closes the station's lid. Call on the world thread. */
    void remove(World world, Vector3i pos, StationPage page) {
        boolean[] wasLast = {false}; // lambdas can only write to captured arrays, not local variables
        // computeIfPresent returning null removes the entry, so empty sets don't pile up.
        pages.computeIfPresent(Key.of(world, pos), (k, set) -> {
            wasLast[0] = set.remove(page) && set.isEmpty();
            return set.isEmpty() ? null : set;
        });
        if (wasLast[0]) {
            StationLid.close(world, pos);
        }
    }

    /** Whether anyone has this station's page open. */
    public boolean isViewed(World world, Vector3i pos) {
        return pages.containsKey(Key.of(world, pos));
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

    /** Refreshes every open station page, each on its own world's thread (e.g. after a config reload). */
    public void refreshEverywhere() {
        for (Set<StationPage> set : pages.values()) {
            for (StationPage page : List.copyOf(set)) {
                page.world().execute(page::refresh);
            }
        }
    }

    /** Closes every open page of this station, e.g. because it was removed. Call on its world thread. */
    public void closeAll(World world, Vector3i pos) {
        for (StationPage page : viewersOf(world, pos)) {
            page.closeBecauseStationGone();
        }
    }
}
