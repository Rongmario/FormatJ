package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Fixtures under {@code src/test/resources/jls25} are standalone Java files a JDK accepts.
 *
 * <p>Each one is compiled with {@code javac} to prove the source itself is valid, parsed at the
 * newest language level to prove FormatJ understands the construct completely, formatted and
 * recompiled to catch a rewrite that produced invalid Java, and formatted a second time to prove
 * the result is a fixed point.
 */
class Jls25FixtureTest {

    private static final Path FIXTURES = Path.of("src/test/resources/jls25");

    @TestFactory
    Stream<DynamicTest> everyFixtureCompilesParsesAndFormats() throws IOException {
        assertTrue(Files.isDirectory(FIXTURES), () -> "missing fixture directory: " + FIXTURES.toAbsolutePath());
        assumeTrue(
                Runtime.version().feature() >= 25,
                () -> "this JDK does not support --release 25: " + Runtime.version());
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no system Java compiler available");

        try (Stream<Path> files = Files.list(FIXTURES)) {
            List<Path> fixtures = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            assertTrue(!fixtures.isEmpty(), "no fixtures found under " + FIXTURES);
            return fixtures.stream().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                assertCompiles(compiler, path.getFileName().toString(), source);

                ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
                assertTrue(!parsed.hasErrors(), () -> "parse errors in " + path + ": " + parsed.diagnostics());
                assertTrue(parsed.complete(), () -> "unparsed regions remain in " + path);

                Formatter formatter = FormatJ.defaultFormatter();
                FormatResult once = formatter.format(FormatRequest.of(source).withName(path.toString()));
                assertTrue(!once.hasErrors(), () -> "formatting failed: " + once.diagnostics());
                assertCompiles(compiler, path.getFileName().toString(), once.text());

                FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
                assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
            }));
        }
    }

    private static void assertCompiles(JavaCompiler compiler, String fileName, String source) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject unit = new StringSource(fileName, source);
        try (
                StandardJavaFileManager base = compiler.getStandardFileManager(
                        diagnostics,
                        null,
                        StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(
                    null,
                    new DiscardingOutputFileManager(base),
                    diagnostics,
                    List.of("--release", "25", "-proc:none"),
                    null,
                    List.of(unit))
                    .call();
            List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics()
                    .stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .toList();
            assertTrue(success && errors.isEmpty(), () -> "javac errors in " + fileName + ": " + errors);
        }
    }

    /** A compilation unit backed by a string, so a fixture is handed to javac without touching disk. */
    private static final class StringSource extends SimpleJavaFileObject {

        private final String source;

        StringSource(String name, String source) {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

    }

    /** Discards compiled class output rather than writing it to disk. */
    private static final class DiscardingOutputFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        DiscardingOutputFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) {
            return new SimpleJavaFileObject(
                    URI.create("mem:///" + className.replace('.', '/') + kind.extension),
                    kind) {

                @Override
                public OutputStream openOutputStream() {
                    return OutputStream.nullOutputStream();
                }

            };
        }

    }

}
