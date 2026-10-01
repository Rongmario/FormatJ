package zone.rong.formatj.core;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.core.config.StyleFiles;
import zone.rong.formatj.core.config.TomlWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleLayoutTest {

    @Test
    void emptyModulesInheritClassRulesUntilOverridden() {
        String source = "module sample { }\n";
        Style inherited = Style.builder()
            .braces(braces -> braces.classPlacement(BracePlacement.NEXT_LINE).emptyClassBody(EmptyBodyStyle.COMPACT))
            .build();
        assertEquals("module sample\n{}\n", format(source, inherited));

        Style overridden = inherited.toBuilder()
            .modules(module -> module.bracePlacement(BracePlacement.END_OF_LINE).emptyBody(EmptyBodyStyle.EXPANDED))
            .build();
        assertEquals("module sample {\n}\n", format(source, overridden));
    }

    @Test
    void moduleBodyBlankLinesCanOverrideClassRulesIndependently() {
        String source = "module sample { requires java.base; }\n";
        Style style = Style.builder()
            .blankLines(blank -> blank.afterClassOpeningBrace(2).beforeClassClosingBrace(2))
            .modules(module -> module.blankLinesAfterOpeningBrace(0).blankLinesBeforeClosingBrace(0))
            .build();

        assertEquals("module sample {\n    requires java.base;\n}\n", format(source, style));
    }

    @Test
    void openModulesAndEveryDirectiveTypeAreLaidOut() {
        String source = """
                open module sample {
                    requires transitive;
                    requires transitive java.logging;
                    requires static java.compiler;
                    exports sample.api to consumer.one, consumer.two;
                    opens sample.internal to consumer.tests;
                    uses sample.api.Service;
                    provides sample.api.Service with sample.internal.First, sample.internal.Second;
                }
                """;
        String formatted = format(
            source,
            style -> style.modules(module -> module.blankLinesAfterOpeningBrace(0).blankLinesBeforeClosingBrace(0))
        );

        assertTrue(formatted.startsWith("open module sample {\n"), formatted);
        assertTrue(formatted.contains("requires transitive;"), formatted);
        assertTrue(formatted.contains("requires transitive java.logging;"), formatted);
        assertTrue(formatted.contains("requires static java.compiler;"), formatted);
        assertTrue(formatted.contains("exports sample.api to consumer.one, consumer.two;"), formatted);
        assertTrue(formatted.contains("opens sample.internal to consumer.tests;"), formatted);
        assertTrue(formatted.contains("uses sample.api.Service;"), formatted);
        assertTrue(
            formatted.contains("provides sample.api.Service with sample.internal.First, sample.internal.Second;"),
            formatted
        );
    }

    @Test
    void directiveGroupSpacingPreservesOrderAndAttachedComments() {
        String source = """
                module sample {
                    uses sample.Service;
                    // Needed by the exported API.
                    requires java.logging;
                    requires java.compiler;
                    /* Kept with the export. */
                    exports sample.api;
                    requires java.sql;
                    provides sample.Service with sample.ServiceImpl;
                }
                """;
        String formatted = format(
            source,
            style -> style.modules(module -> module.blankLinesAfterOpeningBrace(0)
                .blankLinesBeforeClosingBrace(0)
                .blankLinesBetweenDirectiveGroups(1))
        );

        assertTrue(
            formatted.contains(
                "uses sample.Service;\n\n    // Needed by the exported API.\n    requires java.logging;"
            ),
            formatted
        );
        assertTrue(
            formatted.contains("requires java.compiler;\n\n    /* Kept with the export. */\n    exports sample.api;"),
            formatted
        );
        int uses = formatted.indexOf("uses sample.Service");
        int firstRequires = formatted.indexOf("requires java.logging");
        int exports = formatted.indexOf("exports sample.api");
        int secondRequires = formatted.indexOf("requires java.sql");
        int provides = formatted.indexOf("provides sample.Service");
        assertTrue(
            uses < firstRequires && firstRequires < exports && exports < secondRequires && secondRequires < provides,
            formatted
        );
    }

    @Test
    void targetAndImplementationListsWrapIndependently() {
        String source = """
                module sample {
                    exports sample.api to consumer.alpha, consumer.beta, consumer.gamma;
                    opens sample.internal to consumer.alpha, consumer.beta, consumer.gamma;
                    provides sample.Service with sample.impl.Alpha, sample.impl.Beta, sample.impl.Gamma;
                }
                """;
        String targets = format(
            source,
            style -> style.wrapping(wrapping -> wrapping.maxLineLength(45))
                .modules(module -> module.blankLinesAfterOpeningBrace(0)
                    .blankLinesBeforeClosingBrace(0)
                    .exportsOpensTargetListWrapping(WrapPolicy.CHOP_DOWN_ALWAYS)
                    .providesImplementationListWrapping(WrapPolicy.NEVER))
        );

        assertTrue(
            targets.contains("exports sample.api to consumer.alpha,\n        consumer.beta,\n        consumer.gamma;"),
            targets
        );
        assertTrue(
            targets.contains(
                "opens sample.internal to consumer.alpha,\n        consumer.beta,\n        consumer.gamma;"
            ),
            targets
        );
        assertTrue(
            targets.contains("provides sample.Service with sample.impl.Alpha, sample.impl.Beta, sample.impl.Gamma;"),
            targets
        );

        String implementations = format(
            source,
            style -> style.wrapping(wrapping -> wrapping.maxLineLength(45))
                .modules(module -> module.blankLinesAfterOpeningBrace(0)
                    .blankLinesBeforeClosingBrace(0)
                    .exportsOpensTargetListWrapping(WrapPolicy.NEVER)
                    .providesImplementationListWrapping(WrapPolicy.CHOP_DOWN_ALWAYS))
        );

        assertTrue(
            implementations.contains("exports sample.api to consumer.alpha, consumer.beta, consumer.gamma;"),
            implementations
        );
        assertTrue(
            implementations.contains(
                "provides sample.Service with sample.impl.Alpha,\n        sample.impl.Beta,\n        sample.impl.Gamma;"
            ),
            implementations
        );
    }

    @Test
    void listCommentsStayWithTheirTargetsAndImplementations() {
        String source = """
                module sample {
                    exports sample.api to consumer.alpha, // primary
                        consumer.beta;
                    provides sample.Service with sample.impl.Alpha, /* fallback */ sample.impl.Beta;
                }
                """;
        String formatted = format(
            source,
            style -> style.modules(module -> module.blankLinesAfterOpeningBrace(0)
                .blankLinesBeforeClosingBrace(0)
                .exportsOpensTargetListWrapping(WrapPolicy.PRESERVE)
                .providesImplementationListWrapping(WrapPolicy.CHOP_DOWN_ALWAYS))
        );

        assertTrue(formatted.indexOf("consumer.alpha") < formatted.indexOf("// primary"), formatted);
        assertTrue(formatted.indexOf("// primary") < formatted.indexOf("consumer.beta"), formatted);
        assertTrue(formatted.indexOf("sample.impl.Alpha") < formatted.indexOf("/* fallback */"), formatted);
        assertTrue(formatted.indexOf("/* fallback */") < formatted.indexOf("sample.impl.Beta"), formatted);
    }

    @Test
    void preserveKeepsABreakBeforeTheFirstTarget() {
        String source = """
                module sample {
                    exports sample.api to
                        consumer.alpha,
                        consumer.beta;
                }
                """;
        String formatted = format(
            source,
            style -> style.modules(module -> module.blankLinesAfterOpeningBrace(0)
                .blankLinesBeforeClosingBrace(0)
                .exportsOpensTargetListWrapping(WrapPolicy.PRESERVE))
        );

        assertTrue(formatted.contains("exports sample.api to\n        consumer.alpha,"), formatted);
    }

    @Test
    void everyListPolicyReachesAFixedPoint() {
        String source = "module sample { exports sample.api to one, two, three; " +
            "provides sample.Service with First, Second, Third; }\n";
        for (WrapPolicy policy : WrapPolicy.values()) {
            Style style = Style.builder()
                .modules(module -> module.blankLinesAfterOpeningBrace(0)
                    .blankLinesBeforeClosingBrace(0)
                    .exportsOpensTargetListWrapping(policy)
                    .providesImplementationListWrapping(policy))
                .build();
            String once = format(source, style);
            assertEquals(once, format(once, style), policy.name());
        }
    }

    @Test
    void dumpedInheritedModuleRulesReloadWithoutChangingOutput() {
        String source = "module sample { requires java.base; exports sample.api to one, two; }\n";
        Style style = Style.builder()
            .braces(braces -> braces.classPlacement(BracePlacement.NEXT_LINE))
            .blankLines(blank -> blank.afterClassOpeningBrace(0).beforeClassClosingBrace(0))
            .modules(module -> module.emptyBody(EmptyBodyStyle.COMPACT)
                .blankLinesAfterOpeningBrace(0)
                .blankLinesBetweenDirectiveGroups(1)
                .exportsOpensTargetListWrapping(WrapPolicy.CHOP_DOWN_ALWAYS))
            .build();

        String dumped = TomlWriter.write(style);
        assertTrue(dumped.contains("[module]"), dumped);
        assertTrue(dumped.contains("brace-placement = inherit"), dumped);
        assertTrue(dumped.contains("empty-body = compact"), dumped);
        assertTrue(dumped.contains("blank-lines-after-opening-brace = 0"), dumped);
        assertTrue(dumped.contains("blank-lines-before-closing-brace = inherit"), dumped);

        Style reloaded = StyleFiles.parse(dumped);
        assertEquals(style.resolvedValues(), reloaded.resolvedValues());
        assertEquals(format(source, style), format(source, reloaded));
    }

    private static String format(String source, Consumer<StyleBuilder> configure) {
        StyleBuilder builder = Style.builder();
        configure.accept(builder);
        return format(source, builder.build());
    }

    private static String format(String source, Style style) {
        Formatter formatter = FormatJ.newFormatter().style(style).build();
        FormatResult result = formatter.format(FormatRequest.of(source).withName("module-info.java"));
        assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
        return result.text();
    }

}
