package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.core.config.StyleFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * One test per directory under {@code src/test/resources/cases}.
 *
 * <p>A case is {@code input.java}, the {@code expected.java} it must format to, and an optional
 * {@code style.toml} holding the rules to format it with. Adding a case is adding files, never code,
 * which is what keeps the regression suite cheap to grow as rules land.
 */
class GoldenFileTest {

    private static final Path CASES = Path.of("src/test/resources/cases");

    @TestFactory
    Stream<DynamicTest> everyCaseFormatsToItsExpectedOutput() throws IOException {
        assertTrue(Files.isDirectory(CASES), () -> "missing case directory: " + CASES.toAbsolutePath());
        try (Stream<Path> directories = Files.list(CASES)) {
            List<Path> cases = directories.filter(Files::isDirectory).sorted().toList();
            return cases.stream().map(directory -> DynamicTest.dynamicTest(directory.getFileName().toString(), () -> {
                String input = read(directory.resolve("input.java"));
                String expected = read(directory.resolve("expected.java"));
                Formatter formatter = FormatJ.newFormatter().style(styleFor(directory)).build();

                String formatted = formatter.format(FormatRequest.of(input).withName(directory + "/input.java")).text();
                assertEquals(expected, formatted);

                String again = formatter.format(FormatRequest.of(formatted)).text();
                assertEquals(formatted, again, "formatting must be a fixed point");
            }));
        }
    }

    private static Style styleFor(Path directory) {
        Path styleFile = directory.resolve("style.toml");
        return Files.isRegularFile(styleFile) ? StyleFiles.load(styleFile) : Style.defaults();
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

}
