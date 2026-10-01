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
import zone.rong.formatj.core.pipeline.TokenEquivalence;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * <p>A fixture is either one {@code .java} file, or a directory of them that must compile as a
 * single unit, such as a {@code module-info.java} alongside the packages it exports. Either way,
 * each is compiled with {@code javac} to prove the source itself is valid, parsed at the newest
 * language level to prove FormatJ understands the construct completely, formatted and recompiled
 * to catch a rewrite that produced invalid Java, and formatted a second time to prove the result
 * is a fixed point.
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

        try (Stream<Path> entries = Files.list(FIXTURES)) {
            List<Path> fixtures = entries.sorted().toList();
            assertTrue(!fixtures.isEmpty(), "no fixtures found under " + FIXTURES);
            return fixtures.stream()
                    .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                        if (Files.isDirectory(path)) {
                            testDirectoryFixture(compiler, path);
                        } else {
                            testSingleFileFixture(compiler, path);
                        }
                    }));
        }
    }

    /** A lone {@code .java} file, compiled and formatted on its own. */
    private static void testSingleFileFixture(JavaCompiler compiler, Path path) throws IOException {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertCompiles(compiler, List.of(new StringSource(path.getFileName().toString(), source)));

        ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
        assertTrue(!parsed.hasErrors(), () -> "parse errors in " + path + ": " + parsed.diagnostics());
        assertTrue(parsed.complete(), () -> "unparsed regions remain in " + path);

        Formatter formatter = FormatJ.defaultFormatter();
        FormatResult once = formatter.format(FormatRequest.of(source).withName(path.toString()));
        assertTrue(!once.hasErrors(), () -> "formatting failed: " + once.diagnostics());
        assertTrue(
                TokenEquivalence.equivalent(source, once.text()),
                () -> "formatting changed the program: " + TokenEquivalence.firstDifference(source, once.text()));
        assertCompiles(compiler, List.of(new StringSource(path.getFileName().toString(), once.text())));

        FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName(path.toString()));
        assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
    }

    /**
     * A directory of {@code .java} files that only compile together, such as a {@code
     * module-info.java} exporting packages declared alongside it.
     */
    private static void testDirectoryFixture(JavaCompiler compiler, Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertTrue(!files.isEmpty(), "no .java files found under " + directory);

        List<String> sources = new ArrayList<>(files.size());
        for (Path file : files) {
            sources.add(Files.readString(file, StandardCharsets.UTF_8));
        }
        assertCompiles(compiler, unitsOf(files, sources));

        Formatter formatter = FormatJ.defaultFormatter();
        List<String> formattedOnce = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String source = sources.get(i);
            ParseResult parsed = JavaParser.parse(source, LanguageLevel.LATEST, false);
            assertTrue(!parsed.hasErrors(), () -> "parse errors in " + file + ": " + parsed.diagnostics());
            assertTrue(parsed.complete(), () -> "unparsed regions remain in " + file);

            FormatResult once = formatter.format(FormatRequest.of(source).withName(file.toString()));
            assertTrue(!once.hasErrors(), () -> "formatting failed in " + file + ": " + once.diagnostics());
            assertTrue(
                    TokenEquivalence.equivalent(source, once.text()),
                    () -> "formatting changed the program in " + file + ": "
                            + TokenEquivalence.firstDifference(source, once.text()));
            formattedOnce.add(once.text());
        }
        assertCompiles(compiler, unitsOf(files, formattedOnce));

        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String formatted = formattedOnce.get(i);
            FormatResult twice = formatter.format(FormatRequest.of(formatted).withName(file.toString()));
            assertEquals(formatted, twice.text(), () -> "formatting must be a fixed point: " + file);
        }
    }

    private static List<JavaFileObject> unitsOf(List<Path> files, List<String> sources) {
        List<JavaFileObject> units = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            units.add(new StringSource(files.get(i).getFileName().toString(), sources.get(i)));
        }
        return units;
    }

    private static void assertCompiles(JavaCompiler compiler, List<JavaFileObject> units) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
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
                    units)
                    .call();
            List<Diagnostic<? extends JavaFileObject>> errors = diagnostics.getDiagnostics()
                    .stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .toList();
            assertTrue(success && errors.isEmpty(), () -> "javac errors: " + errors);
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
