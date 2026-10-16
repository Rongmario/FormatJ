package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AnnotationPlacement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 2 applied to parameters: with the parameter's annotation placed on the same line, an
 * annotation that fits the line it would start on moves there whole instead of breaking inside its
 * own parentheses, the way the same rule behaves for fields and methods.
 *
 * <p>The choice the formatter has is column arithmetic rather than a different rule: an annotation
 * that cannot sit on any available line (here: 115 characters starting at column 8, past the 120
 * mark) has no whole line to move to, so breaking inside its own arguments is the only way to keep
 * the code readable, the same fallback a field or a package declaration takes.
 */
class ParameterAnnotationPlacementTest {

    private static final String METHOD = """
            class A {
                void method(%s int first, int second) { }
            }
            """;

    /** 110 characters: fits its own line at column 8, but not beside the declaration inline. */
    private static final String MOVABLE = "@Config.Comment(\"Field.get/set calls fall back to unrestricted access when they hit final or inaccessiblexxx\")";

    /** 115 characters: cannot fit any line this indentation offers, so its arguments break. */
    private static final String UNMOVABLE = "@Config.Comment(\"Field.get/set calls fall back to unrestricted access when they hit final or inaccessible fields.\")";

    private static String format(String source, AnnotationPlacement placement) {
        Style style = Style.builder().annotations(annotations -> annotations.parameterPlacement(placement)).build();
        return FormatJ.newFormatter().style(style).build().format(source);
    }

    @Test
    void anAnnotationThatFitsItsOwnLineMovesThereWhole() {
        String formatted = format(String.format(METHOD, MOVABLE), AnnotationPlacement.SAME_LINE);
        assertEquals(
            """
            class A {

                void method(
                    @Config.Comment("Field.get/set calls fall back to unrestricted access when they hit final or inaccessiblexxx")
                    int first,
                    int second
                ) { }

            }
            """,
            formatted
        );
        formatted.lines().forEach(line -> assertTrue(line.length() <= 120, line));
        assertEquals(formatted, format(formatted, AnnotationPlacement.SAME_LINE));
    }

    @Test
    void anAnnotationThatCannotFitAnyLineBreaksInsideItsOwnArguments() {
        String formatted = format(String.format(METHOD, UNMOVABLE), AnnotationPlacement.SAME_LINE);
        assertTrue(
            formatted.contains(
                "@Config.Comment(\n            \"Field.get/set calls fall back to unrestricted access when they hit final or inaccessible fields.\"\n        ) int first,"
            ),
            formatted
        );
        assertEquals(formatted, format(formatted, AnnotationPlacement.SAME_LINE));
    }

}
