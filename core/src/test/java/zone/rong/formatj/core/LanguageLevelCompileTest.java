package zone.rong.formatj.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.TextBlockRules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles what each gated rule writes with the JDK of the release it was formatted for.
 *
 * <p>Every gate is checked from both sides. The output for a release compiles on that release's JDK,
 * and the output for the gate's minimum does not compile one release earlier, which is what makes the
 * minimum the right one. Run by the {@code languageLevelTest} task, which hands in each {@code javac}.
 */
class LanguageLevelCompileTest {

    private static final Style ARROW = Style.builder().set(SwitchRules.CASE_STYLE, SwitchCaseStyle.ARROW).build();
    private static final Style ESCAPE = Style.builder().set(TextBlockRules.ESCAPE_TRAILING_SPACES, true).build();
    private static final Style REMOVE = Style.builder().set(ModifierRules.REMOVE_REDUNDANT, true).build();

    private static final String SWITCH = """
        class T {

            int f(int n) {
                switch (n) {
                    case 1:
                        n++;
                        break;
                    default:
                        n--;
                }
                return n;
            }

        }
        """;

    private static final String TEXT_BLOCK = "class T {\n\n    String s = \"\"\"\n        a  \n        \"\"\";\n\n}\n";

    private static final String SAFE_VARARGS = """
        class T {

            @SafeVarargs
            private final <E> void f(E... elements) { }

        }
        """;

    @TempDir Path directory;

    private static String format(String source, Style style, LanguageLevel level, boolean preview) {
        return FormatJ.newFormatter().style(style).languageLevel(level).previewFeatures(preview).build().format(source);
    }

    private boolean compiles(String source, int release, boolean preview) throws IOException, InterruptedException {
        Path file = Files.writeString(directory.resolve("T.java"), source);
        List<String> command = new ArrayList<>(List.of(
            System.getProperty("formatj.javac." + release),
            "-d",
            directory.toString()
        ));
        if (preview) {
            command.addAll(List.of("--enable-preview", "-source", String.valueOf(release)));
        }
        command.add(file.toString());
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor() ==
            0;
    }

    @Test
    void arrowCasesCompileFromJava14() throws IOException, InterruptedException {
        String arrow = format(SWITCH, ARROW, LanguageLevel.JAVA_14, false);
        assertTrue(arrow.contains("->"));

        assertTrue(compiles(format(SWITCH, ARROW, LanguageLevel.JAVA_8, false), 8, false));
        assertTrue(compiles(format(SWITCH, ARROW, LanguageLevel.JAVA_13, false), 13, false));
        assertFalse(compiles(arrow, 13, false));
        assertTrue(compiles(format(SWITCH, ARROW, LanguageLevel.JAVA_13, true), 13, true));
        assertTrue(compiles(arrow, 14, false));
    }

    @Test
    void theSpaceEscapeCompilesFromJava15() throws IOException, InterruptedException {
        String escaped = format(TEXT_BLOCK, ESCAPE, LanguageLevel.JAVA_14, true);
        assertTrue(escaped.contains("\\s"));

        assertTrue(compiles(format(TEXT_BLOCK, ESCAPE, LanguageLevel.JAVA_13, true), 13, true));
        assertFalse(compiles(escaped, 13, true));
        assertTrue(compiles(escaped, 14, true));
        assertTrue(compiles(format(TEXT_BLOCK, ESCAPE, LanguageLevel.JAVA_15, false), 15, false));
    }

    @Test
    void aPrivateSafeVarargsMethodCompilesWithoutFinalFromJava9() throws IOException, InterruptedException {
        String stripped = format(SAFE_VARARGS, REMOVE, LanguageLevel.JAVA_9, false);
        assertFalse(stripped.contains("final"));

        assertTrue(compiles(format(SAFE_VARARGS, REMOVE, LanguageLevel.JAVA_8, false), 8, false));
        assertFalse(compiles(stripped, 8, false));
        assertTrue(compiles(stripped, 9, false));
    }

}
