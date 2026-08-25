package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.core.parser.JavaParser;
import zone.rong.formatj.core.parser.ParseResult;
import zone.rong.formatj.core.pipeline.TokenEquivalence;
import org.junit.jupiter.api.Test;

class FormatterPipelineTest {

    private static final String SOURCE = """
            class A {
            
                void run() {
                    System.out.println("hello");
                }
                
            }
            """;

    @Test
    void theBuilderProducesAFormatterCarryingItsConfiguration() {
        Formatter formatter = FormatJ.newFormatter()
                .style(Preset.GOOGLE.style())
                .languageLevel(LanguageLevel.JAVA_21)
                .build();

        assertEquals(LanguageLevel.JAVA_21, formatter.languageLevel());
        assertEquals(Preset.GOOGLE.style(), formatter.style());
    }

    @Test
    void alreadyFormattedSourceComesBackUnchanged() {
        FormatResult result = FormatJ.defaultFormatter().format(FormatRequest.of(SOURCE).withName("A.java"));

        assertEquals(SOURCE, result.text());
        assertTrue(result.isUnchanged());
        assertFalse(result.hasErrors());
    }

    @Test
    void badlyLaidOutSourceIsReformatted() {
        String messy = "package a;\nclass A{void run(){if(x){g();}else{h();}}}\n";

        FormatResult result = FormatJ.defaultFormatter().format(FormatRequest.of(messy).withName("A.java"));

        assertFalse(result.hasErrors());
        assertEquals(
                """
                package a;

                class A {
                
                    void run() {
                        if (x) { g(); } else { h(); }
                    }
                    
                }
                """,
                result.text());
    }

    @Test
    void aFileTheParserCannotUnderstandKeepsThatRegionVerbatim() {
        String broken = "class A {\n    void f() {\n        int x = = 1;\n    }\n}\n";

        FormatResult result = FormatJ.defaultFormatter().format(FormatRequest.of(broken).withName("A.java"));

        assertFalse(result.hasErrors(), () -> "diagnostics: " + result.diagnostics());
        assertTrue(result.text().contains("int x = = 1;"), result.text());
        assertTrue(
                result.diagnostics()
                        .stream()
                        .anyMatch(d -> d.severity() == Diagnostic.Severity.WARNING),
                "an unparsed region must be reported");
    }

    @Test
    void tokenEquivalenceIgnoresLayoutButNotProgramChanges() {
        assertTrue(TokenEquivalence.equivalent("int  x =  1;", "int x = 1;"));
        assertTrue(TokenEquivalence.equivalent("int x = 1; // note", "int x = 1;"));
        assertFalse(TokenEquivalence.equivalent("int x = 1;", "int x = 2;"));
        assertFalse(TokenEquivalence.equivalent("int x = 1;", "int x = 1"));
        assertEquals(null, TokenEquivalence.firstDifference("class A {}", "class  A  {\n}\n"));
        assertTrue(TokenEquivalence.firstDifference("int x;", "int y;").contains("changed from 'x'"));
        ParseResult noSemi = JavaParser.parse("enum Color { RED, GREEN, BLUE }\n", LanguageLevel.LATEST, false);
        ParseResult withSemi = JavaParser.parse("enum Color { RED, GREEN, BLUE; }\n", LanguageLevel.LATEST, false);
        assertEquals(null, TokenEquivalence.firstDifference(noSemi.root().green(), withSemi.root().green()));
    }

    @Test
    void diagnosticsRenderWithTheFileName() {
        Diagnostic diagnostic = Diagnostic.error("broken", 3, 7);
        assertEquals("A.java:3:7: error: broken", diagnostic.format("A.java"));
    }

    @Test
    void aStyleIsCarriedThroughUnchanged() {
        Style style = Style.builder().indent(indent -> indent.size(2)).build();
        assertEquals(style, FormatJ.newFormatter().style(style).build().style());
    }

    @Test
    void enumTerminatorStaysOnTheLastConstant() {
        FormatResult result = format("""
                public enum LanguageLevel {
                    JAVA_17(17),
                    JAVA_25(25)
                    ;
                    public static final LanguageLevel LATEST = JAVA_25;
                    LanguageLevel(int release) {}
                }
                """);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("JAVA_25(25);"), result.text());
        assertFalse(result.text().contains("25)\n    ;"), result.text());
    }

    @Test
    void aNoArgumentEnumOmitsTheOptionalSemicolonByDefault() {
        FormatResult without = format("enum Color { RED, GREEN, BLUE }\n");
        FormatResult with = format("enum Color { RED, GREEN, BLUE; }\n");

        assertFalse(without.hasErrors(), () -> without.diagnostics().toString());
        assertFalse(with.hasErrors(), () -> with.diagnostics().toString());
        assertEquals(without.text(), with.text());
        assertFalse(without.text().contains(";"), without.text());
    }

    @Test
    void requiringTheOptionalSemicolonInsertsItOnNoArgumentEnums() {
        Style style = Style.builder().wrapping(wrapping -> wrapping.requireEnumConstantSemicolon(true)).build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();

        FormatResult result = formatter.format(FormatRequest.of("enum Color { RED, GREEN, BLUE }\n"));

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("BLUE;"), result.text());
    }

    @Test
    void parameterizedEnumsAlwaysKeepTheSemicolon() {
        FormatResult result = format("""
                enum Named { A(1), B(2); Named(int n) { } }
                """);

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("B(2);"), result.text());
    }

    @Test
    void parameterizedConstantsGetASemicolonEvenWhenTheOptionIsOff() {
        FormatResult result = format("enum Named { A(1), B(2) }\n");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains("B(2);"), result.text());
    }

    @Test
    void aCommentOnTheOptionalSemicolonKeepsIt() {
        FormatResult result = format("enum Color { RED, GREEN, BLUE; // keep\n}\n");

        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        assertTrue(result.text().contains(";"), result.text());
        assertTrue(result.text().contains("keep"), result.text());
    }

    @Test
    void requiringTheOptionalSemicolonIsAFixedPoint() {
        Style style = Style.builder().wrapping(wrapping -> wrapping.requireEnumConstantSemicolon(true)).build();
        Formatter formatter = FormatJ.newFormatter().style(style).build();
        String once = formatter.format(FormatRequest.of("enum Color { RED, GREEN, BLUE }\n")).text();
        String twice = formatter.format(FormatRequest.of(once)).text();
        assertEquals(once, twice);
        assertEquals(true, style.get(WrappingRules.REQUIRE_ENUM_CONSTANT_SEMICOLON));
    }

    private static FormatResult format(String source) {
        return FormatJ.defaultFormatter().format(FormatRequest.of(source).withName("E.java"));
    }

}
