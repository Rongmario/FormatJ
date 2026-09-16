package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class RangeFormattingTest {

    private static final Formatter FORMATTER = FormatJ.defaultFormatter();

    @Test
    void onlyTheMethodCoveredByTheRangeIsReformatted() {
        String source = """
                class A {
                    void first(){if(x){g();}}
                    // marker
                    void second(){if(y){h();}}
                }
                """;
        int start = source.indexOf("void second");
        int end = source.indexOf('\n', source.indexOf("second(){if(y){h();}}"));

        FormatResult result = format(source, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(
                """
                class A {
                    void first(){if(x){g();}}
                    // marker
                    void second() {
                        if (y) { h(); }
                    }

                }
                """,
                result.text());
    }

    @Test
    void emptyRangesFormatTheWholeFile() {
        String source = "class A{void run(){if(x){g();}}}\n";

        FormatResult wholeFile = FORMATTER.format(FormatRequest.of(source).withName("A.java"));

        assertFalse(wholeFile.hasErrors());
        assertTrue(wholeFile.text().contains("class A {"), wholeFile.text());
    }

    @Test
    void formattingARangeTwiceProducesTheSameTextAndItStillParses() {
        String source = """
                class A {
                    void first(  ) {g();}
                    // marker
                    void second(){if(y){h();}}
                }
                """;
        int start = source.indexOf("void second");

        FormatResult once = format(source, start, source.length());
        FormatResult twice = format(once.text(), once.text().indexOf("void second"), once.text().length());

        assertEquals(once.text(), twice.text());
        ParseResult reparsed = JavaParser.parse(once.text(), LanguageLevel.LATEST, false);
        assertFalse(reparsed.hasErrors());
    }

    @Test
    void aRangeThatWouldReflowDistantLinesLeavesThemAlone() {
        // A whole-file format would also tighten "first"'s parameter-list spacing; since the range
        // only covers "second", "first" must come out exactly as written.
        String source = """
                class A {
                    void first(  ) {g();}
                    // marker
                    void second(){if(y){h();}}
                }
                """;
        int start = source.indexOf("void second");

        FormatResult result = format(source, start, source.length());

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(
                """
                class A {
                    void first(  ) {g();}
                    // marker
                    void second() {
                        if (y) { h(); }
                    }

                }
                """,
                result.text());
    }

    @Test
    void aRangeReachingEndOfFileIsFormatted() {
        String source = "class A {\n    void run(){g();}\n}\n";

        FormatResult result = format(source, source.indexOf("void run"), source.length());

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals("class A {\n\n    void run() {\n        g();\n    }\n\n}\n", result.text());
    }

    @Test
    void aZeroLengthRangeInsideIndentationStillFormatsTheLineItTouches() {
        String source = "class A {\n    void run(){g();}\n}\n";
        int point = source.indexOf("    void") + 2;

        FormatResult result = format(source, point, point);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals("class A {\n\n    void run() {\n        g();\n    }\n\n}\n", result.text());
    }

    private static FormatResult format(String source, int start, int end) {
        return FORMATTER.format(
                FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));
    }

}
