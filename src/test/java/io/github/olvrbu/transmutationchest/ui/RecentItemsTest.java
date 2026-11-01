package io.github.olvrbu.transmutationchest.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentItemsTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void newestFirstWithoutDuplicates() {
        RecentItems recents = new RecentItems();
        recents.record(alice, "A");
        recents.record(alice, "B");
        recents.record(alice, "A");
        assertEquals(List.of("A", "B"), recents.of(alice));
    }

    @Test
    void perPlayer() {
        RecentItems recents = new RecentItems();
        recents.record(alice, "A");
        assertEquals(List.of(), recents.of(bob));
    }

    @Test
    void oldestDroppedBeyondLimit() {
        RecentItems recents = new RecentItems();
        for (int i = 0; i <= RecentItems.LIMIT; i++) {
            recents.record(alice, "Item" + i);
        }
        List<String> items = recents.of(alice);
        assertEquals(RecentItems.LIMIT, items.size());
        assertEquals("Item" + RecentItems.LIMIT, items.get(0));
        assertEquals("Item1", items.get(items.size() - 1));
    }
}
