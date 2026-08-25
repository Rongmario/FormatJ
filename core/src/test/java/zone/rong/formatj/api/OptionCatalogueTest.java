package zone.rong.formatj.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.config.StyleFiles;
import zone.rong.formatj.core.config.TomlWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OptionCatalogueTest {

    private static final Pattern KEY = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*(\\.[a-z0-9]+(-[a-z0-9]+)*)+");

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
