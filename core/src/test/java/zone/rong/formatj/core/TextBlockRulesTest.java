package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.TextBlockIndentPolicy;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.pipeline.RewriteVerification;
import zone.rong.formatj.core.rewrite.TokenEdit;
import zone.rong.formatj.core.text.TextBlocks;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The text block rules, and the one thing they are all measured against: the string the block
 * denotes.
 *
 * <p>Re-indenting must not change it at all, which is what makes indentation layout. The other two
 * rules change it in one stated way each, which is what makes them rewrites.
 */
class TextBlockRulesTest {

    private static final String BLOCK = "\"\"\"\n        select *\n          from t\n        \"\"\"";

    private static String source(String block) {
        return "class T {\n\n    String q() {\n        return " + block + ";\n    }\n\n}\n";
    }

    private static FormatResult format(Style style, String block) {
        return FormatJ.newFormatter().style(style).languageLevel(LanguageLevel.LATEST).build()
                .format(FormatRequest.of(source(block)).withName("T.java"));
    }

    /** The text block of the formatted output, read back out of it. */
    private static String blockOf(String formatted) {
        int start = formatted.indexOf("\"\"\"");
        int end = formatted.indexOf("\"\"\"", start + 3);
        return formatted.substring(start, end + 3);
    }

    // ------------------------------------------------------------- the value

    @Test
    void theValueIsTheStringTheLanguageSays() {
        assertEquals("select *\n  from t\n", TextBlocks.value(BLOCK));
    }

    @Test
    void aClosingDelimiterOnTheContentLineLeavesNoFinalNewline() {
        assertEquals("select *", TextBlocks.value("\"\"\"\n        select *\"\"\""));
    }

    @Test
    void trailingSpacesAreThrownAwayUnlessTheyAreEscaped() {
        assertEquals("a", TextBlocks.value("\"\"\"\n        a  \"\"\""));
        assertEquals("a  ", TextBlocks.value("\"\"\"\n        a \\s\"\"\""));
    }

    @Test
    void unicodeWhitespaceIsContentRatherThanIncidentalIndentation() {
        String emSpace = "\u2003";
        String block = "\"\"\"\n        " + emSpace + "\"\"\"";

        assertEquals(emSpace, TextBlocks.value(block));

        FormatResult result =
                format(Style.builder().set(TextBlockRules.INDENT_POLICY, TextBlockIndentPolicy.MINIMAL).build(), block);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals(emSpace, TextBlocks.value(blockOf(result.text())));
        assertTrue(result.text().contains(emSpace), result.text());
    }

    // ---------------------------------------------------------- re-indenting

    @Test
    void reindentingMovesTheBlockAndNotTheString() {
        for (TextBlockIndentPolicy policy :
                List.of(TextBlockIndentPolicy.REINDENT_TO_BLOCK, TextBlockIndentPolicy.MINIMAL)) {
            FormatResult result =
                    format(Style.builder().set(TextBlockRules.INDENT_POLICY, policy).build(), BLOCK);
            assertFalse(result.hasErrors(), () -> policy + ": " + result.diagnostics());
            String rewritten = blockOf(result.text());
            assertEquals(TextBlocks.value(BLOCK), TextBlocks.value(rewritten), policy.toString());
            assertTrue(rewritten.contains("\n        select *") || rewritten.contains("\n               select *"),
                    policy + " produced " + rewritten);
        }
    }

    @Test
    void preserveLeavesTheBlockExactlyAsWritten() {
        FormatResult result = format(Style.defaults(), BLOCK);
        assertEquals(BLOCK, blockOf(result.text()));
    }

    // ------------------------------------------------------------- rewriting

    @Test
    void theClosingDelimiterRuleAddsTheLineTerminatorItImplies() {
        String inline = "\"\"\"\n        a\n        b\"\"\"";
        FormatResult result =
                format(Style.builder().set(TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE, true).build(), inline);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals("a\nb\n", TextBlocks.value(blockOf(result.text())));
    }

    @Test
    void theEscapeRuleMakesTrailingSpacesSignificant() {
        String spaced = "\"\"\"\n        a  \n        b\n        \"\"\"";
        FormatResult result =
                format(Style.builder().set(TextBlockRules.ESCAPE_TRAILING_SPACES, true).build(), spaced);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertEquals("a  \nb\n", TextBlocks.value(blockOf(result.text())));
    }

    @Test
    void bothRulesAreOffByDefaultBecauseTheyChangeAString() {
        assertFalse(Style.defaults().get(TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE));
        assertFalse(Style.defaults().get(TextBlockRules.ESCAPE_TRAILING_SPACES));
    }

    // ------------------------------------------------------------- the law

    private static GreenNode tree() {
        return JavaParser.parse(source(BLOCK), LanguageLevel.LATEST, false).root().green();
    }

    /** Where the text block sits in the token stream, which is the coordinate an edit is written in. */
    private static int blockPosition(GreenNode tree) {
        return ProgramTokens.lexemes(tree).indexOf(BLOCK);
    }

    @Test
    void anEditThatChangesTheContentFailsTheLaw() {
        GreenNode tree = tree();
        String problem =
                RewriteVerification.verifyOutput(
                        tree,
                        tree,
                        List.of(
                                new TokenEdit(
                                        TextBlockRules.ESCAPE_TRAILING_SPACES,
                                        "quietly editing the query",
                                        blockPosition(tree),
                                        List.of(BLOCK),
                                        List.of("\"\"\"\n        delete *\n          from t\n        \"\"\""),
                                        TokenEdit.Bias.INNERMOST_FIRST)));
        assertNotNull(problem);
        assertTrue(problem.contains("what the text block says"), problem);
    }

    @Test
    void anEditThatOnlyMovesTheEndSatisfiesTheLaw() {
        GreenNode tree = tree();
        String problem =
                RewriteVerification.verifyOutput(
                        tree,
                        tree,
                        List.of(
                                new TokenEdit(
                                        TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE,
                                        "moving the delimiter",
                                        blockPosition(tree),
                                        List.of(BLOCK),
                                        List.of(BLOCK),
                                        TokenEdit.Bias.INNERMOST_FIRST)));
        assertNull(problem);
    }

    @Test
    void anEditThatIsNotAWholeTextBlockFailsTheLaw() {
        GreenNode tree = tree();
        String problem =
                RewriteVerification.verifyOutput(
                        tree,
                        tree,
                        List.of(
                                new TokenEdit(
                                        TextBlockRules.CLOSING_DELIMITER_ON_OWN_LINE,
                                        "reaching outside the block",
                                        blockPosition(tree),
                                        List.of(BLOCK),
                                        List.of("\"a\""),
                                        TokenEdit.Bias.INNERMOST_FIRST)));
        assertNotNull(problem);
        assertTrue(problem.contains("one whole text block"), problem);
    }

}
