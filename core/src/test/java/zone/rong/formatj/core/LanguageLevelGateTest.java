package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.TextBlockRules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A rule that writes syntax leaves the code as written below the release that syntax arrived in. */
class LanguageLevelGateTest {

    private static final String SWITCH = """
        class T {

            void f(int n) {
                switch (n) {
                    case 1:
                        g(n);
                        break;
                    default:
                        h(n);
                }
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

    private static String format(String source, Style style, LanguageLevel level, boolean preview) {
        return FormatJ.newFormatter().style(style).languageLevel(level).previewFeatures(preview).build().format(source);
    }

    @Test
    void aJava8ProjectKeepsItsColonCasesUnderTheDefaultStyle() {
        Style arrow = Style.builder().set(SwitchRules.CASE_STYLE, SwitchCaseStyle.ARROW).build();
        assertFalse(format(SWITCH, arrow, LanguageLevel.JAVA_8, false).contains("->"));
    }

    @Test
    void arrowCasesAreWrittenFromJava14() {
        Style arrow = Style.builder().set(SwitchRules.CASE_STYLE, SwitchCaseStyle.ARROW).build();
        assertFalse(format(SWITCH, arrow, LanguageLevel.JAVA_13, false).contains("->"));
        assertTrue(format(SWITCH, arrow, LanguageLevel.JAVA_13, true).contains("->"));
        assertTrue(format(SWITCH, arrow, LanguageLevel.JAVA_14, false).contains("->"));
    }

    @Test
    void theSpaceEscapeIsWrittenFromJava15() {
        Style escape = Style.builder().set(TextBlockRules.ESCAPE_TRAILING_SPACES, true).build();
        assertFalse(format(TEXT_BLOCK, escape, LanguageLevel.JAVA_14, false).contains("\\s"));
        assertTrue(format(TEXT_BLOCK, escape, LanguageLevel.JAVA_15, false).contains("\\s"));
    }

    @Test
    void aPrivateSafeVarargsMethodLosesFinalFromJava9() {
        Style remove = Style.builder().set(ModifierRules.REMOVE_REDUNDANT, true).build();
        assertTrue(format(SAFE_VARARGS, remove, LanguageLevel.JAVA_8, false).contains("final"));
        assertFalse(format(SAFE_VARARGS, remove, LanguageLevel.JAVA_9, false).contains("final"));
    }

}
