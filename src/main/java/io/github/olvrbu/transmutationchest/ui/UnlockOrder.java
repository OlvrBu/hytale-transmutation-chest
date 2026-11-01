package io.github.olvrbu.transmutationchest.ui;

import java.util.Comparator;

/**
 * Order of the Unlock tab. Plain Java, so it's unit-testable.
 * <p>
 * Open unlocks come first: those you have materials for, then those with some progress, then the
 * untouched ones; completed unlocks (only shown on request) go last. Alphabetical within each group.
 */
public final class UnlockOrder {

    /** One row's sort inputs: its translated name, whether it's complete, items you have, items donated. */
    public record Entry(String id, String name, boolean done, int have, long donated) {

        int group() {
            if (done) {
                return 3;
            }
            if (have > 0) {
                return 0;
            }
            return donated > 0 ? 1 : 2;
        }
    }

    public static final Comparator<Entry> COMPARATOR = Comparator.comparingInt(Entry::group)
            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id);

    private UnlockOrder() {
    }
}
