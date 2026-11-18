package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** How numeric literals are spelled. The rules change a token's text, never its value. */
public final class LiteralRules {

    public static final Option<LongSuffix> LONG_SUFFIX = Option.ofEnum(
        "literals.long-suffix",
        LongSuffix.UPPER,
        "Case of the suffix on long literals"
    );

    public static final Option<HexDigitCase> HEX_DIGITS = Option.ofEnum(
        "literals.hex-digits",
        HexDigitCase.UPPER,
        "Case of the digits a to f in hexadecimal literals"
    );

    public static final Option<FloatSuffix> FLOAT_SUFFIX = Option.ofEnum(
        "literals.float-suffix",
        FloatSuffix.PRESERVE,
        "Case of the suffix on float and double literals"
    );

    /**
     * Digits a plain decimal integer needs before underscores group it in threes.
     *
     * <p>Only literals written without underscores are grouped, so a hand-grouped literal such as a
     * card number or a bit mask keeps its own grouping.
     */
    public static final Option<Integer> DIGIT_GROUPING_THRESHOLD = Option.ofInt(
        "literals.digit-grouping-threshold",
        0,
        "Digits a decimal integer literal needs before underscores group it in threes; 0 disables"
    );

    private LiteralRules() { }

}
