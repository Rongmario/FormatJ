package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.WrapPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Issue 1: with {@code wrapping.closing-delimiter = "own-line"}, a parameter list that fits on its
 * own line but not together with the trailing {@code ) {} must wrap as a whole, the way it does when
 * the delimiter is attached — not leave every parameter on the first line and move only the
 * {@code )} down.
 */
class ClosingDelimiterTest {

    private static final String METHOD = """
            class A {

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(version, access, name, signature, superName, interfaces);
                }
            }
            """;

    private static String format(String source, ClosingDelimiter delimiter) {
        return format(source, style -> style.wrapping(
            wrapping -> wrapping.maxLineLength(117)
                .closingDelimiter(delimiter)
                .methodParameters(WrapPolicy.WRAP_IF_LONG)
                .breakAfterOpenParen(false)
        ));
    }

    private static String format(String source, java.util.function.Consumer<StyleBuilder> configure) {
        StyleBuilder builder = Style.builder();
        configure.accept(builder);
        return FormatJ.newFormatter().style(builder.build()).build().format(source);
    }

    @Test
    void anOwnLineClosingDelimiterWrapsTheListNotOnlyTheDelimiter() {
        String formatted = format(METHOD, ClosingDelimiter.OWN_LINE);
        assertEquals(
            """
            class A {

                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces
                ) {
                    super.visit(version, access, name, signature, superName, interfaces);
                }

            }
            """,
            formatted
        );
        assertEquals(formatted, format(formatted, ClosingDelimiter.OWN_LINE));
    }

    @Test
    void anAttachedClosingDelimiterStaysAtTheEndOfTheList() {
        String formatted = format(METHOD, ClosingDelimiter.ATTACHED);
        assertEquals(
            """
            class A {

                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {
                    super.visit(version, access, name, signature, superName, interfaces);
                }

            }
            """,
            formatted
        );
    }

    @Test
    void aListThatFitsTogetherWithTheDelimiterIsNotBroken() {
        String formatted = format(
            "class A {\n    public void visit(int version, String[] interfaces) {\n        super.visit(version, interfaces);\n    }\n}\n",
            ClosingDelimiter.OWN_LINE
        );
        assertFalse(formatted.contains("visit(int version, String[] interfaces\n"), formatted);
    }

    @Test
    void anArgumentListIsMeasuredTogetherWithItsClosingDelimiter() {
        String formatted = format(
            """
            class A {
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    someCall(version, access, name, signature, superName, interfaces, interfaces, version, version, more, args, here, a, b, c);
                }
            }
            """,
            ClosingDelimiter.OWN_LINE
        );
        // The last argument belongs with the ) below it, so the list breaks as a whole.
        assertFalse(formatted.contains("someCall(version, access, name, signature, superName, interfaces, interfaces, version, version, more, args, here, a, b, c\n"), formatted);
        assertEquals(formatted, format(formatted, ClosingDelimiter.OWN_LINE));
    }

}
