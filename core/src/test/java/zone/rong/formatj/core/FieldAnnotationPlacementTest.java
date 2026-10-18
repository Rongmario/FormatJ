package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AnnotationPlacement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issues 2 and 2b: with the field's annotation placed on the same line, an annotation whose
 * arguments cannot fit must not break inside its own parentheses, and a short annotation must stay
 * on the line when the whole declaration fits.
 */
class FieldAnnotationPlacementTest {

    private static final String LONG = "@Config.Comment(\"Field.get/set calls fall back to unrestricted access when they hit final or inaccessible fields.\")\n" +
        "public static boolean reflectFields = true;\n";

    private static final String SHORT = "@Config.Comment(\"Short comment.\")\n" +
        "public static boolean shortField = true;\n";

    private static String format(String source, AnnotationPlacement placement) {
        Style style = Style.builder().annotations(annotations -> annotations.fieldPlacement(placement)).build();
        return FormatJ.newFormatter().style(style).build().format(source);
    }

    @Test
    void aSameLineAnnotationThatDoesNotFitMovesTheFieldToTheNextLine() {
        String formatted = format(LONG, AnnotationPlacement.SAME_LINE);
        assertEquals(LONG, formatted);
    }

    @Test
    void aShortAnnotationStaysOnTheDeclarationLine() {
        String formatted = format(SHORT, AnnotationPlacement.SAME_LINE);
        assertTrue(
            formatted.contains("@Config.Comment(\"Short comment.\") public static boolean shortField = true;"),
            formatted
        );
    }

    @Test
    void sameLineWhenShortKeepsAShortAnnotationOnTheLine() {
        String formatted = format(SHORT, AnnotationPlacement.SAME_LINE_WHEN_SHORT);
        assertTrue(
            formatted.contains("@Config.Comment(\"Short comment.\") public static boolean shortField = true;"),
            formatted
        );
    }

    @Test
    void sameLineWhenShortMovesALongAnnotationToItsOwnLine() {
        String formatted = format(LONG, AnnotationPlacement.SAME_LINE_WHEN_SHORT);
        assertEquals(LONG, formatted);
    }

}
