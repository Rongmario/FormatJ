package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Lambda parameter and body layout. */
public final class LambdaRules {

    public static final Option<LambdaParameterStyle> PARAMETER_STYLE =
            Option.ofEnum(
                    "lambdas.parameter-style",
                    LambdaParameterStyle.PRESERVE,
                    "Parentheses around a single untyped parameter");

    public static final Option<BracePolicy> BODY_BRACES =
            Option.ofEnum("lambdas.body-braces", BracePolicy.PRESERVE, "Braces around a lambda body");

    public static final Option<Boolean> KEEP_SINGLE_EXPRESSION_INLINE =
            Option.ofBoolean(
                    "lambdas.keep-single-expression-inline",
                    true,
                    "Keep a single-expression body on the arrow's line");

    private LambdaRules() {}

    /** Fluent view of the {@code lambdas.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder parameterStyle(LambdaParameterStyle value) {
            style.set(PARAMETER_STYLE, value);
            return this;
        }

        public Builder bodyBraces(BracePolicy value) {
            style.set(BODY_BRACES, value);
            return this;
        }

        public Builder keepSingleExpressionInline(boolean value) {
            style.set(KEEP_SINGLE_EXPRESSION_INLINE, value);
            return this;
        }

    }

}
