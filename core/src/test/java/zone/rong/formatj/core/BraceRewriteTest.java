package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.rewrite.RewriteResult;
import zone.rong.formatj.core.rewrite.RewriteStage;
import org.junit.jupiter.api.Test;

/** What the brace rules do, and the cases where they decline to do it. */
class BraceRewriteTest {

    private static final String HEAD = "class T {\n\n    void run(int n) {\n";
    private static final String TAIL = "\n    }\n\n}\n";

    private static RewriteResult rewrite(String body, BracePolicy policy) {
        GreenNode root = JavaParser.parse(HEAD + body + TAIL, LanguageLevel.LATEST, false).root().green();
        Style style = Style.builder()
                .set(BraceRules.IF_ELSE, policy)
                .set(BraceRules.FOR_LOOP, policy)
                .set(BraceRules.WHILE_LOOP, policy)
                .build();
        return RewriteStage.apply(root, style);
    }

    private static String tokensAfter(String body, BracePolicy policy) {
        return String.join(" ", ProgramTokens.lexemes(rewrite(body, policy).root()));
    }

    @Test
    void preserveTouchesNothing() {
        assertTrue(rewrite("        if (n > 0) log(n);", BracePolicy.PRESERVE).unchanged());
        assertTrue(rewrite("        if (n > 0) { log(n); }", BracePolicy.PRESERVE).unchanged());
    }

    @Test
    void alwaysBracesEveryKindOfBody() {
        assertTrue(
                tokensAfter("        if (n > 0) log(n);", BracePolicy.ALWAYS).contains("if ( n > 0 ) { log ( n ) ; }"));
        assertTrue(
                tokensAfter("        while (n > 0) n--;", BracePolicy.ALWAYS).contains("while ( n > 0 ) { n -- ; }"));
        assertTrue(
                tokensAfter("        for (int i = 0; i < n; i++) log(i);", BracePolicy.ALWAYS).contains(
                        ") { log ( i ) ; }"));
        assertTrue(
                tokensAfter("        for (String s : all()) log(s);", BracePolicy.ALWAYS).contains(
                        ") { log ( s ) ; }"));
        assertTrue(tokensAfter("        do n--; while (n > 0);", BracePolicy.ALWAYS).contains("do { n -- ; } while"));
    }

    @Test
    void alwaysBracesBothArmsOfAnIfElse() {
        assertTrue(
                tokensAfter("        if (n > 0) log(n); else log(0);", BracePolicy.ALWAYS).contains(
                        "if ( n > 0 ) { log ( n ) ; } else { log ( 0 ) ; }"));
    }

    @Test
    void anElseIfStaysAChainRatherThanBecomingANestedIf() {
        String tokens = tokensAfter("        if (n > 0) log(n); else if (n < 0) log(0);", BracePolicy.ALWAYS);
        assertTrue(tokens.contains("else if ( n < 0 ) { log ( 0 ) ; }"), tokens);
    }

    @Test
    void nestedBodiesAreBracedFromTheInsideOut() {
        assertTrue(
                tokensAfter("        if (n > 0) if (n > 1) log(n);", BracePolicy.ALWAYS).contains(
                        "if ( n > 0 ) { if ( n > 1 ) { log ( n ) ; } }"));
    }

    @Test
    void neverRemovesBracesFromASingleStatementBody() {
        assertTrue(
                tokensAfter("        if (n > 0) { log(n); }", BracePolicy.NEVER).contains("if ( n > 0 ) log ( n ) ;"));
    }

    @Test
    void neverKeepsBracesRoundMoreThanOneStatement() {
        assertTrue(rewrite("        if (n > 0) { log(n); log(n); }", BracePolicy.NEVER).unchanged());
    }

    @Test
    void aDeclarationCannotBecomeAnUnbracedBody() {
        assertTrue(rewrite("        if (n > 0) { int x = 1; }", BracePolicy.NEVER).unchanged());
    }

    @Test
    void anIfWithAnElseKeepsBracesRoundAnInnerIf() {
        assertTrue(rewrite("        if (n > 0) { if (n > 1) log(n); } else log(0);", BracePolicy.NEVER).unchanged());
    }

    @Test
    void bracesCarryingCommentsAreLeftAlone() {
        assertTrue(
                rewrite("        if (n > 0) { // why\n            log(n);\n        }", BracePolicy.NEVER).unchanged());
        assertTrue(
                rewrite(
                        "        if (n > 0) {\n            log(n);\n            // why\n        }",
                        BracePolicy.NEVER).unchanged());
    }

    @Test
    void whenMultiStatementBracesOnlyWhatNeedsIt() {
        assertTrue(
                tokensAfter("        if (n > 0) { log(n); }", BracePolicy.WHEN_MULTI_STATEMENT).contains(
                        "if ( n > 0 ) log ( n ) ;"));
        assertTrue(rewrite("        if (n > 0) { log(n); log(n); }", BracePolicy.WHEN_MULTI_STATEMENT).unchanged());
        assertTrue(rewrite("        if (n > 0) log(n);", BracePolicy.WHEN_MULTI_STATEMENT).unchanged());
    }

    @Test
    void everyEditIsDeclared() {
        RewriteResult result = rewrite("        if (n > 0) log(n); else log(0);", BracePolicy.ALWAYS);
        assertEquals(4, result.edits().size(), () -> "edits: " + result.edits());
        assertFalse(result.unchanged());
    }

    @Test
    void rewritingSettlesAfterOnePass() {
        for (BracePolicy policy : BracePolicy.values()) {
            String body = """
                            if (n > 0) log(n); else log(0);
                            while (n > 0) { n--; }
                            for (int i = 0; i < n; i++) { log(i); }
                            do n--; while (n > 0);
                    """;
            Style style = Style.builder()
                    .set(BraceRules.IF_ELSE, policy)
                    .set(BraceRules.FOR_LOOP, policy)
                    .set(BraceRules.WHILE_LOOP, policy)
                    .build();
            GreenNode once = rewrite(body, policy).root();
            assertTrue(
                    RewriteStage.apply(once, style).unchanged(),
                    () -> "a second pass of " + policy + " still had something to say");
        }
    }

}
