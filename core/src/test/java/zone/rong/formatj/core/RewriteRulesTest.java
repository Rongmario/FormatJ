package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.LambdaParameterStyle;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.YieldStyle;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.rewrite.RewriteResult;
import zone.rong.formatj.core.rewrite.RewriteStage;
import org.junit.jupiter.api.Test;

/**
 * The five rules that add or remove code outside the brace and import families, and the cases where
 * each of them declines.
 *
 * <p>Everything here goes through the whole formatter rather than the rewrite stage alone wherever it
 * can, because passing verification is half of what these rules have to do: an edit the rewrite makes
 * but does not declare correctly costs the file its rewrites, and the output would then silently be
 * the unrewritten one.
 */
class RewriteRulesTest {

    private static <T> String format(String source, Option<T> option, T value) {
        return FormatJ.newFormatter()
                .style(Style.builder().set(option, value).build())
                .previewFeatures(true)
                .build()
                .format(source);
    }

    private static <T> RewriteResult rewrite(String source, Option<T> option, T value) {
        GreenNode root = JavaParser.parse(source, LanguageLevel.LATEST, true).root().green();
        return RewriteStage.apply(root, Style.builder().set(option, value).build());
    }

    private static <T> String tokens(String source, Option<T> option, T value) {
        return String.join(" ", ProgramTokens.lexemes(rewrite(source, option, value).root()));
    }

    private static String method(String body) {
        return "class T {\n\n    void run() {\n" + body + "\n    }\n\n}\n";
    }

    // -------------------------------------------------- lambdas.parameter-style

    @Test
    void parenthesesAreAddedRoundABareParameter() {
        String source = method("        run(x -> x + 1);");
        assertTrue(
                format(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.ALWAYS_PARENTHESISE).contains(
                        "run((x) -> x + 1);"));
    }

    @Test
    void parenthesesAreRemovedFromALoneUntypedParameter() {
        String source = method("        run((x) -> x + 1);");
        assertTrue(
                format(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE).contains(
                        "run(x -> x + 1);"));
    }

    @Test
    void parenthesesTheLanguageRequiresAreKept() {
        for (String parameters : new String[] {"()", "(a, b)", "(int x)", "(var x)", "(final x)", "(@A x)"}) {
            String source = method("        run(" + parameters + " -> 1);");
            assertTrue(
                    rewrite(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE).unchanged(),
                    parameters);
        }
    }

    @Test
    void preserveLeavesEitherFormAlone() {
        assertTrue(
                rewrite(method("        run(x -> 1);"), LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.PRESERVE)
                        .unchanged());
        assertTrue(
                rewrite(method("        run((x) -> 1);"), LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.PRESERVE)
                        .unchanged());
    }

    // ----------------------------------------------------- lambdas.body-braces

    @Test
    void aLambdaBlockReturningAValueCollapsesToTheExpression() {
        String source = method("        run(x -> { return x + 1; });");
        assertTrue(format(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).contains("run(x -> x + 1);"));
    }

    @Test
    void aLambdaBlockHoldingOneCallKeepsItsBracesWithoutTargetTypeInformation() {
        String source = method("        run(x -> { log(x); });");
        assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged());
    }

    @Test
    void aReturnedStatementExpressionKeepsItsBracesBecauseOverloadResolutionCanChange() {
        String source =
                """
                import java.util.function.Consumer;
                import java.util.function.Function;

                class T {

                    String task(String value) {
                        return value;
                    }

                    String pick(Function<String, String> function) {
                        return "function";
                    }

                    String pick(Consumer<String> consumer) {
                        return "consumer";
                    }

                    String choose() {
                        return pick(value -> { return task(value); });
                    }

                }
                """;

        assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged());
    }

    @Test
    void everyReturnedStatementExpressionKeepsItsBraces() {
        for (String expression : new String[] {"log(x)", "x = 1", "x++", "++x", "new Object()"}) {
            String source = method("        run(x -> { return " + expression + "; });");
            assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged(), expression);
        }
    }

    @Test
    void aLambdaBodyThatIsNotOneExpressionKeepsItsBraces() {
        String[] bodies = {"{ }", "{ log(x); log(x); }", "{ return; }", "{ int y = x; }", "{ if (x) f(); }"};
        for (String body : bodies) {
            String source = method("        run(x -> " + body + ");");
            assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged(), body);
        }
    }

    @Test
    void whenMultiStatementCollapsesTheOneStatementBodyAndLeavesTheRest() {
        assertTrue(
                format(method("        run(x -> { return x; });"), LambdaRules.BODY_BRACES,
                                BracePolicy.WHEN_MULTI_STATEMENT)
                        .contains("run(x -> x);"));
        assertTrue(
                rewrite(method("        run(x -> { f(); g(); });"), LambdaRules.BODY_BRACES,
                                BracePolicy.WHEN_MULTI_STATEMENT)
                        .unchanged());
    }

    @Test
    void alwaysDeclinesBecauseTheTargetTypeDecidesWhatTheBlockWouldSay() {
        assertTrue(rewrite(method("        run(x -> x + 1);"), LambdaRules.BODY_BRACES, BracePolicy.ALWAYS)
                .unchanged());
    }

    @Test
    void aBraceCarryingACommentKeepsTheBody() {
        String source = method("        run(x -> { // note\n            return x;\n        });");
        assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged());
    }

    // ----------------------------------------------------- sealed.permits-order

    private static final String SEALED = "sealed interface I permits C, A, B {\n}\n";

    @Test
    void permittedTypesSortAscending() {
        assertTrue(
                format(SEALED, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING).contains("permits A, B, C"),
                format(SEALED, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING));
    }

    @Test
    void permittedTypesSortDescending() {
        assertTrue(format(SEALED, SealedRules.PERMITS_ORDER, SortOrder.DESCENDING).contains("permits C, B, A"));
    }

    @Test
    void permitsPreserveAndAnAlreadySortedClauseTouchNothing() {
        assertTrue(rewrite(SEALED, SealedRules.PERMITS_ORDER, SortOrder.PRESERVE).unchanged());
        assertTrue(
                rewrite("sealed interface I permits A, B {\n}\n", SealedRules.PERMITS_ORDER, SortOrder.ASCENDING)
                        .unchanged());
    }

    @Test
    void aSinglePermittedTypeIsAlreadyInOrder() {
        assertTrue(
                rewrite("sealed interface I permits A {\n}\n", SealedRules.PERMITS_ORDER, SortOrder.ASCENDING)
                        .unchanged());
    }

    @Test
    void aQualifiedNameSortsOnItsWholeText() {
        String source = "sealed interface I permits b.B, a.A {\n}\n";
        assertTrue(format(source, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING).contains("permits a.A, b.B"));
    }

    // ------------------------------------------------ switch.arrow-case-braces

    private static String statementSwitch(String cases) {
        return method("        switch (n) {\n" + cases + "\n        }");
    }

    @Test
    void bracesAreAddedRoundAStatementArrowBody() {
        String formatted =
                format(statementSwitch("            case 1 -> f();"),
                        SwitchRules.ARROW_CASE_BRACES,
                        BracePolicy.ALWAYS);
        assertTrue(formatted.contains("case 1 -> {"), formatted);
    }

    @Test
    void bracesComeOffAOneStatementArrowBody() {
        String source = statementSwitch("            case 1 -> { f(); }");
        assertTrue(
                format(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.NEVER).contains("case 1 -> f();"));
        assertTrue(
                format(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.WHEN_MULTI_STATEMENT).contains(
                        "case 1 -> f();"));
    }

    @Test
    void aBodyWithNoUnbracedFormKeepsItsBraces() {
        for (String body : new String[] {"{ f(); g(); }", "{ int x = 1; }", "{ if (n > 0) f(); }", "{ }"}) {
            String source = statementSwitch("            case 1 -> " + body);
            assertTrue(rewrite(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.NEVER).unchanged(), body);
        }
    }

    @Test
    void anExpressionSwitchIsNotTheBraceRulesBusiness() {
        String source = method("        int v = switch (n) {\n            case 1 -> { yield 2; }\n        };");
        assertTrue(rewrite(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.NEVER).unchanged());
        assertTrue(rewrite(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.ALWAYS).unchanged());
    }

    // ------------------------------------------------------ switch.yield-style

    private static String expressionSwitch(String cases) {
        return method("        int v = switch (n) {\n" + cases + "\n        };");
    }

    @Test
    void aLoneYieldBecomesAnExpressionBody() {
        String source = expressionSwitch("            case 1 -> { yield 2; }");
        String formatted = format(source, SwitchRules.YIELD_STYLE, YieldStyle.EXPRESSION_WHEN_POSSIBLE);
        assertTrue(formatted.contains("case 1 -> 2;"), formatted);
    }

    @Test
    void anExpressionBodyBecomesABlockWithAYield() {
        String source = expressionSwitch("            case 1 -> 2;");
        String formatted = format(source, SwitchRules.YIELD_STYLE, YieldStyle.ALWAYS_BLOCK);
        assertTrue(formatted.contains("yield 2;"), formatted);
        assertTrue(formatted.contains("case 1 -> {"), formatted);
    }

    @Test
    void aBlockThatDoesMoreThanYieldStaysABlock() {
        String source = expressionSwitch("            case 1 -> { f(); yield 2; }");
        assertTrue(rewrite(source, SwitchRules.YIELD_STYLE, YieldStyle.EXPRESSION_WHEN_POSSIBLE).unchanged());
    }

    @Test
    void aThrowHasNoValueToYield() {
        String source = expressionSwitch("            case 1 -> throw new E();");
        assertTrue(rewrite(source, SwitchRules.YIELD_STYLE, YieldStyle.ALWAYS_BLOCK).unchanged());
    }

    @Test
    void aStatementSwitchIsNotTheYieldRulesBusiness() {
        String source = statementSwitch("            case 1 -> f();");
        assertTrue(rewrite(source, SwitchRules.YIELD_STYLE, YieldStyle.ALWAYS_BLOCK).unchanged());
    }

    @Test
    void yieldStyleRoundTripsBothWays() {
        String block = expressionSwitch("            case 1 -> { yield 2; }");
        String expression = format(block, SwitchRules.YIELD_STYLE, YieldStyle.EXPRESSION_WHEN_POSSIBLE);
        assertTrue(expression.contains("case 1 -> 2;"));
        assertTrue(
                format(expression, SwitchRules.YIELD_STYLE, YieldStyle.ALWAYS_BLOCK).contains("yield 2;"));
    }

    // ---------------------------------------------------------- fixed point

    @Test
    void everyRuleSettlesAfterOnePass() {
        assertSettles(method("        run((x) -> { return x + 1; });"), LambdaRules.BODY_BRACES, BracePolicy.NEVER);
        assertSettles(
                method("        run((x) -> 1);"), LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE);
        assertSettles(SEALED, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING);
        assertSettles(statementSwitch("            case 1 -> { f(); }"), SwitchRules.ARROW_CASE_BRACES,
                BracePolicy.NEVER);
        assertSettles(expressionSwitch("            case 1 -> { yield 2; }"), SwitchRules.YIELD_STYLE,
                YieldStyle.EXPRESSION_WHEN_POSSIBLE);
    }

    private static <T> void assertSettles(String source, Option<T> option, T value) {
        String once = format(source, option, value);
        assertEquals(once, format(once, option, value), option.key());
    }

}
