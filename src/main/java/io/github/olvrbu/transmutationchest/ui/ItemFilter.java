package io.github.olvrbu.transmutationchest.ui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Which item rows the chest page shows. Plain Java, so it's unit-testable.
 * <p>
 * The search query is split into words; an item matches if every word occurs in its display name or
 * its ID (case-insensitive, underscores in IDs count as spaces). "cobble stone" therefore matches
 * "Cobblestone" and "Rock_Stone_Cobble".
 */
public record ItemFilter(List<String> words, boolean ownedOnly) {

    public static final ItemFilter NONE = new ItemFilter(List.of(), false);

    public ItemFilter {
        words = List.copyOf(words);
    }

    public static ItemFilter of(String query, boolean ownedOnly) {
        String normalized = normalize(query == null ? "" : query);
        List<String> words = normalized.isBlank() ? List.of()
                : Arrays.stream(normalized.trim().split("\\s+")).toList();
        return new ItemFilter(words, ownedOnly);
    }

    public ItemFilter withQuery(String query) {
        return of(query, ownedOnly);
    }

    public ItemFilter withOwnedOnly(boolean ownedOnly) {
        return new ItemFilter(words, ownedOnly);
    }

    public boolean matches(String itemId, String displayName, int owned) {
        if (ownedOnly && owned <= 0) {
            return false;
        }
        String haystack = normalize(itemId) + " " + normalize(displayName == null ? "" : displayName);
        for (String word : words) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
