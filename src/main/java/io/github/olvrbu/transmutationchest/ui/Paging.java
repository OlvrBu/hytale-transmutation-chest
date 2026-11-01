package io.github.olvrbu.transmutationchest.ui;

import java.util.List;

/** Splitting a list into fixed-size pages. Plain Java, unit-testable. Pages are 0-based. */
public final class Paging {

    private Paging() {
    }

    /** Number of pages; at least 1, so an empty list still shows "page 1 of 1". */
    public static int pageCount(int items, int pageSize) {
        return Math.max(1, (items + pageSize - 1) / pageSize);
    }

    /** Brings a page index back into range (e.g. after the filter shrank the list). */
    public static int clamp(int page, int items, int pageSize) {
        return Math.max(0, Math.min(page, pageCount(items, pageSize) - 1));
    }

    public static <T> List<T> slice(List<T> items, int page, int pageSize) {
        int from = clamp(page, items.size(), pageSize) * pageSize;
        return items.subList(Math.min(from, items.size()), Math.min(from + pageSize, items.size()));
    }
}
