package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Which form switch cases and their values are written in. */
public final class SwitchRules {

    public static final Option<SwitchCaseStyle> CASE_STYLE = Option.ofEnum(
        "switch.case-style",
        SwitchCaseStyle.ARROW,
        "Arrow or colon case labels"
    );

    public static final Option<YieldStyle> YIELD_STYLE = Option.ofEnum(
        "switch.yield-style",
        YieldStyle.EXPRESSION_WHEN_POSSIBLE,
        "How the value of an arrow case body is written"
    );

    private SwitchRules() { }

}
