package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.ArrayRules;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.BracketStyle;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.LambdaParameterStyle;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.SealedRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.YieldStyle;
import zone.rong.formatj.core.cst.GreenNode;
import zone.rong.formatj.core.cst.ProgramTokens;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.rewrite.RewriteResult;
import zone.rong.formatj.core.rewrite.RewriteStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** A style whose only token-changing rule is the one under test. */
    private static <T> Style only(Option<T> option, T value) {
        return Style.builder()
            .set(BraceRules.IF_ELSE, BracePolicy.PRESERVE)
            .set(BraceRules.FOR_LOOP, BracePolicy.PRESERVE)
            .set(BraceRules.WHILE_LOOP, BracePolicy.PRESERVE)
            .set(LambdaRules.BODY_BRACES, BracePolicy.PRESERVE)
            .set(ImportRules.ORDER, SortOrder.PRESERVE)
            .set(option, value)
            .build();
    }

    private static <T> String format(String source, Option<T> option, T value) {
        return FormatJ.newFormatter().style(only(option, value)).previewFeatures(true).build().format(source);
    }

    private static <T> RewriteResult rewrite(String source, Option<T> option, T value) {
        GreenNode root = JavaParser.parse(source, LanguageLevel.LATEST, true).root().green();
        return RewriteStage.apply(root, only(option, value));
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
                "run((x) -> x + 1);"
            )
        );
    }

    @Test
    void parenthesesAreRemovedFromALoneUntypedParameter() {
        String source = method("        run((x) -> x + 1);");
        assertTrue(
            format(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE).contains(
                "run(x -> x + 1);"
            )
        );
    }

    @Test
    void parenthesesAreAddedRoundAnUnnamedParameter() {
        String source = method("        run(_ -> 1);");
        assertTrue(
            format(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.ALWAYS_PARENTHESISE).contains(
                "run((_) -> 1);"
            )
        );
    }

    @Test
    void parenthesesAreRemovedFromALoneUnnamedParameter() {
        String source = method("        run((_) -> 1);");
        assertTrue(
            format(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE).contains(
                "run(_ -> 1);"
            )
        );
    }

    @Test
    void parenthesesTheLanguageRequiresAreKept() {
        for (String parameters : new String[] {
            "()",
            "(a, b)",
            "(int x)",
            "(var x)",
            "(final x)",
            "(@A x)",
            "(_, x)",
            "(int _)",
            "(var _)"
        }) {
            String source = method("        run(" + parameters + " -> 1);");
            assertTrue(
                rewrite(source, LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.OMIT_WHEN_POSSIBLE).unchanged(),
                parameters
            );
        }
    }

    @Test
    void preserveLeavesEitherFormAlone() {
        assertTrue(
            rewrite(
                method("        run(x -> 1);"),
                LambdaRules.PARAMETER_STYLE,
                LambdaParameterStyle.PRESERVE
            ).unchanged()
        );
        assertTrue(
            rewrite(
                method("        run((x) -> 1);"),
                LambdaRules.PARAMETER_STYLE,
                LambdaParameterStyle.PRESERVE
            ).unchanged()
        );
    }

    // ----------------------------------------------------- lambdas.body-braces

    @Test
    void aLambdaBlockReturningAValueCollapsesToTheExpression() {
        String source = method("        run(x -> { return x + 1; });");
        assertTrue(format(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).contains("run(x -> x + 1);"));
    }

    @Test
    void aLambdaBlockHoldingOneCallCollapsesToTheCall() {
        String source = method("        run(x -> { log(x); });");
        assertTrue(format(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).contains("run(x -> log(x));"));
    }

    @Test
    void aReturnedStatementExpressionCollapsesLikeAnyOtherValue() {
        for (String expression : new String[] { "log(x)", "x = 1", "x++", "++x", "new Object()" }) {
            String source = method("        run(x -> { return " + expression + "; });");
            assertTrue(
                format(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).contains("run(x -> " + expression + ");"),
                expression
            );
        }
    }

    @Test
    void aLambdaBodyThatIsNotOneExpressionKeepsItsBraces() {
        String[] bodies = { "{ }", "{ log(x); log(x); }", "{ return; }", "{ int y = x; }", "{ if (x) f(); }" };
        for (String body : bodies) {
            String source = method("        run(x -> " + body + ");");
            assertTrue(rewrite(source, LambdaRules.BODY_BRACES, BracePolicy.NEVER).unchanged(), body);
        }
    }

    @Test
    void whenMultiStatementCollapsesTheOneStatementBodyAndLeavesTheRest() {
        assertTrue(
            format(
                method("        run(x -> { return x; });"),
                LambdaRules.BODY_BRACES,
                BracePolicy.WHEN_MULTI_STATEMENT
            ).contains("run(x -> x);")
        );
        assertTrue(
            rewrite(
                method("        run(x -> { f(); g(); });"),
                LambdaRules.BODY_BRACES,
                BracePolicy.WHEN_MULTI_STATEMENT
            ).unchanged()
        );
    }

    @Test
    void alwaysDeclinesBecauseTheTargetTypeDecidesWhatTheBlockWouldSay() {
        assertTrue(
            rewrite(method("        run(x -> x + 1);"), LambdaRules.BODY_BRACES, BracePolicy.ALWAYS).unchanged()
        );
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
            format(SEALED, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING)
        );
    }

    @Test
    void permittedTypesSortDescending() {
        assertTrue(format(SEALED, SealedRules.PERMITS_ORDER, SortOrder.DESCENDING).contains("permits C, B, A"));
    }

    @Test
    void permitsPreserveAndAnAlreadySortedClauseTouchNothing() {
        assertTrue(rewrite(SEALED, SealedRules.PERMITS_ORDER, SortOrder.PRESERVE).unchanged());
        assertTrue(
            rewrite(
                "sealed interface I permits A, B {\n}\n",
                SealedRules.PERMITS_ORDER,
                SortOrder.ASCENDING
            ).unchanged()
        );
    }

    @Test
    void aSinglePermittedTypeIsAlreadyInOrder() {
        assertTrue(
            rewrite("sealed interface I permits A {\n}\n", SealedRules.PERMITS_ORDER, SortOrder.ASCENDING).unchanged()
        );
    }

    @Test
    void aQualifiedNameSortsOnItsWholeText() {
        String source = "sealed interface I permits b.B, a.A {\n}\n";
        assertTrue(format(source, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING).contains("permits a.A, b.B"));
    }

    // --------------------------------------------------------- modifiers.order

    private static final String MODIFIERS = """
            sealed public abstract class T permits T.Child {

                @Second static @First public final int field = 1;

                synchronized final public void run() {
                }

                @First private T() {
                }

                static public interface Nested {
                }

                static public enum Choice {
                    ONE
                }

                static public record Pair(int value) {
                }

                abstract public @interface Marker {
                }

                static non-sealed public class Child extends T {
                }

            }

            @interface First {
            }

            @interface Second {
            }
            """;

    @Test
    void modifierOrderIsPreservedByDefault() {
        assertEquals(ModifierOrder.PRESERVE, ModifierRules.ORDER.defaultValue());
        GreenNode root = JavaParser.parse(MODIFIERS, LanguageLevel.LATEST, true).root().green();
        assertTrue(RewriteStage.apply(root, Style.builder().build()).unchanged());
    }

    @Test
    void declarationKindsUseTheirCanonicalModifierOrders() {
        String formatted = format(MODIFIERS, ModifierRules.ORDER, ModifierOrder.CANONICAL);
        GreenNode root = JavaParser.parse(formatted, LanguageLevel.LATEST, true).root().green();
        String rewritten = String.join(" ", ProgramTokens.lexemes(root));
        assertTrue(rewritten.contains("public abstract sealed class T"), rewritten);
        assertTrue(rewritten.contains("@ Second public @ First static final int field"), rewritten);
        assertTrue(rewritten.contains("public final synchronized void run"), rewritten);
        assertTrue(rewritten.contains("@ First private T ( )"), rewritten);
        assertTrue(rewritten.contains("public static interface Nested"), rewritten);
        assertTrue(rewritten.contains("public static enum Choice"), rewritten);
        assertTrue(rewritten.contains("public static record Pair"), rewritten);
        assertTrue(rewritten.contains("public abstract @ interface Marker"), rewritten);
        assertTrue(rewritten.contains("public static non - sealed class Child"), rewritten);
    }

    @Test
    void escapedModifierKeywordsKeepTheirSpelling() {
        String source = "static \\u0070ublic class T {\n}\n";
        String rewritten = tokens(source, ModifierRules.ORDER, ModifierOrder.CANONICAL);
        assertTrue(rewritten.startsWith("\\u0070ublic static class T"), rewritten);
    }

    @Test
    void commentsAndMalformedModifierListsLeaveTheSequenceAlone() {
        assertTrue(
            rewrite(
                "static /* boundary */ public class T {\n}\n",
                ModifierRules.ORDER,
                ModifierOrder.CANONICAL
            ).unchanged()
        );
        assertTrue(
            rewrite("static public public class T {\n}\n", ModifierRules.ORDER, ModifierOrder.CANONICAL).unchanged()
        );
        assertTrue(rewrite("native public class T {\n}\n", ModifierRules.ORDER, ModifierOrder.CANONICAL).unchanged());
    }

    @Test
    void formatterOffRegionsKeepTheirModifierOrder() {
        String source = """
                class T {
                    static public int sorted;
                    // @formatter:off
                    static public int untouched;
                    // @formatter:on
                }
                """;
        String formatted = format(source, ModifierRules.ORDER, ModifierOrder.CANONICAL);
        assertTrue(formatted.contains("static public int untouched;"), formatted);
        assertTrue(formatted.contains("public static int sorted;"), formatted);
    }

    @Test
    void modifierOrderingComposesWithOtherRewrites() {
        String source = """
                sealed public interface I permits C, A, B {
                    static public void run(boolean ready) {
                        if (ready) work();
                    }
                }
                """;
        Style style = Style.builder()
            .modifiers(modifiers -> modifiers.order(ModifierOrder.CANONICAL))
            .sealedTypes(sealed -> sealed.permitsOrder(SortOrder.ASCENDING))
            .braces(braces -> braces.ifElse(BracePolicy.ALWAYS))
            .build();
        String formatted = FormatJ.newFormatter().style(style).build().format(source);
        assertTrue(formatted.contains("public sealed interface I permits A, B, C"), formatted);
        assertTrue(formatted.contains("public static void run"), formatted);
        assertTrue(formatted.contains("if (ready) {"), formatted);
    }

    // ------------------------------------------------ switch.arrow-case-braces

    private static String statementSwitch(String cases) {
        return method("        switch (n) {\n" + cases + "\n        }");
    }

    @Test
    void bracesAreAddedRoundAStatementArrowBody() {
        String formatted = format(
            statementSwitch("            case 1 -> f();"),
            SwitchRules.ARROW_CASE_BRACES,
            BracePolicy.ALWAYS
        );
        assertTrue(formatted.contains("case 1 -> {"), formatted);
    }

    @Test
    void bracesComeOffAOneStatementArrowBody() {
        String source = statementSwitch("            case 1 -> { f(); }");
        assertTrue(format(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.NEVER).contains("case 1 -> f();"));
        assertTrue(
            format(source, SwitchRules.ARROW_CASE_BRACES, BracePolicy.WHEN_MULTI_STATEMENT).contains("case 1 -> f();")
        );
    }

    @Test
    void aBodyWithNoUnbracedFormKeepsItsBraces() {
        for (String body : new String[] { "{ f(); g(); }", "{ int x = 1; }", "{ if (n > 0) f(); }", "{ }" }) {
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
        assertTrue(format(expression, SwitchRules.YIELD_STYLE, YieldStyle.ALWAYS_BLOCK).contains("yield 2;"));
    }

    // ------------------------------------------------ modifiers.remove-redundant

    private static String removeRedundant(String source) {
        return format(source, ModifierRules.REMOVE_REDUNDANT, true);
    }

    @Test
    void interfaceMembersLoseWhatTheyImply() {
        String source = """
                interface T {

                    public static final int X = 1;

                    public abstract void a();

                    public default void b() {}

                    public static void c() {}

                    private void d() {}

                    public static class N {}
                }
                """;
        assertEquals(
            """
                interface T {

                    int X = 1;

                    void a();

                    default void b() { }

                    static void c() { }

                    private void d() { }

                    class N { }

                }
                """,
            removeRedundant(source)
        );
    }

    @Test
    void nestedEnumsRecordsAndInterfacesLoseStatic() {
        String source = """
                class T {

                    public static enum E {
                        A
                    }

                    static record R(int x) {}

                    private static interface I {}

                    static class C {}
                }
                """;
        assertEquals(
            """
                class T {

                    public enum E {

                        A

                    }

                    record R(int x) { }

                    private interface I { }

                    static class C { }

                }
                """,
            removeRedundant(source)
        );
    }

    @Test
    void privateFinalMethodsEnumConstructorsAndResourcesAreSimplified() {
        String source = """
                enum E {
                    A;

                    private E() {}

                    private final void a() {}

                    public final void b() {}

                    void c() throws Exception {
                        try (final var r = open()) {}
                    }
                }
                """;
        assertEquals(
            """
                enum E {

                    A;

                    E() { }

                    private void a() { }

                    public final void b() { }

                    void c() throws Exception {
                        try (var r = open()) {
                        }
                    }

                }
                """,
            removeRedundant(source)
        );
    }

    @Test
    void aDocCommentAndBlankLineSurviveRemovingTheFirstModifier() {
        String source = """
                interface T {

                    int a();

                    /** Docs. */
                    public abstract void b();
                }
                """;
        assertEquals(
            """
                interface T {

                    int a();

                    /** Docs. */
                    void b();

                }
                """,
            removeRedundant(source)
        );
    }

    @Test
    void redundantModifiersWithCommentsStay() {
        String source = "interface T {\n\n    public /* api */ void a();\n\n}\n";
        assertEquals(source, removeRedundant(source));
    }

    // ------------------------------------------------- arrays.c-style-brackets

    private static String javaBrackets(String source) {
        return format(source, ArrayRules.C_STYLE_BRACKETS, BracketStyle.JAVA);
    }

    @Test
    void fieldsLocalsAndParametersTakeTheirBracketsOnTheType() {
        String source = """
                class T {

                    int a[] = {1};

                    void run(String args[], int[] ok, int m[][]) {
                        long l[] = new long[1];
                        for (int i[] = null;;) {}
                    }
                }
                """;
        assertEquals(
            """
                class T {

                    int[] a = { 1 };

                    void run(String[] args, int[] ok, int[][] m) {
                        long[] l = new long[1];
                        for (int[] i = null;;) {
                        }
                    }

                }
                """,
            javaBrackets(source)
        );
    }

    @Test
    void declaratorsMoveTogetherOrNotAtAll() {
        assertEquals("class T {\n\n    int[] a, b;\n\n}\n", javaBrackets("class T {\n\n    int a[], b[];\n\n}\n"));
        String mixed = "class T {\n\n    int a[], b;\n\n}\n";
        assertEquals(mixed, javaBrackets(mixed));
    }

    @Test
    void annotatedCommentedAndVarargsBracketsStay() {
        String source = """
                class T {

                    int a@A[];

                    int b[/* c */];

                    int m()[] {
                        return null;
                    }

                    void v(String... a[]) { }

                }
                """;
        assertEquals(source, javaBrackets(source));
    }

    // ---------------------------------------------------------- fixed point

    @Test
    void everyRuleSettlesAfterOnePass() {
        assertSettles(method("        run((x) -> { return x + 1; });"), LambdaRules.BODY_BRACES, BracePolicy.NEVER);
        assertSettles(
            method("        run((x) -> 1);"),
            LambdaRules.PARAMETER_STYLE,
            LambdaParameterStyle.OMIT_WHEN_POSSIBLE
        );
        assertSettles(SEALED, SealedRules.PERMITS_ORDER, SortOrder.ASCENDING);
        assertSettles(MODIFIERS, ModifierRules.ORDER, ModifierOrder.CANONICAL);
        assertSettles(
            statementSwitch("            case 1 -> { f(); }"),
            SwitchRules.ARROW_CASE_BRACES,
            BracePolicy.NEVER
        );
        assertSettles(
            expressionSwitch("            case 1 -> { yield 2; }"),
            SwitchRules.YIELD_STYLE,
            YieldStyle.EXPRESSION_WHEN_POSSIBLE
        );
    }

    private static <T> void assertSettles(String source, Option<T> option, T value) {
        String once = format(source, option, value);
        assertEquals(once, format(once, option, value), option.key());
    }

}
