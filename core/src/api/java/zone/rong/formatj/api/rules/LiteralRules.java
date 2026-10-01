package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** How numeric literals are spelled. The rules change a token's text, never its value. */
public final class LiteralRules {

    public static final Option<LongSuffix> LONG_SUFFIX = Option.ofEnum(
        "literals.long-suffix",
        LongSuffix.PRESERVE,
        "Case of the suffix on long literals"
    );

    public static final Option<HexDigitCase> HEX_DIGITS = Option.ofEnum(
        "literals.hex-digits",
        HexDigitCase.PRESERVE,
        "Case of the digits a to f in hexadecimal literals"
    );

    private LiteralRules() { }

    /** Fluent view of the {@code literals.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder longSuffix(LongSuffix value) {
            style.set(LONG_SUFFIX, value);
            return this;
        }

        public Builder hexDigits(HexDigitCase value) {
            style.set(HEX_DIGITS, value);
            return this;
        }

    }

}
