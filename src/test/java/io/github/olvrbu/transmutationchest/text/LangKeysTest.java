package io.github.olvrbu.transmutationchest.text;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every translation key referenced in code or UI layouts must exist in the English .lang file,
 * otherwise players would see the raw key. (Gradle runs tests with the project root as working dir.)
 */
class LangKeysTest {

    private static final Path LANG = Path.of("src/main/resources/Server/Languages/en-US/transmutationchest.lang");
    private static final Pattern JAVA_KEY = Pattern.compile("Texts\\.(?:of|key)\\(\"([^\"]+)\"\\)");
    private static final Pattern UI_KEY = Pattern.compile("%transmutationchest\\.([A-Za-z0-9_.]+)");
    private static final Pattern ASSET_KEY = Pattern.compile("\"transmutationchest\\.([A-Za-z0-9_.]+)\"");

    @Test
    void allReferencedKeysExist() throws IOException {
        Set<String> defined = definedKeys();
        Set<String> missing = new TreeSet<>();
        collect(Path.of("src/main/java"), ".java", JAVA_KEY, defined, missing);
        collect(Path.of("src/main/resources/Common/UI"), ".ui", UI_KEY, defined, missing);
        collect(Path.of("src/main/resources/Server/Item"), ".json", ASSET_KEY, defined, missing);
        assertEquals(Set.of(), missing, "keys missing from " + LANG);
    }

    @Test
    void langFileHasNoDuplicateKeys() throws IOException {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new TreeSet<>();
        for (String line : Files.readAllLines(LANG)) {
            String key = keyOf(line);
            if (key != null && !seen.add(key)) {
                duplicates.add(key);
            }
        }
        assertEquals(Set.of(), duplicates);
        assertFalse(seen.isEmpty());
    }

    private static Set<String> definedKeys() throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : Files.readAllLines(LANG)) {
            String key = keyOf(line);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String keyOf(String line) {
        int eq = line.indexOf('=');
        return line.isBlank() || line.startsWith("#") || eq < 0 ? null : line.substring(0, eq).trim();
    }

    private static void collect(Path root, String extension, Pattern pattern, Set<String> defined, Set<String> missing)
            throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(extension)).toList()) {
                Matcher m = pattern.matcher(Files.readString(file));
                while (m.find()) {
                    if (!defined.contains(m.group(1))) {
                        missing.add(m.group(1) + "  (" + file + ")");
                    }
                }
            }
        }
    }
}
