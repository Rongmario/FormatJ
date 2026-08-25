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
                        if (x) {
                            g();
                        } else {
                            h();
                        }
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

}
