package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AnnotationRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue 3: an annotation above {@code package} is controlled by its own rule,
 * {@code annotations.package-placement}, instead of being pulled onto the package line unconditionally.
 */
class PackageAnnotationPlacementTest {

    private static final String DOC_SOURCE = """
            /**
             * Docs.
             */
            @NullMarked
            package repro.pkg;
            """;

    private static final String PLAIN_SOURCE = "@NullMarked\npackage repro.pkg;\n";

    private static String format(String source, AnnotationPlacement placement) {
        Style style = Style.builder().annotations(annotations -> annotations.packagePlacement(placement)).build();
        return FormatJ.newFormatter().style(style).build().format(source);
    }

    @Test
    void theDefaultIsANewLineForTheAnnotation() {
        String formatted = format(DOC_SOURCE, Style.defaults().get(AnnotationRules.PACKAGE_PLACEMENT));
        assertEquals(
            """
            /**
             * Docs.
             */
            @NullMarked
            package repro.pkg;
            """,
            formatted
        );
    }

    @Test
    void sameLineJoinsTheAnnotationToThePackage() {
        String formatted = format(DOC_SOURCE, AnnotationPlacement.SAME_LINE);
        assertEquals(
            """
            /**
             * Docs.
             */
            @NullMarked package repro.pkg;
            """,
            formatted
        );
    }

    @Test
    void sameLineWhenShortJoinsAShortAnnotationToThePackage() {
        String formatted = format(PLAIN_SOURCE, AnnotationPlacement.SAME_LINE_WHEN_SHORT);
        assertEquals("@NullMarked package repro.pkg;\n", formatted);
    }

    @Test
    void preserveKeepsTheAnnotationWhereTheAuthorPutIt() {
        String formatted = format(DOC_SOURCE, AnnotationPlacement.PRESERVE);
        assertEquals(
            """
            /**
             * Docs.
             */
            @NullMarked
            package repro.pkg;
            """,
            formatted
        );
    }

    @Test
    void sameLineWhenShortStillMovesALongAnnotationToItsOwnLine() {
        String longAnnotation = """
                @Config.Comment("Field.get/set calls fall back to unrestricted access when they hit final or inaccessible fields.")
                package repro.pkg;
                """;
        String formatted = format(longAnnotation, AnnotationPlacement.SAME_LINE_WHEN_SHORT);
        assertTrue(formatted.indexOf("@Config.Comment") < formatted.indexOf("package"), formatted);
    }

}
