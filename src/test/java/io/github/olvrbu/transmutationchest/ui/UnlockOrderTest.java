package io.github.olvrbu.transmutationchest.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnlockOrderTest {

    @Test
    void materialsThenProgressThenUntouchedThenDone() {
        List<UnlockOrder.Entry> entries = new ArrayList<>(List.of(
                new UnlockOrder.Entry("done", "Aaa", true, 50, 500),
                new UnlockOrder.Entry("untouched", "Bbb", false, 0, 0),
                new UnlockOrder.Entry("progress", "Ccc", false, 0, 10),
                new UnlockOrder.Entry("materials", "Ddd", false, 3, 0)));
        entries.sort(UnlockOrder.COMPARATOR);
        assertEquals(List.of("materials", "progress", "untouched", "done"),
                entries.stream().map(UnlockOrder.Entry::id).toList());
    }

    @Test
    void materialsWinOverProgress() {
        List<UnlockOrder.Entry> entries = new ArrayList<>(List.of(
                new UnlockOrder.Entry("progressOnly", "Aaa", false, 0, 100),
                new UnlockOrder.Entry("both", "Zzz", false, 1, 100)));
        entries.sort(UnlockOrder.COMPARATOR);
        assertEquals("both", entries.get(0).id());
    }

    @Test
    void alphabeticalWithinGroupIgnoringCase() {
        List<UnlockOrder.Entry> entries = new ArrayList<>(List.of(
                new UnlockOrder.Entry("s", "slate", false, 0, 0),
                new UnlockOrder.Entry("b", "Basalt", false, 0, 0),
                new UnlockOrder.Entry("m", "Marble", false, 0, 0)));
        entries.sort(UnlockOrder.COMPARATOR);
        assertEquals(List.of("b", "m", "s"), entries.stream().map(UnlockOrder.Entry::id).toList());
    }
}
