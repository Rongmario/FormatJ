package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.CommentReflow;
import zone.rong.formatj.api.rules.CommentRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.WrappingRules;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The rules that were waiting on the prose check: {@code comments.reflow} and the {@code javadoc.*}
 * family.
 *
 * <p>Each is checked for what it does and, where the answer is "nothing", for the fact that it leaves
 * the comment byte for byte as the author wrote it. A rule that quietly re-spaced every comment it
 * walked past on its way to the one it wanted would pass a test that only looked at its own output.
 */
class CommentProseRulesTest {

    private static String format(Consumer<StyleBuilder> rules, String body) {
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        FormatResult result =
                FormatJ.newFormatter()
                        .style(builder.build())
                        .languageLevel(LanguageLevel.LATEST)
                        .build()
                        .format(FormatRequest.of("class T {\n\n" + body + "\n}\n").withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    // --------------------------------------------------------- comments.reflow

    @Test
    void reflowIsOffByDefault() {
        String body = "    // one two three four five six seven eight nine ten eleven twelve\n    void f() { }\n";
        assertTrue(format(style -> { }, body).contains("// one two three four five six seven eight nine ten eleven twelve"));
    }

    @Test
    void reflowRefillsARunOfLineCommentsToTheMargin() {
        String formatted =
                format(
                        style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                                .set(WrappingRules.MAX_LINE_LENGTH, 40),
                        "    // one two three four five six seven eight nine\n    // ten\n    void f() { }\n");
        assertTrue(formatted.contains("    // one two three four five six seven\n    // eight nine ten\n"), formatted);
    }

    @Test
    void reflowLeavesATrailingCommentOnItsOwnLine() {
        String formatted =
                format(
                        style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                                .set(WrappingRules.MAX_LINE_LENGTH, 40),
                        "    void f() { } // one two three four five six seven eight\n");
        assertTrue(formatted.contains("// one two three four five six seven eight"), formatted);
    }

    @Test
    void reflowLeavesCommentedOutCodeRagged() {
        String formatted =
                format(
                        style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                                .set(WrappingRules.MAX_LINE_LENGTH, 40),
                        "    //g();\n    //h();\n    void f() { }\n");
        assertTrue(formatted.contains("    //g();\n    //h();\n"), formatted);
    }

    // ------------------------------------------------------------- javadoc.*

    @Test
    void aCommentNoRuleHasAnythingToSayAboutComesOutUntouched() {
        String comment = "    /**\n     * Text.\n     *\n     * @param a x\n     */\n";
        String formatted = format(style -> { }, comment + "    void f(int a) { }\n");
        assertTrue(formatted.contains(comment), formatted);
    }

    @Test
    void blankLineBeforeTagsWritesTheBlankLineItAsksFor() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.BLANK_LINE_BEFORE_TAGS, true),
                        "    /**\n     * Text.\n     * @param a x\n     */\n    void f(int a) { }\n");
        assertTrue(formatted.contains("     * Text.\n     *\n     * @param a x\n"), formatted);
    }

    @Test
    void blankLineBeforeTagsTakesItAwayAgainWhenTurnedOff() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.BLANK_LINE_BEFORE_TAGS, false),
                        "    /**\n     * Text.\n     *\n     * @param a x\n     */\n    void f(int a) { }\n");
        assertTrue(formatted.contains("     * Text.\n     * @param a x\n"), formatted);
    }

    @Test
    void keepSingleLineLeavesAOneLineCommentOnOneLine() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.WRAP, true),
                        "    /** Text. */\n    void f() { }\n");
        assertTrue(formatted.contains("    /** Text. */\n"), formatted);
    }

    @Test
    void keepSingleLineTurnedOffSpreadsItOut() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.WRAP, true).set(JavadocRules.KEEP_SINGLE_LINE, false),
                        "    /** Text. */\n    void f() { }\n");
        assertTrue(formatted.contains("    /**\n     * Text.\n     */\n"), formatted);
    }

    @Test
    void tagContinuationIndentPushesTheSecondLineOfATagOver() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.WRAP, true)
                                .set(JavadocRules.TAG_CONTINUATION_INDENT, 4)
                                .set(WrappingRules.MAX_LINE_LENGTH, 40),
                        "    /**\n     * @param a one two three four five six seven\n     */\n    void f(int a) { }\n");
        assertTrue(formatted.contains("\n     *     "), formatted);
    }

    @Test
    void wrappingNeverTouchesASample() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.WRAP, true).set(WrappingRules.MAX_LINE_LENGTH, 40),
                        "    /**\n     * <pre>\n     *   int x =   1;\n     * </pre>\n     */\n    void f() { }\n");
        assertTrue(formatted.contains("     *   int x =   1;\n"), formatted);
    }

    @Test
    void tagOrderPutsTheTagsInTheConventionalOrder() {
        String formatted =
                format(
                        style -> style.set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL),
                        "    /**\n     * @return r\n     * @param a x\n     */\n    int f(int a) { return a; }\n");
        assertTrue(formatted.indexOf("@param") < formatted.indexOf("@return"), formatted);
    }

    @Test
    void formattingStaysAFixedPointWithEveryProseRuleOn() {
        String body =
                "    /**\n     * One two three four five six seven eight nine ten eleven twelve.\n"
                        + "     *\n     * @return r\n     * @param a x\n     */\n"
                        + "    // a comment that is quite long and will need refilling at this margin\n"
                        + "    int f(int a) { return a; }\n";
        Consumer<StyleBuilder> rules =
                style -> style.set(CommentRules.REFLOW, CommentReflow.REFLOW_TO_LINE_LENGTH)
                        .set(JavadocRules.WRAP, true)
                        .set(JavadocRules.TAG_ORDER, zone.rong.formatj.api.rules.JavadocTagOrder.CANONICAL)
                        .set(JavadocRules.ALIGN_TAG_DESCRIPTIONS, true)
                        .set(JavadocRules.ADD_PARAGRAPH_TAGS, true)
                        .set(WrappingRules.MAX_LINE_LENGTH, 50);
        String once = format(rules, body);
        StyleBuilder builder = Style.builder();
        rules.accept(builder);
        String twice =
                FormatJ.newFormatter().style(builder.build()).languageLevel(LanguageLevel.LATEST).build()
                        .format(FormatRequest.of(once)).text();
        assertEquals(once, twice);
    }

}
