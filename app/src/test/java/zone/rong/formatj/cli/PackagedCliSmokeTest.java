package zone.rong.formatj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the installed CLI launcher, not {@link CliRunner} in-process.
 *
 * <p>The launcher is the distribution entry point {@code bin/formatj}. Two invocations of the same
 * arguments must agree, which is the smoke that a packaged build is not a one-shot accident.
 */
class PackagedCliSmokeTest {

    private static Path launcher() {
        String path = System.getProperty("formatj.launcher");
        assertTrue(path != null && !path.isBlank(), "formatj.launcher system property must point at bin/formatj");
        Path file = Path.of(path);
        assertTrue(Files.isRegularFile(file), () -> "missing launcher: " + file);
        assertTrue(Files.isExecutable(file), () -> "launcher is not executable: " + file);
        return file;
    }

    @Test
    void dumpConfigRunsTwiceWithTheSameCatalogue() throws Exception {
        Run first = run(List.of("--dump-config"));
        Run second = run(List.of("--dump-config"));

        assertEquals(0, first.exitCode, first.err);
        assertEquals(0, second.exitCode, second.err);
        assertEquals(first.out, second.out);
        assertTrue(first.out.contains("[indent]"), first.out);
        assertTrue(first.out.contains("size = 4"), first.out);
        assertTrue(first.out.contains("max-line-length = 120"), first.out);
        assertTrue(first.out.contains("[text-blocks]"), first.out);
    }

    @Test
    void writeThenCheckIsAFixedPointOnASmallFile(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("Sample.java");
        Files.writeString(source, "package sample;class Sample{void run(){int x=1;}}\n", StandardCharsets.UTF_8);

        Run first = run(List.of("--write", source.toString()));
        assertEquals(0, first.exitCode, first.err);
        String formatted = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(formatted.contains("int x = 1;"), formatted);
        assertTrue(formatted.contains("class Sample"), formatted);

        Run second = run(List.of("--write", source.toString()));
        assertEquals(0, second.exitCode, second.err);
        assertEquals(formatted, Files.readString(source, StandardCharsets.UTF_8));

        Run check = run(List.of("--check", source.toString()));
        assertEquals(0, check.exitCode, check.err);
    }

    private static Run run(List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(launcher().toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        Path outFile = Files.createTempFile("formatj-cli-", ".out");
        Path errFile = Files.createTempFile("formatj-cli-", ".err");
        try {
            builder.redirectOutput(outFile.toFile());
            builder.redirectError(errFile.toFile());
            Process process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException("launcher timed out: " + command + "\n" + Files.readString(errFile));
            }
            return new Run(
                    process.exitValue(),
                    Files.readString(outFile, StandardCharsets.UTF_8),
                    Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    private record Run(int exitCode, String out, String err) { }

}
