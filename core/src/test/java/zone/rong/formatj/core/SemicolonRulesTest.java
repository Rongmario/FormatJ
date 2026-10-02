package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemicolonRulesTest {

    private static final Style ON = Style.builder().semicolons(semicolons -> semicolons.removeRedundant(true)).build();

    private static String format(String source, Style style) {
        return FormatJ.newFormatter().style(style).build().format(source);
    }

    private static String format(String source) {
        return format(source, ON);
    }

    @Test
    void strayTypeAndMemberSemicolonsAreRemoved() {
        String source = "class T {\n\n    int a;;\n\n    ;\n    void f() {\n    }\n    ;\n\n    interface I {\n        ;\n    }\n\n}\n;;\n";
        String formatted = format(source);
        assertEquals(-1, formatted.indexOf(";;"), formatted);
        assertEquals(formatted, format(formatted));
        assertTrue(formatted.contains("int a;"), formatted);
        assertTrue(formatted.trim().endsWith("}"), formatted);
    }

    @Test
    void strayEnumSemicolonsAreRemovedOnceMembersFollowTheConstants() {
        String formatted = format("enum E {\n    A, B;\n    ;\n    void f() {\n    }\n    ;\n}\n");
        assertEquals(1, formatted.chars().filter(c -> c == ';').count(), formatted);
    }

    @Test
    void semicolonsWithMeaningAreKept() {
        String source = """
                enum E {
                    A, B;
                }

                enum F {
                    A;
                    ;
                }

                class T {

                    void f() {
                        ;
                        for (;;) {
                        }
                    }

                }
                """;
        assertEquals(source, format(source));
    }

    @Test
    void semicolonCarryingACommentIsKept() {
        String source = "class T {\n\n    int a; /* keep */ ;\n    // note\n    ;\n\n}\n";
        String formatted = format(source);
        assertTrue(formatted.contains("keep") && formatted.contains("note"), formatted);
    }

    @Test
    void semicolonsAreRemovedByDefault() {
        String formatted = format("class T {\n\n    ;\n\n}\n;\n", Style.builder().build());
        assertEquals(0, formatted.chars().filter(c -> c == ';').count(), formatted);
    }

}
