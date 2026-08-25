package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.config.StyleFiles;
import zone.rong.formatj.core.config.TomlReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StyleFileTest {

    @Test
    void readsTablesDottedKeysAndArrays() {
        Map<String, String> values =
                TomlReader.read(
                        """
                # a comment
                preset = "google"

                [indent]
                size = 4          # trailing comment
                use-tabs = false

                [imports]
                groups = ["java", "javax", "*"]
                """);
        assertEquals("google", values.get("preset"));
        assertEquals("4", values.get("indent.size"));
        assertEquals("false", values.get("indent.use-tabs"));
        assertEquals("[\"java\", \"javax\", \"*\"]", values.get("imports.groups"));
    }

    @Test
    void aPresetKeyChoosesTheStartingPointAndOtherKeysOverrideIt() {
        Style style =
                StyleFiles.parse(
                        """
                preset = "google"

                [indent]
                size = 4

                [wrapping]
                chained-calls = "break-when-too-long"
                """);
        assertEquals(4, style.get(IndentRules.SIZE));
        assertEquals(100, style.get(WrappingRules.MAX_LINE_LENGTH));
        assertEquals(ChainPolicy.BREAK_WHEN_TOO_LONG, style.get(WrappingRules.CHAINED_CALLS));
    }

    @Test
    void listValuesLoadAsLists() {
        Style style =
                StyleFiles.parse(
                        """
                [imports]
                groups = ["java", "zone.rong.formatj", "*"]
                """);
        assertEquals(List.of("java", "zone.rong.formatj", "*"), style.get(ImportRules.GROUPS));
    }

    @Test
    void malformedFilesFailWithTheLineNumber() {
        TomlReader.TomlException failure =
                assertThrows(TomlReader.TomlException.class, () -> TomlReader.read("[indent\nsize = 4\n"));
        assertEquals(1, failure.line());
        assertTrue(failure.getMessage().contains("line 1"));
    }

    @Test
    void discoveryWalksUpToTheNearestStyleFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        Path nested = Files.createDirectories(root.resolve("module/src/main/java"));

        assertEquals(root.resolve("formatj.toml"), StyleFiles.discover(nested).orElseThrow());
        assertEquals(6, StyleFiles.discoverOrDefault(nested).get(IndentRules.SIZE));
    }

    @Test
    void discoveryFallsBackToDefaultsWhenThereIsNoFile(@TempDir Path root) {
        assertEquals(4, StyleFiles.discoverOrDefault(root).get(IndentRules.SIZE));
    }

}
