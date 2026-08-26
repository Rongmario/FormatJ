package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The {@code comments.*} formatter-off escape hatch. */
class FormatterOffTest {

    private static final String MEMBERS = """
            class A {

                // formatj:off
                int[][] matrix = {
                    { 1, 0 },
                    { 0, 1 },
                };
                int    spaced   =   1;
                // formatj:on
                int    normal   =   2;

            }
            """;

    @Test
    void aMarkedRegionKeepsEveryColumnItHad() {
        String formatted = format(MEMBERS, style -> { });

        assertTrue(formatted.contains("    { 1, 0 },\n        { 0, 1 },"), formatted);
        assertTrue(formatted.contains("int    spaced   =   1;"), formatted);
        // Everything after the on-marker is formatted as usual.
        assertTrue(formatted.contains("int normal = 2;"), formatted);
    }

    @Test
    void aMarkedRegionIsAFixedPoint() {
        Formatter formatter = FormatJ.defaultFormatter();
        String once = formatter.format(FormatRequest.of(MEMBERS).withName("A.java")).text();
        String twice = formatter.format(FormatRequest.of(once).withName("A.java")).text();

        assertEquals(once, twice);
    }

    @Test
    void theMarkersCanBeRenamed() {
        String source = """
                class A {

                    // @formatter:off
                    int    kept   =   1;
                    // @formatter:on
                    int    tidied   =   2;

                }
                """;

        String formatted =
                format(source, style -> style.comments(comments -> comments
                        .offMarker("@formatter:off")
                        .onMarker("@formatter:on")));

        assertTrue(formatted.contains("int    kept   =   1;"), formatted);
        assertTrue(formatted.contains("int tidied = 2;"), formatted);
    }

    @Test
    void theHatchCanBeClosedAltogether() {
        String formatted = format(MEMBERS, style -> style.comments(comments -> comments.honourFormatterOff(false)));

        assertFalse(formatted.contains("int    spaced   =   1;"), formatted);
        assertTrue(formatted.contains("int spaced = 1;"), formatted);
    }

    @Test
    void aRegionWithoutAnOnMarkerRunsToTheEndOfItsBody() {
        String source = "class A {\n\n    // formatj:off\n    int    a   =   1;\n    int    b   =   2;\n\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("int    a   =   1;"), formatted);
        assertTrue(formatted.contains("int    b   =   2;"), formatted);
    }

    @Test
    void statementsInsideAMethodCanBeMarkedToo() {
        String source = """
                class B {

                    void f() {
                        // formatj:off
                        int a   =   1;
                        // formatj:on
                        int c   =   3;
                    }

                }
                """;

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("int a   =   1;"), formatted);
        assertTrue(formatted.contains("int c = 3;"), formatted);
    }

    @Test
    void aMarkerAtTheTopOfAFileCoversTheWholeFile() {
        String source = "// formatj:off\nclass   A {\n    int   x =  1;\n}\n";

        String formatted = format(source, style -> { });

        assertEquals(source, formatted);
    }

    private static String format(String source, Consumer<StyleBuilder> configure) {
        StyleBuilder builder = Style.builder();
        configure.accept(builder);
        Formatter formatter = FormatJ.newFormatter().style(builder.build()).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("A.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

}
