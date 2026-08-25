package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import org.junit.jupiter.api.Test;

/** The builder's rewrite gate, used by whitespace-only IDE requests. */
class FormatterRewritesTest {

    private static final String SOURCE = """
            class T {

                void run(int n) {
                    if (n > 0) log(n);
                }

            }
            """;

    @Test
    void rewritesOnAddsBracesWhenTheStyleAsks() {
        String formatted = FormatJ.newFormatter().style(alwaysBraces()).build().format(SOURCE);
        assertTrue(formatted.contains("if (n > 0) {"), formatted);
    }

    @Test
    void rewritesOffLeavesTheProgramAlone() {
        String formatted = FormatJ.newFormatter().style(alwaysBraces()).rewrites(false).build().format(SOURCE);
        assertFalse(formatted.contains("if (n > 0) {"), formatted);
        assertTrue(formatted.contains("log(n);"), formatted);
    }

    private static Style alwaysBraces() {
        return Style.builder().set(BraceRules.IF_ELSE, BracePolicy.ALWAYS).build();
    }

}
