package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.OperatorWrap;
import zone.rong.formatj.api.rules.WrapPolicy;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SeparatedOperatorLayoutTest {

    @Test
    void defaultsKeepTheExistingFlatForms() {
        String source = """
                class A<T extends First & Second> {

                    Object value;

                    void f() {
                        java.util.function.Function<String, String> reference = A::<String>identity;
                        boolean matched = value instanceof String text;
                        Object cast = (First & Second) value;
                        try {
                            run();
                        } catch (FirstFailure | SecondFailure failure) {
                            handle(failure);
                        }
                    }

                }
                """;

        String formatted = formatFixedPoint(source, style -> { });

        assertTrue(formatted.contains("A::<String>identity"), formatted);
        assertTrue(formatted.contains("value instanceof String text"), formatted);
        assertTrue(formatted.contains("FirstFailure | SecondFailure failure"), formatted);
        assertTrue(formatted.contains("T extends First & Second"), formatted);
        assertTrue(formatted.contains("(First & Second) value"), formatted);
    }

    @Test
    void spacingControlsAreIndependent() {
        String source = """
                class A<T extends First & Second> {

                    void f(Object value) {
                        Object reference = A::<String>identity;
                        boolean matched = value instanceof String text;
                        Object cast = (First & Second) value;
                        try {
                            run();
                        } catch (FirstFailure | SecondFailure failure) {
                            handle(failure);
                        }
                    }

                }
                """;

        String formatted =
                formatFixedPoint(source, style -> style.spacing(spacing -> spacing
                        .aroundMethodReferenceOperator(true)
                        .aroundMulticatchSeparator(false)
                        .aroundIntersectionSeparator(false)));

        assertTrue(formatted.contains("A :: <String>identity"), formatted);
        assertTrue(formatted.contains("FirstFailure|SecondFailure failure"), formatted);
        assertTrue(formatted.contains("T extends First&Second"), formatted);
        assertTrue(formatted.contains("(First&Second) value"), formatted);
        assertTrue(formatted.contains("value instanceof String text"), formatted);
    }

    @Test
    void methodReferencesSupportEveryPolicyAndBothOperatorPositions() {
        String source = """
                class A {

                    Object f() {
                        return AnExceptionallyLongQualifierName::<AnExceptionallyLongTypeArgument>create;
                    }

                }
                """;
        for (WrapPolicy policy : WrapPolicy.values()) {
            String formatted =
                    formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                            .maxLineLength(55)
                            .methodReference(policy)));
            boolean broken = formatted.contains("AnExceptionallyLongQualifierName\n");
            assertEquals(
                    policy != WrapPolicy.NEVER && policy != WrapPolicy.PRESERVE,
                    broken,
                    () -> policy + ":\n" + formatted);
        }

        String before =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .methodReference(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .methodReferenceOperatorPosition(OperatorWrap.BEFORE_OPERATOR)));
        String after =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .methodReference(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .methodReferenceOperatorPosition(OperatorWrap.AFTER_OPERATOR)));
        String shortForm =
                formatFixedPoint(
                        "class A { Object f() { return A::create; } }\n",
                        style -> style.wrapping(wrapping -> wrapping.methodReference(WrapPolicy.WRAP_IF_LONG)));
        String preserved =
                formatFixedPoint(
                        "class A { Object f() { return A::\n        create; } }\n",
                        style -> style.wrapping(wrapping -> wrapping.methodReference(WrapPolicy.PRESERVE)));

        assertTrue(before.contains("AnExceptionallyLongQualifierName\n" + "                ::"), before);
        assertTrue(after.contains("AnExceptionallyLongQualifierName::\n"), after);
        assertTrue(shortForm.contains("return A::create;"), shortForm);
        assertFalse(preserved.contains("A::create"), preserved);
    }

    @Test
    void instanceofPolicyExplicitlyOverridesSimplePatternInlining() {
        String source = """
                class A {

                    boolean f(Object value) {
                        return value instanceof AnExceptionallyLongTypeName patternVariable;
                    }

                }
                """;
        for (WrapPolicy policy : WrapPolicy.values()) {
            String formatted =
                    formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                            .maxLineLength(45)
                            .instanceofExpression(policy)));
            assertEquals(
                    policy != WrapPolicy.NEVER && policy != WrapPolicy.PRESERVE,
                    formatted.contains("value instanceof\n"),
                    () -> policy + ":\n" + formatted);
        }

        String legacy = formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping.maxLineLength(45)));
        String patternAllowsWrap =
                formatFixedPoint(source, style -> style
                        .patterns(patterns -> patterns.keepSimplePatternInline(false))
                        .wrapping(wrapping -> wrapping.maxLineLength(45)));
        String explicit =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .instanceofExpression(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .instanceofOperatorPosition(OperatorWrap.BEFORE_OPERATOR)));

        assertTrue(legacy.contains("value instanceof AnExceptionallyLongTypeName patternVariable"), legacy);
        assertTrue(patternAllowsWrap.contains("value instanceof\n"), patternAllowsWrap);
        assertTrue(
                explicit.contains("value\n" + "                instanceof AnExceptionallyLongTypeName patternVariable"),
                explicit);
    }

    @Test
    void multicatchSupportsPoliciesPlacementAnnotationsAndComments() {
        String source = """
                class A {

                    void f() {
                        try {
                            run();
                        } catch (@One FirstLongFailure | @Two SecondLongFailure | ThirdLongFailure failure) {
                            handle(failure);
                        }
                    }

                }
                """;
        for (WrapPolicy policy : WrapPolicy.values()) {
            String formatted =
                    formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                            .maxLineLength(60)
                            .multicatch(policy)));
            assertEquals(
                    policy != WrapPolicy.NEVER && policy != WrapPolicy.PRESERVE,
                    formatted.contains("@One FirstLongFailure\n"),
                    () -> policy + ":\n" + formatted);
        }

        String before =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .multicatch(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .multicatchSeparatorPosition(OperatorWrap.BEFORE_OPERATOR)));
        String after =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .multicatch(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .multicatchSeparatorPosition(OperatorWrap.AFTER_OPERATOR)));
        String withComment =
                formatFixedPoint(
                        """
                class A {
                    void f() {
                        try { run(); } catch (FirstFailure | // keep this alternative
                                SecondFailure failure) { handle(failure); }
                    }
                }
                """,
                        style -> style.wrapping(wrapping -> wrapping.multicatch(WrapPolicy.CHOP_DOWN_ALWAYS)));

        assertTrue(before.contains("@One FirstLongFailure\n" + "                | @Two SecondLongFailure"), before);
        assertTrue(after.contains("@One FirstLongFailure |\n" + "                @Two SecondLongFailure"), after);
        assertTrue(withComment.contains("// keep this alternative"), withComment);
    }

    @Test
    void intersectionsSharePoliciesAndPlacementAcrossBoundsAndCasts() {
        String source = """
                class A<T extends @One FirstLongInterface & @Two SecondLongInterface & ThirdLongInterface> {

                    Object f(Object value) {
                        return (@One FirstLongInterface & @Two SecondLongInterface & ThirdLongInterface) value;
                    }

                }
                """;
        for (WrapPolicy policy : WrapPolicy.values()) {
            String formatted =
                    formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                            .maxLineLength(65)
                            .intersectionTypes(policy)));
            assertEquals(
                    policy != WrapPolicy.NEVER && policy != WrapPolicy.PRESERVE,
                    formatted.contains("@One FirstLongInterface\n"),
                    () -> policy + ":\n" + formatted);
        }

        String before =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .intersectionTypes(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .intersectionSeparatorPosition(OperatorWrap.BEFORE_OPERATOR)));
        String after =
                formatFixedPoint(source, style -> style.wrapping(wrapping -> wrapping
                        .intersectionTypes(WrapPolicy.CHOP_DOWN_ALWAYS)
                        .intersectionSeparatorPosition(OperatorWrap.AFTER_OPERATOR)));

        assertTrue(before.contains("@One FirstLongInterface\n" + "                & @Two SecondLongInterface"), before);
        assertTrue(
                before.contains("(@One FirstLongInterface\n" + "                & @Two SecondLongInterface"),
                before);
        assertTrue(after.contains("@One FirstLongInterface &\n" + "                @Two SecondLongInterface"), after);
        assertTrue(after.contains("(@One FirstLongInterface &\n" + "                @Two SecondLongInterface"), after);
    }

    @Test
    void commentsBetweenOperandsSurviveEveryConstruct() {
        String source = """
                class A<T extends First & /* bound */ Second> {

                    Object f(Object value) {
                        Object reference = A:: /* reference */ create;
                        boolean matched = value instanceof /* test */ String;
                        Object cast = (First & /* cast */ Second) value;
                        try {
                            run();
                        } catch (FirstFailure | /* catch */ SecondFailure failure) {
                            handle(failure);
                        }
                        return reference;
                    }

                }
                """;

        String formatted = formatFixedPoint(source, style -> { });

        assertTrue(formatted.contains("/* reference */"), formatted);
        assertTrue(formatted.contains("/* test */"), formatted);
        assertTrue(formatted.contains("/* catch */"), formatted);
        assertTrue(formatted.contains("/* bound */"), formatted);
        assertTrue(formatted.contains("/* cast */"), formatted);
    }

    private static String formatFixedPoint(String source, Consumer<StyleBuilder> configure) {
        StyleBuilder builder = Style.builder();
        configure.accept(builder);
        Formatter formatter = FormatJ.newFormatter().style(builder.build()).build();
        FormatResult once = formatter.format(FormatRequest.of(source).withName("A.java"));
        assertFalse(once.hasErrors(), () -> once.diagnostics().toString());
        FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName("A.java"));
        assertFalse(twice.hasErrors(), () -> twice.diagnostics().toString());
        assertEquals(once.text(), twice.text());
        return once.text();
    }

}
