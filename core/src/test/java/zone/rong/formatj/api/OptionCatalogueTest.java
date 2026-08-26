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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
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

    @Test
    void everyReadmeRowDefaultAndLegalValuesMatchTheCatalogue() {
        String readme = readmeText();
        List<String> problems = readmeProblems(readme);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    @Test
    void enumValuesFromAnotherGlossaryDoNotSatisfyARow() {
        String readme = readmeText();
        String corrupted =
                readme.replace(
                        "| `wrapping.method-parameters`                     | `WrapPolicy`",
                        "| `wrapping.method-parameters`                     | `BracePolicy`");
        assertFalse(corrupted.equals(readme), "fixture row was not replaced");

        List<String> problems = readmeProblems(corrupted);
        assertTrue(
                problems.stream().anyMatch(problem -> problem.startsWith("wrapping.method-parameters")),
                () -> String.join("\n", problems));
    }

    private static List<String> readmeProblems(String readme) {
        List<ReadmeRow> rows = readmeRows(readme);
        List<String> problems = new ArrayList<>();
        for (Option<?> option : OptionRegistry.all()) {
            ReadmeRow row = null;
            for (ReadmeRow candidate : rows) {
                if (candidate.key.equals(option.key())) {
                    row = candidate;
                    break;
                }
            }
            if (row == null) {
                problems.add(option.key() + " has no README table row");
                continue;
            }
            String documented = stripTicks(row.defaultValue);
            String rendered = renderedDefault(option);
            if (!defaultsMatch(documented, rendered)) {
                problems.add(option.key() + " README default '" + documented + "' != catalogue '" + rendered + "'");
            }
            if (option.kind() == Option.Kind.ENUM) {
                List<String> documentedValues = documentedEnumValues(option, row, readme);
                if (!new HashSet<>(documentedValues).equals(new HashSet<>(option.allowedValues()))) {
                    problems.add(
                            option.key() + " README values " + documentedValues + " != catalogue "
                                    + option.allowedValues());
                }
            } else if (option.kind() == Option.Kind.BOOLEAN) {
                if (!row.values.isEmpty()
                        && !row.values.toLowerCase(Locale.ROOT).contains("boolean")
                        && !row.values.contains("true")) {
                    problems.add(option.key() + " README values '" + row.values + "' do not name boolean");
                }
            }
        }
        return problems;
    }

    private static <T> String renderedDefault(Option<T> option) {
        return option.render(option.defaultValue());
    }

    private static boolean defaultsMatch(String documented, String rendered) {
        if (documented.equals(rendered) || documented.equals(stripQuotes(rendered))) {
            return true;
        }
        return stripQuotes(documented).equals(stripQuotes(rendered));
    }

    private static List<String> documentedEnumValues(Option<?> option, ReadmeRow row, String readme) {
        List<String> values = codeTokens(row.values);
        String typeName = option.type().getSimpleName();
        if (!values.isEmpty() && !values.equals(List.of(typeName))) {
            return values;
        }
        Matcher glossary = Pattern.compile(
                "`" + Pattern.quote(typeName) + "` values are (.*?)(?:\\.(?:\\s|$)|\\R\\s*\\R)",
                Pattern.DOTALL)
                .matcher(readme);
        return glossary.find() ? codeTokens(glossary.group(1)) : List.of();
    }

    private static List<String> codeTokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile("`([^`]+)`").matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group(1));
        }
        return List.copyOf(tokens);
    }

    private static Set<String> readmeTableKeys() {
        Set<String> keys = new HashSet<>();
        for (ReadmeRow row : readmeRows(readmeText())) {
            keys.add(row.key);
        }
        return keys;
    }

    private static String readmeText() {
        try {
            return Files.readString(repositoryRoot().resolve("README.md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ReadmeRow(String key, String values, String defaultValue) { }

    private static List<ReadmeRow> readmeRows(String readme) {
        List<ReadmeRow> rows = new ArrayList<>();
        int keyCol = -1;
        int valuesCol = -1;
        int defaultCol = -1;
        for (String line : readme.split("\n", -1)) {
            if (!line.startsWith("|")) {
                keyCol = -1;
                valuesCol = -1;
                defaultCol = -1;
                continue;
            }
            List<String> cells = tableCells(line);
            if (headerIndex(cells, "Key") >= 0 && headerIndex(cells, "Default") >= 0) {
                keyCol = headerIndex(cells, "Key");
                valuesCol = headerIndex(cells, "Values");
                defaultCol = headerIndex(cells, "Default");
                continue;
            }
            if (keyCol < 0 || defaultCol < 0 || defaultCol >= cells.size() || keyCol >= cells.size()) {
                continue;
            }
            if (cells.stream().allMatch(cell -> cell.matches("[-: ]*"))) {
                continue;
            }
            Matcher key = Pattern.compile("`([a-z0-9.-]+)`").matcher(cells.get(keyCol));
            if (!key.find()) {
                continue;
            }
            String values = valuesCol >= 0 && valuesCol < cells.size() ? cells.get(valuesCol) : "";
            rows.add(new ReadmeRow(key.group(1), values, cells.get(defaultCol)));
        }
        return rows;
    }

    private static List<String> tableCells(String line) {
        String body = line;
        if (body.startsWith("|")) {
            body = body.substring(1);
        }
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : body.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private static int headerIndex(List<String> cells, String name) {
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String stripTicks(String text) {
        return text.replace("`", "").trim();
    }

    private static String stripQuotes(String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
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
        assertEquals(
                option.defaultValue(),
                option.parse(option.render(option.defaultValue())),
                () -> option.key() + " did not round-trip");
    }

    @Test
    void stringValuesWithPunctuationRoundTrip() {
        Option<String> option = firstOfKind(Option.Kind.STRING);
        for (String value : List.of("plain", "a, b", "say \"hi\"", "C:\\path", "  padded  ", "")) {
            assertEquals(value, option.parse(option.render(value)), () -> "did not round-trip: " + value);
        }
    }

    @Test
    void stringListElementsWithPunctuationRoundTrip() {
        Option<List<String>> option = firstOfKind(Option.Kind.STRING_LIST);
        List<String> value = List.of("java.*", "a, b", "say \"hi\"", "C:\\path");
        assertEquals(value, option.parse(option.render(value)));
    }

    @Test
    void unterminatedQuotesInAListAreRejected() {
        Option<List<String>> option = firstOfKind(Option.Kind.STRING_LIST);
        assertThrows(IllegalArgumentException.class, () -> option.parse("[\"a, b]"));
    }

    @SuppressWarnings("unchecked")
    private static <T> Option<T> firstOfKind(Option.Kind kind) {
        for (Option<?> option : OptionRegistry.all()) {
            if (option.kind() == kind) {
                return (Option<T>) option;
            }
        }
        throw new AssertionError("no option of kind " + kind);
    }

    @Test
    void groupsAreInCatalogueOrderWhicheverClassInitialisedFirst() {
        assertEquals(IndentRules.SIZE, OptionRegistry.require("indent.size"));
        assertEquals(
                List.of("file", "indent", "wrapping", "braces", "spacing", "blank-lines", "alignment"),
                OptionRegistry.groups().subList(0, 7));
        assertEquals(OptionRegistry.groups(), List.copyOf(OptionRegistry.groups()));
        List<String> keys = OptionRegistry.all().stream().map(Option::key).toList();
        assertEquals(List.copyOf(OptionRegistry.asMap().keySet()), keys);
        assertTrue(
                keys.indexOf("indent.size") < keys.indexOf("wrapping.max-line-length"),
                "indent.* must precede wrapping.* regardless of class initialisation order");
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
