package zone.rong.formatj.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.core.FormatJ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatJEngineTest {

    @Test
    void discoveryWalksUpToTheNearestStyleFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        Path nested = Files.createDirectories(root.resolve("module/src/main/java"));
        FormatJEngine engine = new FormatJEngine(FormatJEngine.Settings.discover());

        assertEquals(6, engine.styleFor(nested.resolve("Foo.java")).get(IndentRules.SIZE));
        assertTrue(engine.describeStyle(nested).contains(root.resolve("formatj.toml").toString()));
    }

    @Test
    void anExplicitPresetSkipsDiscovery(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatJEngine engine = new FormatJEngine(new FormatJEngine.Settings(null, Preset.GOOGLE));

        assertEquals(2, engine.styleFor(root.resolve("Foo.java")).get(IndentRules.SIZE));
    }

    @Test
    void anExplicitStyleFileWins(@TempDir Path root) throws IOException {
        Path other = root.resolve("other.toml");
        Files.writeString(other, "[indent]\nsize = 8\n");
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatJEngine engine = new FormatJEngine(new FormatJEngine.Settings(other, null));

        assertEquals(8, engine.styleFor(root.resolve("Foo.java")).get(IndentRules.SIZE));
        assertTrue(engine.describeStyle(root).contains(other.toString()));
    }

    @Test
    void unchangedInputIsReportedUnchanged() {
        String source = FormatJ.defaultFormatter()
                .format(
                        """
                                class T {

                                    void run() { }

                                }
                                """);
        FormatJEngine.Outcome outcome = formatWhole(source);
        assertTrue(outcome.unchanged());
        assertEquals(source, outcome.text());
    }

    @Test
    void whitespaceOnlyDoesNotAddBraces(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[braces]\nif-else = \"always\"\n");
        Path file = root.resolve("T.java");
        String source = """
                class T {

                    void run(int n) {
                        if (n > 0) log(n);
                    }

                }
                """;
        FormatJEngine engine = new FormatJEngine(FormatJEngine.Settings.discover());
        FormatJEngine.Outcome withRewrites =
                engine.format(
                        new FormatJEngine.Request(
                                source,
                                "T.java",
                                file,
                                List.of(),
                                LanguageLevel.LATEST,
                                false,
                                true));
        FormatJEngine.Outcome withoutRewrites =
                engine.format(
                        new FormatJEngine.Request(
                                source,
                                "T.java",
                                file,
                                List.of(),
                                LanguageLevel.LATEST,
                                false,
                                false));
        assertTrue(withRewrites.text().contains("if (n > 0) {"), withRewrites.text());
        assertFalse(withoutRewrites.text().contains("if (n > 0) {"), withoutRewrites.text());
    }

    @Test
    void aRangeLeavesTheUnselectedMethodAlone() {
        String source = """
                class T {

                    void a() { int keep = 1; }

                    void b() { int value = 1+2+3+4+5+6+7+8+9+10+11+12+13+14+15+16+17+18+19+20; }

                }
                """;
        int start = source.indexOf("void b()");
        int end = source.indexOf('\n', start);
        FormatJEngine engine = new FormatJEngine(FormatJEngine.Settings.discover());
        FormatJEngine.Outcome whole = engine.format(request(source, List.of()));
        FormatJEngine.Outcome ranged = engine.format(request(source, List.of(new SourceRange(start, end))));

        assertFalse(whole.unchanged(), whole.text());
        assertTrue(whole.text().contains("void a() {"), whole.text());
        assertTrue(ranged.text().contains("void a() { int keep = 1; }"), ranged.text());
        assertFalse(ranged.text().contains("void b() { int value"), ranged.text());
    }

    @Test
    void wholeFileRangeSkipsSplicing() {
        String source = """
                class T {

                    void a() { int keep = 1; }

                }
                """;
        FormatJEngine.Outcome emptyRanges = formatWhole(source);
        FormatJEngine.Outcome covering =
                new FormatJEngine(FormatJEngine.Settings.discover()).format(
                        request(source, List.of(new SourceRange(0, source.length()))));
        assertEquals(emptyRanges.text(), covering.text());
    }

    private static FormatJEngine.Outcome formatWhole(String source) {
        return new FormatJEngine(FormatJEngine.Settings.discover()).format(request(source, List.of()));
    }

    private static FormatJEngine.Request request(String source, List<SourceRange> ranges) {
        return new FormatJEngine.Request(source, "T.java", null, ranges, LanguageLevel.LATEST, false, true);
    }

}
