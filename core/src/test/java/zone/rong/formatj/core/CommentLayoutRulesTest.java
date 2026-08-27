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
 * The three {@code comments.*} layout rules that were in the catalogue before they changed output:
 * trailing-comment-column, keep-first-column-comments, and indent-with-code.
 */
class CommentLayoutRulesTest {

    @Test
    void trailingCommentsArePaddedToTheConfiguredColumn() {
        String source = "class A {\n\n    private int x; // one\n    private String name; // two\n\n}\n";

        String formatted = format(source, style -> style.comments(comments -> comments.trailingCommentColumn(40)));

        assertEquals(40, commentColumn(formatted, "private int x;"));
        assertEquals(40, commentColumn(formatted, "private String name;"));
        assertFixedPoint(source, style -> style.comments(comments -> comments.trailingCommentColumn(40)));
    }

    @Test
    void aZeroTrailingCommentColumnLeavesTheCommentAgainstTheCode() {
        String source = "class A {\n\n    private int x; // one\n\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("private int x; // one"), formatted);
        assertTrue(commentColumn(formatted, "private int x;") < 40, formatted);
    }

    @Test
    void aLineAlreadyPastTheColumnKeepsItsOrdinarySpacing() {
        String source = "class A {\n\n    private String aVeryLongFieldNameIndeed; // note\n\n}\n";

        String formatted = format(source, style -> style.comments(comments -> comments.trailingCommentColumn(20)));

        assertTrue(formatted.contains("aVeryLongFieldNameIndeed; // note"), formatted);
        assertTrue(commentColumn(formatted, "aVeryLongFieldNameIndeed;") > 20, formatted);
    }

    @Test
    void alignmentCanStillPushARunPastTheColumn() {
        String source = "class A {\n\n    private int x; // one\n    private String aVeryLongFieldNameIndeed; // two\n\n}\n";

        String formatted =
                format(source, style -> style.comments(comments -> comments.trailingCommentColumn(40))
                        .alignment(alignment -> alignment.trailingComments(AlignmentPolicy.ALIGN_ON_COLUMN)));

        int first = commentColumn(formatted, "private int x;");
        int second = commentColumn(formatted, "aVeryLongFieldNameIndeed;");
        assertEquals(second, first, formatted);
        assertTrue(first >= 40, formatted);
    }

    @Test
    void trailingCommentMinSpacesAreStillHonouredWhenPaddingToAColumn() {
        String source = "class A {\n\n    private int x; // one\n\n}\n";

        String formatted =
                format(
                        source,
                        style -> style.comments(comments -> comments.trailingCommentColumn(40).trailingCommentMinSpaces(
                                2)));

        assertEquals(40, commentColumn(formatted, "private int x;"));
        int lineStart = formatted.lastIndexOf('\n', formatted.indexOf("//")) + 1;
        String line = formatted.substring(lineStart, formatted.indexOf('\n', lineStart));
        assertTrue(line.contains(";  "), line);
    }

    @Test
    void aFirstColumnCommentIsIndentedWithTheCodeByDefault() {
        String source = """
                class A {

                    void f() {
                // note
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("        // note\n        int x = 1;"), formatted);
        assertFalse(formatted.contains("\n// note\n"), formatted);
    }

    @Test
    void aFirstColumnCommentStaysInColumnOneWhenAsked() {
        String source = """
                class A {

                    void f() {
                // note
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));

        assertTrue(formatted.contains("    void f() {\n// note\n        int x = 1;"), formatted);
        assertFixedPoint(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));
    }

    @Test
    void aFirstColumnBlockCommentStaysInColumnOneWhenAsked() {
        String source = """
                class A {

                    void f() {
                /* note */
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));

        assertTrue(formatted.contains("    void f() {\n/* note */\n        int x = 1;"), formatted);
    }

    @Test
    void aFirstColumnCommentBeforeAClosingBraceStaysInColumnOneWhenAsked() {
        String source = """
                class A {

                    void f() {
                        int x = 1;
                // end
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));

        assertTrue(formatted.contains("        int x = 1;\n// end\n    }"), formatted);
    }

    @Test
    void onlyFirstColumnCommentsArePinnedWhenIndentWithCodeIsOn() {
        String source = """
                class A {

                    void f() {
                // keep
                    // move
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));

        assertTrue(formatted.contains("    void f() {\n// keep\n        // move\n        int x = 1;"), formatted);
    }

    @Test
    void indentWithCodeOffKeepsTheAuthorsCommentIndent() {
        String source = """
                class A {

                    void f() {
                  // two
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.indentWithCode(false)));

        assertTrue(formatted.contains("    void f() {\n  // two\n        int x = 1;"), formatted);
        assertFixedPoint(source, style -> style.comments(comments -> comments.indentWithCode(false)));
    }

    @Test
    void indentWithCodeOnMovesAShallowCommentToTheFollowingStatement() {
        String source = """
                class A {

                    void f() {
                  // two
                        int x = 1;
                    }

                }
                """;

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("        // two\n        int x = 1;"), formatted);
    }

    @Test
    void aFirstColumnCommentInAnUnbracedBodyStaysInColumnOneWhenAsked() {
        String source = """
                class A {

                    void f() {
                        if (ready)
                // keep
                            return;
                    }

                }
                """;

        String formatted = format(source, style -> style.comments(comments -> comments.keepFirstColumnComments(true)));

        assertTrue(formatted.contains("        if (ready)\n// keep\n            return;"), formatted);
    }

    /** 1-based column of the first {@code //} on the line that contains {@code needle}. */
    private static int commentColumn(String source, String needle) {
        int found = source.indexOf(needle);
        assertTrue(found >= 0, () -> "missing " + needle + " in:\n" + source);
        int lineStart = source.lastIndexOf('\n', found) + 1;
        int lineEnd = source.indexOf('\n', found);
        String line = source.substring(lineStart, lineEnd < 0 ? source.length() : lineEnd);
        int comment = line.indexOf("//");
        assertTrue(comment >= 0, () -> "no trailing comment on: " + line);
        return comment + 1;
    }

    private static void assertFixedPoint(String source, Consumer<StyleBuilder> configure) {
        String once = format(source, configure);
        assertEquals(once, format(once, configure));
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
