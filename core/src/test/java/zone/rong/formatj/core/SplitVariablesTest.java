package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 5: the {@code declarations.split-variables} rule, which splits a declaration of several
 * variables into one declaration per variable, repeating the modifiers, annotations and type.
 */
class SplitVariablesTest {

    private static final Style ON = Style.builder().declarations(d -> d.splitVariables(true)).build();

    private static String format(String source, Style style) {
        FormatResult result = FormatJ.newFormatter().style(style).build().format(FormatRequest.of(source).withName("T.java"));
        assertTrue(result.diagnostics().isEmpty(), () -> result.diagnostics().toString());
        return result.text();
    }

    private static String format(String source) {
        return format(source, ON);
    }

    @Test
    void fieldsAndLocalsSplitIntoOneDeclarationPerVariable() {
        String formatted = format("""
                class A {
                    int a, b = 2, c;
                    void f() {
                        int x, y = 1, z;
                    }
                }
                """);
        assertEquals(
            """
            class A {

                int a;
                int b = 2;
                int c;

                void f() {
                    int x;
                    int y = 1;
                    int z;
                }

            }
            """,
            formatted
        );
        assertEquals(formatted, format(formatted));
    }

    @Test
    void commentsOnTheHeadPrintOnceOnTheFirstDeclaration() {
        String formatted = format("""
                class A {
                    /** Docs for a. */
                    @Anno int a, b = 2, c[]; // trailing
                }
                """);
        assertEquals(
            """
            class A {

                /** Docs for a. */
                @Anno int a;
                @Anno int b = 2;
                @Anno int[] c; // trailing

            }
            """,
            formatted
        );
    }

    @Test
    void dimensionsWrittenOnTheNameBelongToThatVariable() {
        String formatted = format("""
                class A {
                    int a[], b;
                    int d[], e[];
                    int[] f[], g[];
                }
                """);
        assertEquals(
            """
            class A {

                int[] a;
                int b;
                int[] d;
                int[] e;
                int[][] f;
                int[][] g;

            }
            """,
            formatted
        );
        assertEquals(formatted, format(formatted));
    }

    @Test
    void aCommentBetweenTheCommaAndTheNextDeclaratorIsKeptAndTheDeclarationStillSplits() {
        String formatted = format("class A {\n    int a, /* mid */ b;\n}\n");
        assertTrue(formatted.contains("int /* mid */ b;"), formatted);
    }

    @Test
    void aCommentOnTheCommaDeclinesTheSplit() {
        String formatted = format("class A {\n    int a /* why */, b;\n}\n");
        assertTrue(formatted.contains("int a /* why */, b;"), formatted);
    }

    @Test
    void aForInitDeclarationIsNotSplit() {
        String formatted = format("""
                class A {
                    void f() {
                        for (int i = 0, j = 1; i < j; i++) {
                        }
                    }
                }
                """);
        assertTrue(formatted.contains("for (int i = 0, j = 1; i < j; i++)"), formatted);
        assertEquals(formatted, format(formatted));
    }

    @Test
    void theRuleIsOffByDefault() {
        String formatted = format("class A {\n    int a, b = 2, c;\n}\n", Style.defaults());
        assertTrue(formatted.contains("int a, b = 2, c;"), formatted);
    }

}
