package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.lexer.JavaLexer;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import zone.rong.formatj.core.pipeline.TokenEquivalence;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ExternalCorpusInvariantTest {

    private static final Set<String> EXPECTED_SAFE_FAILURES =
            Set.of(
                    "com/google/gson/internal/ConstructorConstructor.java",
                    "com/google/gson/internal/bind/ArrayTypeAdapter.java",
                    "com/google/gson/internal/bind/MapTypeAdapterFactory.java",
                    "com/google/gson/internal/bind/ReflectiveTypeAdapterFactory.java",
                    "com/google/gson/reflect/TypeToken.java");

    @Test
    void parserCompletelyCoversAtLeastNinetyPercentOfTheCorpus() throws IOException {
        List<Path> sources = sources(root());
        long complete = sources.stream()
                .filter(path -> {
                    try {
                        String source = Files.readString(path, StandardCharsets.UTF_8);
                        ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
                        return !parsed.hasErrors() && parsed.complete();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .count();
        assertTrue(
                complete * 10 >= sources.size() * 9,
                () -> "complete parser coverage fell below 90%: " + complete + "/" + sources.size());
    }

    @TestFactory
    Stream<DynamicTest> everyExternalSourceSatisfiesTheInvariants() throws IOException {
        Path root = root();
        List<Path> sources = sources(root);

        Formatter formatter = FormatJ.defaultFormatter();
        return sources.stream().map(path -> DynamicTest.dynamicTest(root.relativize(path).toString(), () -> {
            String relative = root.relativize(path).toString().replace('\\', '/');
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertEquals(source, JavaLexer.toSource(JavaLexer.tokenize(source)), "lexer must round-trip");

            ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
            assertEquals(source, parsed.root().text(), "tree must round-trip");

            FormatResult once = formatter.format(FormatRequest.of(source).withName(path.toString()));
            if (parsed.hasErrors()) {
                assertTrue(once.hasErrors(), () -> once.diagnostics().toString());
                assertEquals(source, once.text(), "a hard parse failure must leave the file unchanged");
                return;
            }
            if (once.hasErrors()) {
                assertTrue(EXPECTED_SAFE_FAILURES.contains(relative), () -> "new formatter failure in " + relative
                        + ": " + once.diagnostics());
                assertEquals(source, once.text(), "a failed recovery must leave the file unchanged");
                return;
            }
            assertFalse(EXPECTED_SAFE_FAILURES.contains(relative), () -> relative
                    + " now formats successfully; remove it from EXPECTED_SAFE_FAILURES");
            ParseResult formatted = JavaParser.parse(once.text(), LanguageLevel.LATEST, false);
            assertTrue(TokenEquivalence.firstDifference(parsed.root().green(), formatted.root().green())
                    == null, "formatting changed the program");

            FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
            assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
        }));
    }

    private static Path root() {
        Path root = Path.of(System.getProperty("formatj.external.corpus")).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), () -> "external corpus does not exist: " + root);
        return root;
    }

    private static List<Path> sources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sources = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            assertTrue(sources.size() > 10, () -> "external corpus is too small: " + sources.size());
            return sources;
        }
    }

}
