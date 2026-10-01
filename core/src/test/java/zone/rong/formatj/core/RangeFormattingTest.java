package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.TextBlockIndentPolicy;
import zone.rong.formatj.api.rules.TextBlockRules;
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
                        if (y) {
                            h();
                        }
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
                        if (y) {
                            h();
                        }
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
        assertEquals("class A {\n    void run() {\n        g();\n    }\n}\n", result.text());
    }

    @Test
    void aZeroLengthRangeInsideIndentationStillFormatsTheLineItTouches() {
        String source = "class A {\n    void run(){g();}\n}\n";
        int point = source.indexOf("    void") + 2;

        FormatResult result = format(source, point, point);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals("class A {\n    void run() {\n        g();\n    }\n}\n", result.text());
    }

    @Test
    void aPartialRangeDoesNotSplitAnImportRewrite() {
        String source = "import java.util.Set;\nimport java.util.List;\n\nclass A {\n\n"
                + "    Set<String> values;\n    List<String> others;\n\n}\n";
        Formatter formatter = FormatJ.newFormatter()
                .style(Style.builder().set(ImportRules.ORDER, SortOrder.ASCENDING).build())
                .build();
        int start = source.indexOf("import java.util.Set");
        int end = source.indexOf('\n', start);

        FormatResult result =
                formatter.format(
                        FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(source, result.text());
    }

    @Test
    void anUnsafeBraceRewriteAcrossASelectionIsLeftUnchanged() {
        String source = "class A {\n    void f(boolean b) {\n        if (b)\n            run();\n    }\n}\n";
        Formatter formatter = FormatJ.newFormatter()
                .style(Style.builder().set(BraceRules.IF_ELSE, BracePolicy.ALWAYS).build())
                .build();
        int start = source.indexOf("if (b)");
        int end = start + "if (b)".length();

        FormatResult result =
                formatter.format(
                        FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));

        assertEquals(source, result.text());
    }

    @Test
    void partialFormattingPreservesCrLfOutsideTheSelection() {
        String source = "class A {\r\n    void first(  ) {g();}\r\n    // marker\r\n"
                + "    void second(){h();}\r\n}\r\n";
        int start = source.indexOf("void second");
        int end = source.indexOf("\r\n", start);

        FormatResult result = format(source, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().startsWith("class A {\r\n    void first(  ) {g();}\r\n"), result.text());
        assertTrue(result.text().contains("void second() {"), result.text());
        assertFalse(result.text().replace("\r\n", "").contains("\n"), result.text());
    }

    @Test
    void adjacentMethodsDoNotShareASelectedHunk() {
        String source = "class A {\n    void first(){g();}\n    void second(){h();}\n}\n";
        int start = source.indexOf("void second");
        int end = source.indexOf('\n', start);

        FormatResult result = format(source, start, end);

        assertTrue(result.text().contains("void first(){g();}"), result.text());
        assertTrue(result.text().contains("void second() {"), result.text());
    }

    @Test
    void multipleSelectionsFormatOnlyTheirMethods() {
        String source = "class A {\n    void first(){f();}\n    void second(){g();}\n    void third(){h();}\n}\n";
        int firstStart = source.indexOf("void first");
        int thirdStart = source.indexOf("void third");

        FormatResult result =
                FORMATTER.format(
                        FormatRequest.of(source)
                                .withName("A.java")
                                .withRanges(
                                        List.of(
                                                new SourceRange(firstStart, source.indexOf('\n', firstStart)),
                                                new SourceRange(thirdStart, source.indexOf('\n', thirdStart)))));

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("void first() {"), result.text());
        assertTrue(result.text().contains("void second(){g();}"), result.text());
        assertTrue(result.text().contains("void third() {"), result.text());
    }

    @Test
    void aFullCoverRangeIncludesTheFinalNewlineChange() {
        String source = "class A { }";

        FormatResult result = format(source, 0, source.length());

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().endsWith("\n"), result.text());
    }

    @Test
    void anOversizedAmbiguousDiffCannotDeleteTheSelectedField() {
        String source = "class A {\n" + "\n".repeat(1500) + "    int x=1;\n}\n";
        int start = source.indexOf("int x");
        int end = source.indexOf('\n', start);

        FormatResult result = format(source, start, end);

        assertTrue(result.text().contains("int x"), result.text());
    }

    @Test
    void repeatedBracesStayUnchangedAroundASelectedField() {
        String source = "class A {\n" + "    {\n    }\n".repeat(800) + "    int x=1;\n}\n";
        int start = source.indexOf("int x");
        int end = source.indexOf('\n', start);
        int selectedLineStart = source.lastIndexOf('\n', start) + 1;

        FormatResult result = format(source, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().startsWith(source.substring(0, selectedLineStart)), result.text());
        assertTrue(result.text().contains("int x = 1;"), result.text());
    }

    @Test
    void commonEdgesReduceADiffAboveTheLineProductLimit() {
        String source = "class A {\n" + "    // marker\n".repeat(1500) + "    int x=1;\n}\n";
        int start = source.indexOf("int x");
        int end = source.indexOf('\n', start);
        int selectedLineStart = source.lastIndexOf('\n', start) + 1;

        FormatResult result = format(source, start, end);
        int secondStart = result.text().indexOf("int x");
        FormatResult twice = format(result.text(), secondStart, result.text().indexOf('\n', secondStart));

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().startsWith(source.substring(0, selectedLineStart)), result.text());
        assertTrue(result.text().contains("int x = 1;"), result.text());
        assertEquals(1500, result.text().split("// marker", -1).length - 1);
        assertEquals(result.text(), twice.text());
    }

    @Test
    void uniqueLinesPartitionADiffAboveTheLineProductLimit() {
        StringBuilder source = new StringBuilder("class A {\n    int first=1;\n");
        for (int i = 0; i < 1500; i++) {
            source.append("    // marker ").append(i).append('\n');
        }
        source.append("    int selected=2;\n}\n");
        String text = source.toString();
        int start = text.indexOf("int selected");
        int end = text.indexOf('\n', start);
        int selectedLineStart = text.lastIndexOf('\n', start) + 1;

        FormatResult result = format(text, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().startsWith(text.substring(0, selectedLineStart)), result.text());
        assertTrue(result.text().contains("int first=1;"), result.text());
        assertTrue(result.text().contains("int selected = 2;"), result.text());
        assertTrue(result.text().contains("// marker 749"), result.text());
    }

    @Test
    void partialFormattingPreservesMixedLineEndingsOutsideTheSelection() {
        String prefix = "class A {\r\n    void first(  ) {g();}\n    // marker\r";
        String source = prefix + "    void second(){h();}\r\n}\n";
        int start = source.indexOf("void second");
        int end = source.indexOf("\r\n", start);

        FormatResult result = format(source, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().startsWith(prefix), result.text());
        assertTrue(result.text().contains("void second() {"), result.text());
        assertTrue(result.text().endsWith("}\n"), result.text());
    }

    @Test
    void aRangeAfterAnEscapedLineTerminatorUsesRawOffsets() {
        String escapedLf = "\\" + "u000a";
        String source = "class A {\n    int first=1;" + escapedLf + "    int second=2;\n}\n";
        int start = source.indexOf("int second");
        int end = start + "int second=2;".length();

        FormatResult result = format(source, start, end);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("int first"), result.text());
        assertTrue(result.text().contains("int second"), result.text());
        ParseResult reparsed = JavaParser.parse(result.text(), LanguageLevel.LATEST, false);
        assertTrue(reparsed.complete(), () -> reparsed.diagnostics().toString());
    }

    @Test
    void aPartialTextBlockReindentCannotChangeTheStringValue() {
        String source = "class A {\n    String text = \"\"\"\n        a\n\n        b\n        \"\"\";\n}\n";
        Formatter formatter = FormatJ.newFormatter()
                .style(
                        Style.builder()
                                .set(TextBlockRules.INDENT_POLICY, TextBlockIndentPolicy.REINDENT_TO_BLOCK)
                                .build())
                .build();
        int start = source.indexOf("        a");
        int end = source.indexOf('\n', start);

        FormatResult result =
                formatter.format(
                        FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));

        assertEquals(source, result.text());
    }

    @Test
    void aPartialJavadocReorderCannotLoseATag() {
        String source = "class A {\n    /**\n     * @return value\n     * @param input value\n     */\n"
                + "    int f(int input) { return input; }\n}\n";
        Formatter formatter = FormatJ.newFormatter()
                .style(Style.builder().set(JavadocRules.TAG_ORDER, JavadocTagOrder.CANONICAL).build())
                .build();
        int start = source.indexOf("@return");
        int end = source.indexOf('\n', start);

        FormatResult result =
                formatter.format(
                        FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));

        assertEquals(source, result.text());
    }

    private static FormatResult format(String source, int start, int end) {
        return FORMATTER.format(
                FormatRequest.of(source).withName("A.java").withRanges(List.of(new SourceRange(start, end))));
    }

}
