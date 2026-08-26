package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.AlignmentPolicy;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The column alignment rules.
 *
 * <p>They are tested together because they share one piece of machinery and one danger: alignment is
 * decided after the text has been printed, so what has to hold for every one of them is that the
 * padding never moves a line break, and that formatting the padded output again produces it
 * unchanged.
 */
class AlignmentRulesTest {

    @Test
    void consecutiveFieldNamesShareAColumn() {
        String source = "class A {\n\n    private int x;\n    private String name;\n    private A other;\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.consecutiveFields(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private int    x;"), aligned);
        assertTrue(aligned.contains("private String name;"), aligned);
        assertTrue(aligned.contains("private A      other;"), aligned);
    }

    @Test
    void aBlankLineEndsARunOfFields() {
        String source = "class A {\n\n    private int x;\n    private String name;\n\n    private A o;\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.consecutiveFields(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private A o;"), aligned);
    }

    @Test
    void consecutiveLocalNamesShareAColumn() {
        String source = "class A {\n\n    void f() {\n        int x = 1;\n        String name = \"a\";\n    }\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.consecutiveVariables(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("int    x = 1;"), aligned);
        assertTrue(aligned.contains("String name = \"a\";"), aligned);
    }

    @Test
    void consecutiveAssignmentsShareTheirOperatorColumn() {
        String source = "class A {\n\n    void f() {\n        x = 1;\n        longer = 2;\n        n = 3;\n"
                + "    }\n\n}\n";

        String aligned =
                format(
                        source,
                        style -> style.alignment(a -> a.consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("x      = 1;"), aligned);
        assertTrue(aligned.contains("longer = 2;"), aligned);
        assertTrue(aligned.contains("n      = 3;"), aligned);
    }

    @Test
    void anInitialiserIsAnAssignmentForThisRule() {
        String source = "class A {\n\n    private int x = 1;\n    private String name = \"a\";\n\n}\n";

        String aligned =
                format(
                        source,
                        style -> style.alignment(a -> a.consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private int x       = 1;"), aligned);
        assertTrue(aligned.contains("private String name = \"a\";"), aligned);
    }

    @Test
    void annotationValuesShareAColumn() {
        String source = "class A {\n\n    @Thing(\n            name = \"a rather long value indeed\",\n"
                + "            other = \"another long value here\",\n            x = \"a third long value here\")\n"
                + "    void f() {\n    }\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.annotationValues(AlignmentPolicy.ALIGN_ON_COLUMN))
                        .wrapping(w -> w.maxLineLength(60)));

        assertTrue(aligned.contains("name  = "), aligned);
        assertTrue(aligned.contains("other = "), aligned);
        assertTrue(aligned.contains("x     = "), aligned);
    }

    @Test
    void switchArrowsShareAColumn() {
        String source = "class A {\n\n    int f(int i) {\n        return switch (i) {\n            case 1 -> 1;\n"
                + "            case 22 -> 2;\n            default -> 3;\n        };\n    }\n\n}\n";

        String aligned = format(source, style -> style.alignment(a -> a.switchArrows(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("case 1  -> 1;"), aligned);
        assertTrue(aligned.contains("case 22 -> 2;"), aligned);
        assertTrue(aligned.contains("default -> 3;"), aligned);
    }

    @Test
    void trailingCommentsShareAColumn() {
        String source = "class A {\n\n    private int x; // one\n    private String name; // two\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.trailingComments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private int x;       // one"), aligned);
        assertTrue(aligned.contains("private String name; // two"), aligned);
    }

    @Test
    void chainedDotsHangFromTheFirstOne() {
        String source = "class A {\n\n    void f() {\n        people.stream().filter(p -> p.alive()).map(P::name)"
                + ".sorted().toList();\n    }\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.methodChains(AlignmentPolicy.ALIGN_ON_COLUMN))
                        .wrapping(w -> w.maxLineLength(40)));

        assertTrue(aligned.contains("people.stream()\n              .filter"), aligned);
    }

    @Test
    void ternaryBranchesHangUnderTheirCondition() {
        String source = "class A {\n\n    String f(boolean flag) {\n        return flag ? \"a very long string here\""
                + " : \"another long string here\";\n    }\n\n}\n";

        String aligned = format(source, style -> style.wrapping(w -> w.maxLineLength(60)));
        String plain =
                format(source, style -> style.alignment(a -> a.ternaryBranches(AlignmentPolicy.NONE))
                        .wrapping(w -> w.maxLineLength(60)));

        // Aligned, the branches hang under the condition; unaligned, at indent.ternary past the
        // statement, which is where they used to hang whatever the rule said.
        assertTrue(aligned.contains("return flag\n               ? "), aligned);
        assertTrue(plain.contains("return flag\n                ? "), plain);
    }

    @Test
    void alignmentIsAFixedPoint() {
        String source = "class A {\n\n    private int x = 1; // one\n    private String name = \"a\"; // two\n\n"
                + "    void f() {\n        int i = 1;\n        String s = \"a\";\n        i = 2;\n        s = \"b\";\n"
                + "    }\n\n}\n";
        Consumer<StyleBuilder> everything = style -> style.alignment(a -> a.consecutiveFields(
                AlignmentPolicy.ALIGN_ON_COLUMN)
                .consecutiveVariables(AlignmentPolicy.ALIGN_ON_COLUMN)
                .consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)
                .trailingComments(AlignmentPolicy.ALIGN_ON_COLUMN));

        String once = format(source, everything);

        assertEquals(once, format(once, everything));
    }

    @Test
    void alignmentDoesNotMoveALineBreak() {
        // The padding goes into text that has already been laid out, so switching every rule on must
        // leave a file with exactly the line structure it had with them all off.
        String source = "class A {\n\n    private int x = 1; // one\n    private String name = \"a\"; // two\n\n"
                + "    void f() {\n        int i = 1;\n        String s = \"a\";\n    }\n\n}\n";

        String off = format(source, style -> { });
        String on =
                format(source, style -> style.alignment(a -> a.consecutiveFields(AlignmentPolicy.ALIGN_ON_COLUMN)
                        .consecutiveVariables(AlignmentPolicy.ALIGN_ON_COLUMN)
                        .consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)
                        .trailingComments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertEquals(squeeze(off), squeeze(on));
    }

    @Test
    void aTextBlockKeepsItsStringWhenTheLineInFrontOfItIsPadded() {
        // Padding moves the opening delimiter along its line, and nothing else. The string a text
        // block denotes is measured from its content lines and its closing delimiter, so it is
        // unchanged — and the pipeline's token check would reject the file if it were not.
        String source = "class A {\n\n    private int x = 1;\n    private String text = \"\"\"\n            hello\n"
                + "            \"\"\";\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.consecutiveFields(AlignmentPolicy.ALIGN_ON_COLUMN)
                        .consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private int    x    = 1;"), aligned);
        assertTrue(aligned.contains("\n            hello\n"), aligned);
    }

    @Test
    void aFormatterOffRegionIsNotPadded() {
        String source = "class A {\n\n    // formatj:off\n    private int x = 1;\n    private String name = \"a\";\n"
                + "    // formatj:on\n    private int y = 2;\n\n}\n";

        String aligned =
                format(source, style -> style.alignment(a -> a.consecutiveFields(AlignmentPolicy.ALIGN_ON_COLUMN)
                        .consecutiveAssignments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        assertTrue(aligned.contains("private int x = 1;\n    private String name = \"a\";"), aligned);
    }

    /** The text with each run of spaces collapsed, which is all alignment is allowed to change. */
    private static String squeeze(String text) {
        return text.replaceAll("(?<=\\S) +", " ");
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
