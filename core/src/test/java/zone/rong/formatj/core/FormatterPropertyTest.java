package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.TextBlockIndentPolicy;
import zone.rong.formatj.api.rules.TextBlockRules;
import zone.rong.formatj.core.text.TextBlocks;
import org.junit.jupiter.api.Test;

/**
 * Property-style checks that drive the shipped formatter, parser and rewrite on source strings.
 *
 * <p>Each case would fail if the behaviour it names were dropped: Unicode text-block incidental
 * whitespace, comments at statement boundaries, overload-sensitive lambda collapse, preview syntax,
 * error recovery that still formats the rest of the file, and format-twice stability.
 */
class FormatterPropertyTest {

    private static FormatResult format(String source) {
        return FormatJ.defaultFormatter().format(FormatRequest.of(source).withName("T.java"));
    }

    private static FormatResult format(String source, Style style) {
        return FormatJ.newFormatter().style(style).build().format(FormatRequest.of(source).withName("T.java"));
    }

    private static void assertFixedPoint(Formatter formatter, String source) {
        FormatResult once = formatter.format(FormatRequest.of(source).withName("T.java"));
        assertFalse(once.hasErrors(), () -> once.diagnostics().toString());
        FormatResult twice = formatter.format(FormatRequest.of(once.text()).withName("T.java"));
        assertFalse(twice.hasErrors(), () -> twice.diagnostics().toString());
        assertEquals(once.text(), twice.text(), "formatting must be a fixed point");
    }

    @Test
    void unicodeWhitespaceInATextBlockIsContentUnderEveryIndentPolicy() {
        String[] unicode = {"\u00a0", "\u1680", "\u2003", "\u202f", "\u3000"};
        for (TextBlockIndentPolicy policy : TextBlockIndentPolicy.values()) {
            Style style = Style.builder().set(TextBlockRules.INDENT_POLICY, policy).build();
            for (String space : unicode) {
                String block = "\"\"\"\n        " + space + "keep\n        \"\"\"";
                String source = "class T {\n\n    String q() {\n        return " + block + ";\n    }\n\n}\n";
                FormatResult result = format(source, style);
                assertFalse(result.hasErrors(), () -> policy + " " + result.diagnostics());
                assertTrue(
                        result.text().contains(space),
                        () -> policy + " dropped " + Integer.toHexString(space.charAt(0)) + " from\n" + result.text());
                int start = result.text().indexOf("\"\"\"");
                int end = result.text().indexOf("\"\"\"", start + 3);
                String rewritten = result.text().substring(start, end + 3);
                assertEquals(TextBlocks.value(block), TextBlocks.value(rewritten), policy::toString);
            }
        }
    }

    @Test
    void commentsAtStatementBoundariesStayPutAndDoNotWrapAFittingChain() {
        String source = """
                class T {

                    void run() {
                        // before first
                        int a = 1; // trailing
                        /* between */ int b = 2;
                        if (ready)
                            // unbraced body
                            project.getTasks().withType(T.class).configureEach(t -> t.setX(1));
                        if (ready) {
                            // in block
                            project.getTasks().withType(T.class).configureEach(t -> t.setY(2));
                        } // after if
                        // before last
                        return;
                    }

                }
                """;

        FormatResult result = format(source);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        String text = result.text();
        assertTrue(text.contains("        // before first\n"), text);
        assertTrue(text.contains("int a = 1; // trailing"), text);
        assertTrue(text.contains("/* between */"), text);
        assertTrue(
                text.contains(
                        "            // unbraced body\n"
                                + "            project.getTasks().withType(T.class).configureEach(t -> t.setX(1));"),
                text);
        assertTrue(
                text.contains(
                        "            // in block\n"
                                + "            project.getTasks().withType(T.class).configureEach(t -> t.setY(2));"),
                text);
        assertTrue(text.contains("} // after if"), text);
        assertTrue(text.contains("        // before last\n        return;"), text);
        assertFixedPoint(FormatJ.defaultFormatter(), source);
    }

    @Test
    void collapsingAReturnedCallKeepsBracesWhenOverloadResolutionCouldChange() {
        String source = """
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
                        return pick(value -> {
                            return task(value);
                        });
                    }

                }
                """;

        Style style = Style.builder().set(LambdaRules.BODY_BRACES, BracePolicy.NEVER).build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("value -> {"), result.text());
        assertTrue(result.text().contains("return task(value);"), result.text());
        assertFalse(result.text().contains("pick(value -> task(value))"), result.text());
        assertFixedPoint(formatter, source);
    }

    @Test
    void previewDerivedRecordCreationFormatsAndIsAFixedPoint() {
        String source = """
                class T {

                    Point moved(Point p) {
                        return p with { x = 1; y = 2; };
                    }

                }
                """;

        Formatter formatter = FormatJ.newFormatter().previewFeatures(true).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("T.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("with"), result.text());
        assertTrue(result.text().contains("x = 1"), result.text());
        assertFixedPoint(formatter, source);

        FormatResult withoutPreview = FormatJ.newFormatter()
                .previewFeatures(false)
                .build()
                .format(FormatRequest.of(source).withName("T.java"));
        assertFalse(withoutPreview.hasErrors(), () -> withoutPreview.diagnostics().toString());
        assertTrue(withoutPreview.text().contains("p with { x = 1; y = 2; }"), withoutPreview.text());
    }

    @Test
    void aBrokenStatementIsLeftVerbatimAndTheRestOfTheFileStillFormats() {
        String source = "class T{void broken(){int x = = 1;}void ok(){int y=2;}}\n";

        FormatResult result = format(source);
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("int x = = 1;"), result.text());
        assertTrue(result.text().contains("int y = 2;"), result.text());
        assertTrue(result.text().contains("void ok()"), result.text());
        assertTrue(
                result.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING),
                () -> result.diagnostics().toString());
        assertTrue(result.text().contains("class T {") || result.text().contains("class T{"), result.text());

        FormatResult twice = FormatJ.defaultFormatter().format(FormatRequest.of(result.text()).withName("T.java"));
        assertFalse(twice.hasErrors(), () -> twice.diagnostics().toString());
        assertEquals(result.text(), twice.text());
    }

    @Test
    void aTopLevelUnparsedRegionDoesNotBlockNeighbouringTypes() {
        // A method cannot sit at the compilation-unit level. Recovery must keep that region
        // verbatim and still lay out the well-formed types on either side.
        String source = "class Good{int y=2;} void bad(){int x=1;} class Also{int z=3;}";

        FormatResult result = FormatJ.defaultFormatter().format(FormatRequest.of(source).withName("T.java"));

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(
                result.diagnostics().stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR),
                () -> result.diagnostics().toString());
        assertTrue(result.text().contains("int y = 2"), result.text());
        assertTrue(result.text().contains("int z = 3"), result.text());
        assertTrue(result.text().contains("void bad()"), result.text());
        assertTrue(result.text().contains("int x=1") || result.text().contains("int x = 1"), result.text());
        assertTrue(result.text().contains("class Good"), result.text());
        assertTrue(result.text().contains("class Also"), result.text());

        FormatResult twice = FormatJ.defaultFormatter().format(FormatRequest.of(result.text()).withName("T.java"));
        assertFalse(twice.hasErrors(), () -> twice.diagnostics().toString());
        assertEquals(result.text(), twice.text());
    }

    @Test
    void formattingTwiceIsAFixedPointForMessyAndRewriteStyles() {
        String messy = "package a;class A{void run(){if(x){g();}else{h();}list.stream().map(v->v+1).toList();}}\n";
        assertFixedPoint(FormatJ.defaultFormatter(), messy);

        Style bracing = Style.builder().set(LambdaRules.BODY_BRACES, BracePolicy.NEVER).build();
        assertFixedPoint(FormatJ.newFormatter().style(bracing).build(), messy);

        String withPreview = "class T { Point moved(Point p) { return p with { x = 1; }; } }\n";
        assertFixedPoint(FormatJ.newFormatter().previewFeatures(true).build(), withPreview);

        String unnamed = """
                class T{void run(){run(_ ->1);run((_,x)->x);run((int _)->0);var _=sideEffect();
                for(int i=0,_=sideEffect();i<10;i++){g(i);}for(int _:xs){n++;}
                try(var _=open()){work();}catch(Exception _){log();}}}
                """;
        assertFixedPoint(FormatJ.defaultFormatter(), unnamed);
    }

}
