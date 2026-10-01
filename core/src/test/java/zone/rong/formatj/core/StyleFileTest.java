package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.ModuleRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.config.FileSelection;
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
    void anArrayMayRunOverSeveralLinesAndCarryComments() {
        Style style =
                StyleFiles.parse(
                        """
                [imports]
                order = "ascending"
                groups = [
                    # first-party
                    "com.cleanroommc",
                    "*",        # everything else
                    "net.minecraft",
                    "java",
                ]
                """);
        assertEquals(
                List.of(List.of("com.cleanroommc"), List.of("*"), List.of("net.minecraft"), List.of("java")),
                style.get(ImportRules.GROUPS));
    }

    @Test
    void anUnterminatedArrayNamesItsKey() {
        TomlReader.TomlException thrown =
                assertThrows(TomlReader.TomlException.class, () -> TomlReader.read(
                        """
                        [imports]
                        groups = [
                            "java",
                        """));
        assertTrue(thrown.getMessage().contains("groups"));
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

                [module]
                blank-lines-between-directive-groups = 1

                [modifiers]
                order = "canonical"
                """);
        assertEquals(4, style.get(IndentRules.SIZE));
        assertEquals(100, style.get(WrappingRules.MAX_LINE_LENGTH));
        assertEquals(ChainPolicy.BREAK_WHEN_TOO_LONG, style.get(WrappingRules.CHAINED_CALLS));
        assertEquals(1, style.get(ModuleRules.BLANK_LINES_BETWEEN_DIRECTIVE_GROUPS));
        assertEquals(ModifierOrder.CANONICAL, style.get(ModifierRules.ORDER));
    }

    @Test
    void listValuesLoadAsLists() {
        Style style =
                StyleFiles.parse(
                        """
                [imports]
                groups = ["java", "zone.rong.formatj", "*"]
                """);
        assertEquals(
                List.of(List.of("java"), List.of("zone.rong.formatj"), List.of("*")),
                style.get(ImportRules.GROUPS));
    }

    @Test
    void malformedFilesFailWithTheLineNumber() {
        TomlReader.TomlException failure =
                assertThrows(TomlReader.TomlException.class, () -> TomlReader.read("[indent\nsize = 4\n"));
        assertEquals(1, failure.line());
        assertTrue(failure.getMessage().contains("line 1"));
    }

    @Test
    void loadingAMalformedStyleFileNamesTheFileAndLine(@TempDir Path root) throws IOException {
        Path file = root.resolve("formatj.toml");
        Files.writeString(file, "[indent\nsize = 4\n");

        TomlReader.TomlException failure = assertThrows(TomlReader.TomlException.class, () -> StyleFiles.load(file));
        assertTrue(failure.getMessage().contains(file.toString()), failure.getMessage());
        assertTrue(failure.getMessage().contains("line 1"), failure.getMessage());
    }

    @Test
    void literalStringsKeepAHashAndDropOnlyTheirQuotes() {
        Style style =
                StyleFiles.parse(
                        """
                        [comments]
                        off-marker = '#stop'
                        """);
        assertEquals("#stop", style.get(CommentRules.OFF_MARKER));
    }

    @Test
    void anEscapedBackslashBeforeAClosingQuoteDoesNotEatTheQuote() {
        Style style =
                StyleFiles.parse(
                        """
                        [comments]
                        off-marker = "a\\\\" # trailing comment
                        """);
        assertEquals("a\\", style.get(CommentRules.OFF_MARKER));
    }

    @Test
    void basicStringsDecodeShortAndLongUnicodeEscapes() {
        String shortForm = "\\" + "u0041";
        String longForm = "\\" + "U00000041";
        Style style = StyleFiles.parse("[comments]\noff-marker = \"" + shortForm + longForm + "\"\n");
        assertEquals("AA", style.get(CommentRules.OFF_MARKER));
    }

    @Test
    void duplicateTableHeadersAreRejected() {
        TomlReader.TomlException failure =
                assertThrows(
                        TomlReader.TomlException.class,
                        () -> TomlReader.read("[indent]\nsize = 4\n[indent]\nsize = 5\n"));
        assertTrue(failure.getMessage().contains("indent"), failure.getMessage());
    }

    @Test
    void aLeadingUtf8BomIsIgnored() {
        Map<String, String> values = TomlReader.read("﻿[indent]\nsize = 4\n");
        assertEquals("4", values.get("indent.size"));
    }

    @Test
    void integerValuesAcceptDigitGroupingUnderscores() {
        Style style =
                StyleFiles.parse(
                        """
                        [wrapping]
                        max-line-length = 1_000
                        """);
        assertEquals(1000, style.get(WrappingRules.MAX_LINE_LENGTH));
    }

    @Test
    void quotedKeysAreAccepted() {
        Style style =
                StyleFiles.parse(
                        """
                        [indent]
                        "size" = 6
                        """);
        assertEquals(6, style.get(IndentRules.SIZE));
    }

    @Test
    void inlineTablesExpandToDottedKeys() {
        Style style = StyleFiles.parse("indent = { size = 6 }\n");
        assertEquals(6, style.get(IndentRules.SIZE));
    }

    @Test
    void aLineLengthBelowOneNamesTheOption() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleFiles.parse(
                                """
                                [wrapping]
                                max-line-length = 0
                                """));
        assertTrue(failure.getMessage().contains("wrapping.max-line-length"), failure.getMessage());
    }

    @Test
    void aNegativeCountNamesTheOption() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StyleFiles.parse(
                                """
                                [blank-lines]
                                max-consecutive = -1
                                """));
        assertTrue(failure.getMessage().contains("blank-lines.max-consecutive"), failure.getMessage());
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

    @Test
    void filesIncludeAndExcludeDoNotLeakIntoTheStyleOrFailAsUnknownOptions() {
        Style style =
                StyleFiles.parse(
                        """
                        [files]
                        include = ["src/**"]
                        exclude = ["**/generated/**"]

                        [indent]
                        size = 2
                        """);
        assertEquals(2, style.get(IndentRules.SIZE));
    }

    @Test
    void fileSelectionIsRelativeToTheStyleFilesDirectory(@TempDir Path root) throws IOException {
        Path toml = root.resolve("formatj.toml");
        Files.writeString(
                toml,
                """
                [files]
                include = ["src/**"]
                exclude = ["**/generated/**"]
                """);
        FileSelection selection = StyleFiles.fileSelection(toml);

        assertTrue(selection.matches(root.resolve("src/main/A.java")));
        assertTrue(selection.matches(root.resolve("./src/main/A.java")));
        assertFalse(selection.matches(root.resolve("other/A.java")));
        assertFalse(selection.matches(root.resolve("src/generated/A.java")));
    }

}
