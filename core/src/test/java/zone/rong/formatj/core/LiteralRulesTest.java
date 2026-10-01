package zone.rong.formatj.core;

import org.junit.jupiter.api.Test;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.rules.HexDigitCase;
import zone.rong.formatj.api.rules.LongSuffix;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static String hex(String source, HexDigitCase digits) {
        return format(source, Style.builder().literals(literals -> literals.hexDigits(digits)).build());
    }

    @Test
    void hexDigitsAreRecased() {
        assertEquals(field("0xCAFE_BABEl"), hex(field("0xcafe_BABEl"), HexDigitCase.UPPER));
        assertEquals(field("0xcafe_babeL"), hex(field("0xCAFE_BABEL"), HexDigitCase.LOWER));
    }

    @Test
    void hexDigitsAndLongSuffixCombine() {
        Style style = Style.builder()
            .literals(literals -> literals.hexDigits(HexDigitCase.UPPER).longSuffix(LongSuffix.UPPER))
            .build();
        assertEquals(field("0xCAFEL"), format(field("0xcafel"), style));
    }

    @Test
    void hexDigitsKeepPrefixSuffixAndExponent() {
        assertEquals(field("0xABCDl"), hex(field("0xabcdl"), HexDigitCase.UPPER));
        assertEquals(field("0Xabcdl"), hex(field("0XABCDl"), HexDigitCase.LOWER));
        String floats = "class T {\n\n    double d = 0xA.bCp-3f + 0x.Fp1D;\n\n}\n";
        assertEquals(floats.replace("0xA.bC", "0xA.BC"), hex(floats, HexDigitCase.UPPER));
        assertEquals(floats.replace("0xA.bC", "0xa.bc").replace("0x.F", "0x.f"), hex(floats, HexDigitCase.LOWER));
    }

    @Test
    void hexDigitsLeaveOtherRadixesAlone() {
        String source = "class T {\n\n    long a = 0b1010 + 017 + 12e3 + 1_0D + 0xFFFF_FFFF;\n\n}\n";
        assertEquals(source.replace("0xFFFF_FFFF", "0xffff_ffff"), hex(source, HexDigitCase.LOWER));
        assertEquals(source, hex(source, HexDigitCase.UPPER));
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
