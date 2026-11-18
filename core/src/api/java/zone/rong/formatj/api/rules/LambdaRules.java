package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** How lambda parameters are written. */
public final class LambdaRules {

    public static final Option<LambdaParameterStyle> PARAMETER_STYLE = Option.ofEnum(
        "lambdas.parameter-style",
        LambdaParameterStyle.OMIT_WHEN_POSSIBLE,
        "Parentheses around a single untyped parameter"
    );

    private LambdaRules() { }

}
