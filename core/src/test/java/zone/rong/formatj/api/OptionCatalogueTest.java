package zone.rong.formatj.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.config.StyleFiles;
import zone.rong.formatj.core.config.TomlWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OptionCatalogueTest {

    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");
    private static final Pattern TABLE_ROW_KEY = Pattern.compile("(?m)^\\|\\s*`([a-z0-9.-]+)`[^|]*\\|");

    @Test
    void catalogueIsNotEmpty() {
        assertTrue(OptionRegistry.all().size() > 100, "the rule catalogue should be large by design");
    }

    @Test
    void everyKeyIsWellFormedAndUnique() {
        Set<String> seen = new HashSet<>();
        for (Option<?> option : OptionRegistry.all()) {
            assertTrue(KEY.matcher(option.key()).matches(), () -> "malformed key: " + option.key());
            assertTrue(seen.add(option.key()), () -> "duplicate key: " + option.key());
        }
    }

    @Test
    void everyOptionIsDocumented() {
        for (Option<?> option : OptionRegistry.all()) {
            assertFalse(option.description().isBlank(), () -> option.key() + " has no description");
        }
    }

    @Test
    void everyOptionHasAReadmeTableRow() {
        Set<String> documented = readmeTableKeys();
        List<String> missing = new ArrayList<>();
        for (Option<?> option : OptionRegistry.all()) {
            if (!documented.contains(option.key())) {
                missing.add(option.key());
            }
        }
        assertTrue(missing.isEmpty(), () -> "no README table row documents: " + missing);
    }

    private static Set<String> readmeTableKeys() {
        String readme;
        try {
            readme = Files.readString(repositoryRoot().resolve("README.md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> keys = new HashSet<>();
        Matcher matcher = TABLE_ROW_KEY.matcher(readme);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static Path repositoryRoot() {
        for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("README.md"))) {
                return directory;
            }
        }
        throw new IllegalStateException("README.md not found in any ancestor of " + Path.of("").toAbsolutePath());
    }

    @Test
    void everyOptionRendersAndParsesBackToItself() {
        for (Option<?> option : OptionRegistry.all()) {
            assertRoundTrips(option);
        }
    }

    private static <T> void assertRoundTrips(Option<T> option) {
        String rendered = option.render(option.defaultValue());
        String unquoted =
                rendered.startsWith("\"") && rendered.endsWith("\"")
                        ? rendered.substring(1, rendered.length() - 1)
                        : rendered;
        assertEquals(option.defaultValue(), option.parse(unquoted), () -> option.key() + " did not round-trip");
    }

    @Test
    void unknownKeysAreRejectedWithAUsefulMessage() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> OptionRegistry.require("indent.siz"));
        assertTrue(failure.getMessage().contains("--dump-config"));
    }

    @Test
    void aDumpedStyleParsesBackToTheSameEffectiveValues() {
        Style style = Style.preset(Preset.GOOGLE).indent(indent -> indent.size(3)).build();
        Style reloaded = StyleFiles.parse(TomlWriter.write(style));
        assertEquals(style.resolvedValues(), reloaded.resolvedValues());
        assertEquals(3, reloaded.get(IndentRules.SIZE));
        assertEquals(100, reloaded.get(WrappingRules.MAX_LINE_LENGTH));
    }

}
