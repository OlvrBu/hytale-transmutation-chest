package io.github.olvrbu.transmutationchest.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Items each player recently bought (at any chest; sells don't count), newest first, for the Buy tab's Recents
 * group. In memory only: forgotten on server restart (decided). Plain Java, so it's unit-testable.
 * <p>
 * Synchronized because each world ticks on its own thread, and players in different worlds may trade
 * at the same time.
 */
public final class RecentItems {

    /** One page of the Buy grid. */
    public static final int LIMIT = ChestPage.GRID_PAGE_SIZE;

    private final Map<UUID, Deque<String>> byPlayer = new HashMap<>();

    /** Moves {@code itemId} to the front of the player's list, dropping the oldest beyond {@link #LIMIT}. */
    public synchronized void record(UUID player, String itemId) {
        Deque<String> items = byPlayer.computeIfAbsent(player, p -> new ArrayDeque<>());
        items.remove(itemId);
        items.addFirst(itemId);
        while (items.size() > LIMIT) {
            items.removeLast();
        }
    }

    /** The player's recent items, newest first (a copy). */
    public synchronized List<String> of(UUID player) {
        Deque<String> items = byPlayer.get(player);
        return items == null ? List.of() : List.copyOf(items);
    }
}
