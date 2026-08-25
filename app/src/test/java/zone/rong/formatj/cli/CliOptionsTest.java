package zone.rong.formatj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliOptionsTest {

    @Test
    void checkingIsTheDefaultMode() {
        CliOptions options = CliOptions.parse(new String[] {"src"});
        assertEquals(CliOptions.Mode.CHECK, options.mode());
        assertEquals(List.of(Path.of("src")), options.paths());
    }

    @Test
    void modesAndStyleFlagsParse() {
        CliOptions options =
                CliOptions.parse(
                        new String[] {
                            "--write",
                            "--preset",
                            "google",
                            "--set",
                            "indent.size=2",
                            "--set",
                            "wrapping.max-line-length=100",
                            "--language-level",
                            "21",
                            "--preview",
                            "-j",
                            "3",
                            "--include",
                            "**/*.java",
                            "--exclude",
                            "**/generated/**",
                            "src",
                            "test"
                        });

        assertEquals(CliOptions.Mode.WRITE, options.mode());
        assertEquals(Preset.GOOGLE, options.preset().orElseThrow());
        assertEquals(Map.of("indent.size", "2", "wrapping.max-line-length", "100"), options.overrides());
        assertEquals(LanguageLevel.JAVA_21, options.languageLevel());
        assertTrue(options.previewFeatures());
        assertEquals(3, options.parallelism());
        assertEquals(List.of("**/*.java"), options.includes());
        assertEquals(List.of("**/generated/**"), options.excludes());
        assertEquals(2, options.paths().size());
    }

    @Test
    void stdinDefaultsToWritingTheFormattedSourceOut() {
        CliOptions options = CliOptions.parse(new String[] {"--stdin-name", "Foo.java"});
        assertTrue(options.readStdin());
        assertEquals("Foo.java", options.stdinName());
        assertEquals(CliOptions.Mode.WRITE, options.mode());
    }

    @Test
    void dumpConfigNeedsNoPaths() {
        CliOptions options = CliOptions.parse(new String[] {"--dump-config"});
        assertEquals(CliOptions.Mode.DUMP_CONFIG, options.mode());
        assertTrue(options.paths().isEmpty());
        assertFalse(options.readStdin());
    }

    @Test
    void malformedCommandLinesAreRejected() {
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--nope", "src"}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--set", "indent.size"}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--style"}));
        assertThrows(CliOptions.CliException.class, () -> CliOptions.parse(new String[] {"--jobs", "many", "src"}));
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[] {"--preset", "eclipse", "s"}));
    }

    @Test
    void usageDocumentsEveryMode() {
        String usage = CliOptions.usage();
        for (String flag : List.of("--check", "--write", "--diff", "--dump-config", "--stdin", "--set", "--preset")) {
            assertTrue(usage.contains(flag), () -> "usage does not mention " + flag);
        }
    }

}
