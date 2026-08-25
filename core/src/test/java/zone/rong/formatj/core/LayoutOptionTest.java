package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Layout rules that were in the catalogue before the emitter consulted them. */
class LayoutOptionTest {

    @Test
    void anEmptyMethodBodyFollowsItsOwnRuleNotTheClassBodyRule() {
        String source = """
                class A {

                    void f() {
                    }

                }
                """;

        String compact = format(source, style -> style.braces(braces -> braces.emptyMethodBody(EmptyBodyStyle.COMPACT)));

        assertTrue(compact.contains("void f() {}"), compact);
        // The class body keeps its own, separate rule.
        assertTrue(compact.contains("class A {\n"), compact);
    }

    @Test
    void anEmptyControlBodyIsUnaffectedByTheMethodBodyRule() {
        String source = "class A {\n    void f() {\n        while (x) {\n        }\n    }\n}\n";

        String compact = format(source, style -> style.braces(braces -> braces.emptyMethodBody(EmptyBodyStyle.COMPACT)));

        assertTrue(compact.contains("while (x) { }"), compact);
    }

    @Test
    void aLambdaBlockBraceFollowsTheLambdaPlacementRule() {
        String source = "class A {\n    void f() {\n        run(() -> {\n            g();\n        });\n    }\n}\n";

        String nextLine =
                format(source, style -> style.braces(braces -> braces.lambdaPlacement(BracePlacement.NEXT_LINE)));

        assertTrue(nextLine.contains("run(() ->\n"), nextLine);
        assertTrue(nextLine.contains("{\n"), nextLine);
        assertFalse(nextLine.contains("-> {"), nextLine);
    }

    @Test
    void aLambdaBlockBraceStaysOnTheArrowLineByDefault() {
        String source = "class A {\n    void f() {\n        run(() -> {\n            g();\n        });\n    }\n}\n";

        String formatted = format(source, style -> { });

        assertTrue(formatted.contains("() -> {"), formatted);
    }

    @Test
    void bracketSpacingAppliesToIndexesAndDimensions() {
        String source = "class A {\n    void f() {\n        int[] a = new int[n];\n        g(a[i]);\n    }\n}\n";

        String spaced = format(source, style -> style.spacing(spacing -> spacing.withinBrackets(true)));

        assertTrue(spaced.contains("new int[ n ]"), spaced);
        assertTrue(spaced.contains("a[ i ]"), spaced);
        // An empty pair has nothing to pad.
        assertTrue(spaced.contains("int[] a"), spaced);
    }

    @Test
    void aSpaceCanBeAskedForBeforeStatementSemicolons() {
        String source = """
                package a;

                class A {

                    int x = 1;

                    void f() {
                        g();
                        int y = 2;
                        return;
                    }

                    void h();

                }
                """;

        String spaced = format(source, style -> style.spacing(spacing -> spacing.beforeSemicolon(true)));

        assertTrue(spaced.contains("package a ;"), spaced);
        assertTrue(spaced.contains("int x = 1 ;"), spaced);
        assertTrue(spaced.contains("g() ;"), spaced);
        assertTrue(spaced.contains("int y = 2 ;"), spaced);
        assertTrue(spaced.contains("return ;"), spaced);
        assertTrue(spaced.contains("void h() ;"), spaced);
    }

    @Test
    void theForHeaderKeepsItsOwnSemicolonRule() {
        String source = "class A {\n    void f() {\n        for (int i = 0; i < n; i++) {\n            g();\n        }\n    }\n}\n";

        String spaced = format(source, style -> style.spacing(spacing -> spacing.beforeSemicolon(true)));

        assertTrue(spaced.contains("for (int i = 0; i < n; i++)"), spaced);
    }

    @Test
    void everyRuleUnderTestIsAFixedPoint() {
        String source = """
                package a;

                class A {

                    void f() {
                        int[] v = new int[n];
                        run(() -> {
                            g(v[0]);
                        });
                    }

                    void empty() {
                    }

                }
                """;
        Style style = Style.builder()
                .braces(braces -> braces
                        .emptyMethodBody(EmptyBodyStyle.COMPACT)
                        .lambdaPlacement(BracePlacement.NEXT_LINE))
                .spacing(spacing -> spacing.withinBrackets(true).beforeSemicolon(true))
                .build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();

        String once = formatter.format(FormatRequest.of(source).withName("A.java")).text();
        String twice = formatter.format(FormatRequest.of(once).withName("A.java")).text();

        assertEquals(once, twice);
    }


    @Test
    void anEmptyRecordBodyCanCollapseToOneToken() {
        String source = "record Point(int x, int y) {\n}\n";

        String collapsed = format(source, style -> style.records(records -> records.singleLineEmptyBody(true)));
        String spaced = format(source, style -> { });

        assertTrue(collapsed.contains("record Point(int x, int y) {}"), collapsed);
        assertTrue(spaced.contains("record Point(int x, int y) { }"), spaced);
    }

    @Test
    void aCompactConstructorCanBePaddedWithBlankLines() {
        String source = """
                record Range(int low, int high) {

                    Range {
                        check(low, high);
                    }

                }
                """;

        String padded =
                format(source, style -> style.records(records -> records.compactConstructorBlankLine(true)));

        assertTrue(padded.contains("Range {\n\n        check(low, high);\n\n    }"), padded);
    }

    @Test
    void thePermitsClauseCanStartOnItsOwnLine() {
        String source = "sealed interface Shape permits Circle, Square {\n}\n";

        String ownLine = format(source, style -> style.sealedTypes(sealed -> sealed.permitsOnNewLine(true)));

        assertTrue(ownLine.contains("sealed interface Shape\n        permits Circle, Square {"), ownLine);
    }

    @Test
    void extendsAndImplementsHonourTheirWrappingRule() {
        String source = "class A extends B implements C, D {\n}\n";

        String chopped = format(source, style -> style
                .wrapping(wrapping -> wrapping.extendsImplements(WrapPolicy.CHOP_DOWN_ALWAYS)));

        assertTrue(chopped.contains("class A extends B\n        implements C,\n        D {"), chopped);
    }

    @Test
    void aNeverWrappingImplementsClauseStaysOnOneLine() {
        String source = "class VeryLongClassNameIndeed implements AlphaInterface, BetaInterface, GammaInterface,"
                + " DeltaInterface {\n}\n";

        String flat = format(source, style -> style
                .wrapping(wrapping -> wrapping.extendsImplements(WrapPolicy.NEVER)));

        assertTrue(
                flat.contains("implements AlphaInterface, BetaInterface, GammaInterface, DeltaInterface {"),
                flat);
    }

    @Test
    void tryResourcesCanBeForcedToChopDown() {
        String source = "class A {\n    void f() {\n        try (X x = open()) {\n            g();\n        }\n    }\n}\n";

        String chopped =
                format(source, style -> style.wrapping(wrapping -> wrapping.tryResources(WrapPolicy.CHOP_DOWN_ALWAYS)));

        assertTrue(chopped.contains("try (\n"), chopped);
    }

    @Test
    void anEnumsFirstConstantHasItsOwnBlankLineRule() {
        String source = "enum Color {\n    RED,\n    GREEN;\n\n    void f() { }\n\n}\n";

        String tight = format(source, style -> style.blankLines(lines -> lines.beforeFirstEnumConstant(0)));

        assertTrue(tight.contains("enum Color {\n    RED,"), tight);
    }


    @Test
    void trailingWhitespaceInACommentCanBeKept() {
        String source = "class A {\n\n    /* padded   \n     * lines   \n     */\n    int x = 1;\n\n}\n";

        String trimmed = format(source, style -> { });
        String kept = format(source, style -> style.file(file -> file.trimTrailingWhitespace(false)));

        assertFalse(trimmed.contains("padded   \n"), trimmed);
        assertTrue(kept.contains("padded   \n"), kept);
    }

    @Test
    void theCharsetRuleResolvesToACharset() {
        Style utf16 = Style.builder().file(file -> file.charset("UTF-16")).build();
        Style nonsense = Style.builder().file(file -> file.charset("not-a-charset")).build();

        assertEquals(java.nio.charset.StandardCharsets.UTF_16, FileRules.charset(utf16));
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, FileRules.charset(Style.builder().build()));
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, FileRules.charset(nonsense));
    }


    @Test
    void aLoneMarkerAnnotationCanShareTheDeclarationLine() {
        String source = """
                class A {

                    @Override
                    public String toString() {
                        return "";
                    }

                    @Deprecated
                    @Override
                    public int hashCode() {
                        return 0;
                    }

                }
                """;

        String inline = format(source, style -> style
                .annotations(annotations -> annotations.singleMarkerInline(true)));

        assertTrue(inline.contains("@Override public String toString()"), inline);
        // Two annotations are no longer a lone marker, so they keep their own lines.
        assertTrue(inline.contains("@Deprecated\n    @Override\n    public int hashCode()"), inline);
    }

    @Test
    void anAnnotationWithArgumentsIsNotAMarker() {
        String source = "class A {\n\n    @SuppressWarnings(\"x\")\n    void f() { }\n\n}\n";

        String inline = format(source, style -> style
                .annotations(annotations -> annotations.singleMarkerInline(true)));

        assertTrue(inline.contains("@SuppressWarnings(\"x\")\n    void f()"), inline);
    }

    @Test
    void parametersAndLocalsFollowTheParameterPlacementRule() {
        String source = "class A {\n\n    void f(@NotNull String a) {\n        @Marked int b = 1;\n    }\n\n}\n";

        String ownLines = format(source, style -> style
                .annotations(annotations -> annotations.parameterPlacement(AnnotationPlacement.NEW_LINE)));

        assertTrue(ownLines.contains("@NotNull\n            String a"), ownLines);
        assertTrue(ownLines.contains("@Marked\n        int b = 1;"), ownLines);
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
