package zone.rong.formatj.core.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.SyntaxKind;
import zone.rong.formatj.core.cst.SyntaxToken;
import zone.rong.formatj.core.lexer.Token;
import zone.rong.formatj.core.lexer.TokenKind;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.rewrite.Rewrite;
import zone.rong.formatj.core.rewrite.RewriteContext;
import zone.rong.formatj.core.rewrite.TokenEdit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The negative cases, which are the whole point of the verifier.
 *
 * <p>No shipped rewrite behaves the way the rewrites below do. That is exactly why they have to be
 * written by hand: a check nobody has watched fail is not known to work.
 */
class RewriteVerificationTest {

    private static final String SOURCE = """
            class T {

                void run(int n) {
                    if (n > 0) log(n);
                }

            }
            """;

    private static GreenNode parse(String source) {
        return JavaParser.parse(source, LanguageLevel.LATEST, false).root().green();
    }

    private static Style braces(BracePolicy policy) {
        return Style.builder().set(BraceRules.IF_ELSE, policy).build();
    }

    private static FormatResult format(Style style, Rewrite... rewrites) {
        return new DefaultFormatter(style, LanguageLevel.LATEST, false, true, List.of(rewrites)).format(
                FormatRequest.of(SOURCE).withName("T.java"));
    }

    private static String only(List<Diagnostic> diagnostics, Diagnostic.Severity severity) {
        List<Diagnostic> matching = diagnostics.stream().filter(d -> d.severity() == severity).toList();
        assertEquals(1, matching.size(), () -> "diagnostics: " + diagnostics);
        return matching.getFirst().message();
    }

    // ------------------------------------------------------------ the replay

    @Test
    void aTruthfulLedgerVerifies() {
        List<String> original = List.of("if", "(", "a", ")", "b", "(", ")", ";");
        List<String> expected =
                RewriteVerification.replay(
                        original,
                        List.of(
                                TokenEdit.insert(BraceRules.IF_ELSE, "wrap", 4, TokenEdit.Bias.OUTERMOST_FIRST, "{"),
                                TokenEdit.insert(BraceRules.IF_ELSE, "wrap", 8, TokenEdit.Bias.INNERMOST_FIRST, "}")));
        assertEquals(List.of("if", "(", "a", ")", "{", "b", "(", ")", ";", "}"), expected);
    }

    @Test
    void nestedInsertionsAtOnePositionNestRatherThanCross() {
        List<String> original = List.of("x", ";");
        List<String> expected =
                RewriteVerification.replay(
                        original,
                        List.of(
                                // Recorded innermost first, as the traversal does.
                                TokenEdit.insert(BraceRules.IF_ELSE, "inner", 0, TokenEdit.Bias.OUTERMOST_FIRST, "{"),
                                TokenEdit.insert(BraceRules.IF_ELSE, "inner", 2, TokenEdit.Bias.INNERMOST_FIRST, "}"),
                                TokenEdit.insert(BraceRules.IF_ELSE, "outer", 0, TokenEdit.Bias.OUTERMOST_FIRST, "{"),
                                TokenEdit.insert(BraceRules.IF_ELSE, "outer", 2, TokenEdit.Bias.INNERMOST_FIRST, "}")));
        assertEquals(List.of("{", "{", "x", ";", "}", "}"), expected);
    }

    // --------------------------------------------------------- the edit laws

    @Test
    void anEditMayNotInsertSomethingItsRuleHasNoBusinessInserting() {
        GreenNode before = parse(SOURCE);
        String problem =
                RewriteVerification.verifyOutput(
                        before,
                        before,
                        List.of(
                                TokenEdit.insert(
                                        BraceRules.IF_ELSE,
                                        "sneaking a call in",
                                        0,
                                        TokenEdit.Bias.INNERMOST_FIRST,
                                        "delete")));
        assertNotNull(problem);
        assertTrue(problem.contains("may only insert braces"), problem);
    }

    @Test
    void anUnclosedBraceIsRejected() {
        GreenNode before = parse(SOURCE);
        String problem =
                RewriteVerification.verifyOutput(
                        before,
                        before,
                        List.of(
                                TokenEdit.insert(
                                        BraceRules.IF_ELSE,
                                        "half a wrap",
                                        0,
                                        TokenEdit.Bias.INNERMOST_FIRST,
                                        "{")));
        assertNotNull(problem);
        assertTrue(problem.contains("not balanced"), problem);
    }

    @Test
    void anEditThatDeletesATokenWhichIsNotThereIsRejected() {
        GreenNode before = parse(SOURCE);
        String problem =
                RewriteVerification.verifyOutput(
                        before,
                        before,
                        List.of(
                                TokenEdit.delete(BraceRules.IF_ELSE, "imagined braces", 0, "{"),
                                TokenEdit.delete(BraceRules.IF_ELSE, "imagined braces", 1, "}")));
        assertNotNull(problem);
        assertTrue(problem.contains("but the source has 'class'"), problem);
    }

    // ------------------------------------------------- through the formatter

    @Test
    void anEditMadeButNotDeclaredIsCaughtAndTheFileIsStillFormatted() {
        FormatResult result = format(braces(BracePolicy.ALWAYS), new SilentBraceRewrite());
        assertFalse(result.hasErrors(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(
                only(result.diagnostics(), Diagnostic.Severity.WARNING).contains("skipped for this file"),
                () -> "diagnostics: " + result.diagnostics());
        assertFalse(
                result.text().contains("{\n            log(n);"),
                "the fallback formats the file, it just does not rewrite it");
    }

    @Test
    void anEditDeclaredButNotMadeIsCaught() {
        FormatResult result = format(braces(BracePolicy.ALWAYS), new BoastfulRewrite());
        assertFalse(result.hasErrors(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(
                only(result.diagnostics(), Diagnostic.Severity.WARNING).contains("skipped for this file"),
                () -> "diagnostics: " + result.diagnostics());
        assertFalse(result.text().contains("{\n            log(n);"));
    }

    @Test
    void aRewriteThatLosesACommentIsCaught() {
        String commented = """
                class T {

                    void run(int n) {
                        // keep me
                        log(n);
                    }

                }
                """;
        FormatResult result =
                new DefaultFormatter(
                        braces(BracePolicy.ALWAYS),
                        LanguageLevel.LATEST,
                        false,
                        true,
                        List.of(new ForgetfulRewrite())).format(FormatRequest.of(commented).withName("T.java"));
        assertFalse(result.hasErrors(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(
                only(result.diagnostics(), Diagnostic.Severity.WARNING).contains("comment was lost"),
                () -> "diagnostics: " + result.diagnostics());
        assertTrue(result.text().contains("// keep me"));
    }

    @Test
    void aRewriteThatNeverSettlesIsCaught() {
        FormatResult result = format(braces(BracePolicy.ALWAYS), new RestlessRewrite());
        assertFalse(result.hasErrors(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(
                only(result.diagnostics(), Diagnostic.Severity.WARNING).contains("did not settle"),
                () -> "diagnostics: " + result.diagnostics());
    }

    // ------------------------------------------------------- faulty rewrites

    /** Shared plumbing: these all claim to be the brace rule and are all lying about something. */
    private abstract static class FaultyRewrite implements Rewrite {

        @Override
        public String name() {
            return getClass().getSimpleName();
        }

        @Override
        public boolean enabled(RewriteContext context) {
            return true;
        }

        static GreenNode brace(String lexeme) {
            return GreenNode.leaf(SyntaxToken.of(Token.synthetic(TokenKind.SEPARATOR, lexeme)));
        }

        static GreenNode wrap(GreenNode body) {
            return GreenNode.branch(SyntaxKind.BLOCK, List.of(brace("{"), body, brace("}")));
        }

    }

    /** Adds braces and says nothing about it. */
    private static final class SilentBraceRewrite extends FaultyRewrite {

        @Override
        public GreenNode rewrite(GreenNode node, RewriteContext context) {
            if (node.kind() != SyntaxKind.IF_STATEMENT || node.children().size() < 5) {
                return node;
            }
            GreenNode body = node.children().get(4);
            if (body.kind() == SyntaxKind.BLOCK) {
                return node;
            }
            List<GreenNode> children = new ArrayList<>(node.children());
            children.set(4, wrap(body));
            return GreenNode.branch(node.kind(), children);
        }

    }

    /** Declares braces it never actually added. */
    private static final class BoastfulRewrite extends FaultyRewrite {

        @Override
        public GreenNode rewrite(GreenNode node, RewriteContext context) {
            if (node.kind() != SyntaxKind.IF_STATEMENT || node.children().size() < 5) {
                return node;
            }
            GreenNode body = node.children().get(4);
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "claimed",
                            context.firstPosition(body),
                            TokenEdit.Bias.OUTERMOST_FIRST,
                            "{"));
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "claimed",
                            context.endPosition(body),
                            TokenEdit.Bias.INNERMOST_FIRST,
                            "}"));
            return node;
        }

    }

    /** Wraps a statement in braces and drops the comment attached to it on the way. */
    private static final class ForgetfulRewrite extends FaultyRewrite {

        @Override
        public GreenNode rewrite(GreenNode node, RewriteContext context) {
            if (node.kind() != SyntaxKind.EXPRESSION_STATEMENT) {
                return node;
            }
            GreenNode stripped = strip(node);
            if (stripped == node) {
                return node;
            }
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "wrap",
                            context.firstPosition(node),
                            TokenEdit.Bias.OUTERMOST_FIRST,
                            "{"));
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "wrap",
                            context.endPosition(node),
                            TokenEdit.Bias.INNERMOST_FIRST,
                            "}"));
            return wrap(stripped);
        }

        private static GreenNode strip(GreenNode node) {
            if (node instanceof GreenNode.Leaf leaf) {
                return leaf.token().hasComments() ? GreenNode.leaf(SyntaxToken.of(leaf.token().token())) : leaf;
            }
            List<GreenNode> children = new ArrayList<>(node.children().size());
            boolean changed = false;
            for (GreenNode child : node.children()) {
                GreenNode stripped = strip(child);
                changed |= stripped != child;
                children.add(stripped);
            }
            return changed ? GreenNode.branch(node.kind(), children) : node;
        }

    }

    /** Adds a layer of braces every time it is asked, so it never reaches a fixed point. */
    private static final class RestlessRewrite extends FaultyRewrite {

        @Override
        public GreenNode rewrite(GreenNode node, RewriteContext context) {
            if (node.kind() != SyntaxKind.EXPRESSION_STATEMENT) {
                return node;
            }
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "again",
                            context.firstPosition(node),
                            TokenEdit.Bias.OUTERMOST_FIRST,
                            "{"));
            context.record(
                    TokenEdit.insert(
                            BraceRules.IF_ELSE,
                            "again",
                            context.endPosition(node),
                            TokenEdit.Bias.INNERMOST_FIRST,
                            "}"));
            return wrap(node);
        }

    }

}
