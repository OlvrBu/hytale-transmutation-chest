package io.github.olvrbu.transmutationchest.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compact text form of an item's requirements, for commands: {@code +} separates requirements that are
 * all needed, {@code |} separates alternatives. {@code "Rock_Stone_Cobble+Wood_Oak_Trunk|Wood_Ash_Trunk"}
 * means stone cobble AND (oak OR ash); {@code "none"} means no unlock is needed. Commas are accepted
 * like {@code +}, but Hytale's command parser splits arguments at commas, so commands use {@code +}.
 */
public final class Requirements {

    private Requirements() {
    }

    public static List<Set<String>> parse(String spec) {
        String trimmed = spec == null ? "" : spec.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) {
            return List.of();
        }
        List<Set<String>> result = new ArrayList<>();
        for (String part : trimmed.split("[+,]")) {
            Set<String> anyOf = new LinkedHashSet<>();
            for (String alternative : part.split("\\|")) {
                if (!alternative.isBlank()) {
                    anyOf.add(alternative.trim());
                }
            }
            if (anyOf.isEmpty()) {
                throw new IllegalArgumentException("empty requirement in \"" + spec + "\"");
            }
            result.add(anyOf);
        }
        return result;
    }

    public static String format(List<Set<String>> requirements) {
        if (requirements.isEmpty()) {
            return "none";
        }
        return requirements.stream().map(anyOf -> String.join("|", anyOf)).collect(Collectors.joining("+"));
    }
}
