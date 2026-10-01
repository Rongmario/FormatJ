package zone.rong.formatj.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.LiteralRules;
import zone.rong.formatj.api.rules.LongSuffix;
import org.junit.jupiter.api.Test;

class LiteralRulesTest {

    private static String format(String source, Style style) {
        return FormatJ.newFormatter().style(style).build().format(source);
    }

    private static String format(String source) {
        return format(source, Style.builder().literals(literals -> literals.longSuffix(LongSuffix.UPPER)).build());
    }

    private static String field(String initializer) {
        return "class T {\n\n    long x = " + initializer + ";\n\n}\n";
    }

    @Test
    void longSuffixIsUppercasedInEveryRadix() {
        assertEquals(field("10L"), format(field("10l")));
        assertEquals(field("0xFFL"), format(field("0xFFl")));
        assertEquals(field("0b1_01L"), format(field("0b1_01l")));
        assertEquals(field("017L"), format(field("017l")));
    }

    @Test
    void longSuffixLeavesOtherLiteralsAlone() {
        String source = "class T {\n\n    double d = 1.5e3f + 0x1p3d;\n    String s = \"10l\";\n    Object l = x10l;\n\n}\n";
        assertEquals(source, format(source));
    }

    @Test
    void longSuffixIsPreservedByDefault() {
        assertEquals(field("10l"), format(field("10l"), Style.builder().build()));
    }

    @Test
    void longSuffixSettles() {
        String once = format(field("10l"));
        assertEquals(once, format(once));
    }

}
