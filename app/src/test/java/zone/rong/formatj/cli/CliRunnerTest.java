package zone.rong.formatj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliRunnerTest {

    private static final String SOURCE = """
            package sample;

            class A {
                void run() {}
            }
            """;

    private record Run(int exitCode, String out, String err) {}

    private static Run run(String stdin, String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        InputStream in = new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
        int exitCode =
                new CliRunner(
                        CliOptions.parse(arguments),
                        new PrintStream(out, true, StandardCharsets.UTF_8),
                        new PrintStream(err, true, StandardCharsets.UTF_8),
                        in).run();
        return new Run(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void dumpConfigPrintsTheWholeCatalogue() {
        Run result = run("", "--dump-config");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("[indent]"), result.out());
        assertTrue(result.out().contains("size = 4"), result.out());
        assertTrue(result.out().contains("[switch]"), result.out());
        assertTrue(result.out().contains("max-line-length = 120"), result.out());
    }

    @Test
    void dumpConfigHonoursThePresetAndOverrides() {
        Run result = run("", "--dump-config", "--preset", "google", "--set", "indent.size=3");

        assertTrue(result.out().contains("size = 3"), result.out());
        assertTrue(result.out().contains("max-line-length = 100"), result.out());
    }

    @Test
    void checkingAnAlreadyFormattedTreeSucceeds(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("A.java"), SOURCE);

        Run result = run("", "--check", root.toString());

        assertEquals(0, result.exitCode());
    }

    @Test
    void standardInputIsEchoedThroughTheFormatter() {
        Run result = run(SOURCE, "--stdin", "--stdin-name", "A.java");

        assertEquals(0, result.exitCode());
        assertEquals(SOURCE, result.out());
    }

    @Test
    void missingPathsAreReportedAsAnError(@TempDir Path root) {
        Run result = run("", "--check", root.resolve("absent").toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("no such file or directory"), result.err());
    }

    @Test
    void excludeGlobsSkipFiles(@TempDir Path root) throws IOException {
        Path generated = Files.createDirectories(root.resolve("generated"));
        Files.writeString(generated.resolve("A.java"), SOURCE);

        Run result = run("", "--check", "--exclude", "**/generated/**", "--verbose", root.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.err().contains("no Java sources matched"), result.err());
    }

    @Test
    void verboseReportsEveryFileAndTheTally(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("A.java"), SOURCE);

        Run result = run("", "--check", "--verbose", root.toString());

        assertTrue(result.out().contains("unchanged"), result.out());
        assertTrue(result.err().contains("1 files, 0 changed, 0 failed"), result.err());
    }

    @Test
    void aStyleFileNextToTheSourceIsDiscovered(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 2\n");
        Files.writeString(root.resolve("A.java"), SOURCE);

        Run check = run("", "--check", root.toString());
        assertEquals(1, check.exitCode(), "four-space source does not match a two-space style");

        Run write = run("", "--write", root.toString());
        assertEquals(0, write.exitCode());
        assertTrue(
                Files.readString(root.resolve("A.java")).contains("\n  void run()"),
                Files.readString(root.resolve("A.java")));
    }

    @Test
    void writingReformatsAndCheckingThenPasses(@TempDir Path root) throws IOException {
        Path file = root.resolve("A.java");
        Files.writeString(file, "package sample;\nclass A{void run(){g();}}\n");

        assertEquals(1, run("", "--check", root.toString()).exitCode());
        assertEquals(0, run("", "--write", root.toString()).exitCode());
        assertEquals(
                """
                package sample;

                class A {
                    void run() {
                        g();
                    }
                }
                """,
                Files.readString(file));
        assertEquals(0, run("", "--check", root.toString()).exitCode());
    }

    @Test
    void diffModeReportsWhatWouldChangeWithoutTouchingTheFile(@TempDir Path root) throws IOException {
        Path file = root.resolve("A.java");
        String before = "package sample;\nclass A{}\n";
        Files.writeString(file, before);

        Run result = run("", "--diff", root.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("+class A {}"), result.out());
        assertEquals(before, Files.readString(file));
    }

    @Test
    void helpAndVersionExitCleanly() {
        assertEquals(0, run("", "--help").exitCode());
        assertTrue(run("", "--version").out().startsWith("formatj "));
    }

}
