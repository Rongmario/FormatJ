package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.SwitchRules;
import org.junit.jupiter.api.Test;

/**
 * {@code switch.case-style}, and the switches it has to refuse.
 *
 * <p>The refusals are the interesting half. This is the one rewrite whose safety is a precondition
 * rather than a check afterwards, so the tests that matter are the ones where the precondition is the
 * only thing standing between the formatter and a file that no longer means what it did.
 */
class SwitchCaseStyleTest {

    private static String format(SwitchCaseStyle caseStyle, String body) {
        String source = "class T {\n\n" + body + "\n}\n";
        FormatResult result = FormatJ.newFormatter()
                .style(Style.builder().set(SwitchRules.CASE_STYLE, caseStyle).build())
                .languageLevel(LanguageLevel.LATEST)
                .build()
                .format(FormatRequest.of(source).withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

    private static void unchanged(SwitchCaseStyle caseStyle, String body) {
        assertTrue(format(caseStyle, body).contains(":"), "the switch should have been left in colon form");
    }

    // --------------------------------------------------------- colon to arrow

    @Test
    void aGroupThatCannotFallThroughConverts() {
        String formatted =
                format(
                        SwitchCaseStyle.ARROW,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                g(n);
                                break;
                            default:
                                h(n);
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1 -> g(n);"), formatted);
        assertTrue(formatted.contains("default -> h(n);"), formatted);
    }

    @Test
    void emptyCasesBecomeMoreLabelsOnTheOneBelowThem() {
        String formatted =
                format(
                        SwitchCaseStyle.ARROW,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1:
                            case 2:
                            case 3:
                                g(n);
                                break;
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1, 2, 3 -> g(n);"), formatted);
    }

    @Test
    void aGroupWithSeveralStatementsGetsABlock() {
        String formatted =
                format(
                        SwitchCaseStyle.ARROW,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                g(n);
                                h(n);
                                break;
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1 -> {"), formatted);
    }

    @Test
    void aLoneYieldBecomesAnExpressionBody() {
        String formatted =
                format(
                        SwitchCaseStyle.ARROW,
                        """
                    int f(int n) {
                        return switch (n) {
                            case 1: yield 2;
                            default: yield 3;
                        };
                    }
                """);
        assertTrue(formatted.contains("case 1 -> 2;"), formatted);
        assertTrue(formatted.contains("default -> 3;"), formatted);
    }

    @Test
    void aSwitchThatFallsThroughIsRefused() {
        unchanged(
                SwitchCaseStyle.ARROW,
                """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                g(n);
                            case 2:
                                h(n);
                                break;
                        }
                    }
                """);
    }

    @Test
    void aGroupDeclaringALocalIsRefused() {
        unchanged(
                SwitchCaseStyle.ARROW,
                """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                int shared = n;
                                g(shared);
                                break;
                            default:
                                break;
                        }
                    }
                """);
    }

    @Test
    void aBreakThatLeavesTheSwitchFromInsideTheGroupIsRefused() {
        unchanged(
                SwitchCaseStyle.ARROW,
                """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                if (n > 0) {
                                    break;
                                }
                                g(n);
                                break;
                            default:
                                break;
                        }
                    }
                """);
    }

    @Test
    void aBreakBelongingToANestedLoopDoesNotStopTheConversion() {
        String formatted =
                format(
                        SwitchCaseStyle.ARROW,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1:
                                while (n > 0) {
                                    break;
                                }
                                break;
                            default:
                                break;
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1 -> {"), formatted);
    }

    @Test
    void aGuardedLabelIsNotMergedWithTheEmptyCasesAboveIt() {
        unchanged(
                SwitchCaseStyle.ARROW,
                """
                    void f(Object o) {
                        switch (o) {
                            case Integer i:
                            case String s when s.isEmpty():
                                g(o);
                                break;
                            default:
                                break;
                        }
                    }
                """);
    }

    // --------------------------------------------------------- arrow to colon

    @Test
    void anExpressionBodyGetsItsBreakBack() {
        String formatted =
                format(
                        SwitchCaseStyle.COLON,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1, 2 -> g(n);
                            default -> throw new IllegalStateException();
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1, 2:"), formatted);
        assertTrue(formatted.contains("break;"), formatted);
        assertFalse(formatted.contains("IllegalStateException();\n                break;"), formatted);
    }

    @Test
    void anExpressionSwitchGetsItsYieldBack() {
        String formatted =
                format(
                        SwitchCaseStyle.COLON,
                        """
                    int f(int n) {
                        return switch (n) {
                            case 1 -> 2;
                            default -> 3;
                        };
                    }
                """);
        assertTrue(formatted.contains("yield 2;"), formatted);
        assertTrue(formatted.contains("yield 3;"), formatted);
    }

    @Test
    void aBlockBodyIsRefusedBecauseNobodyKnowsWhereTheBreakGoes() {
        String formatted =
                format(
                        SwitchCaseStyle.COLON,
                        """
                    void f(int n) {
                        switch (n) {
                            case 1 -> {
                                g(n);
                                return;
                            }
                            default -> h(n);
                        }
                    }
                """);
        assertTrue(formatted.contains("case 1 -> {"), formatted);
        assertTrue(formatted.contains("default -> h(n);"), formatted);
    }

    @Test
    void preserveIsTheDefaultAndChangesNothing() {
        assertEquals(SwitchCaseStyle.PRESERVE, Style.defaults().get(SwitchRules.CASE_STYLE));
    }

}
