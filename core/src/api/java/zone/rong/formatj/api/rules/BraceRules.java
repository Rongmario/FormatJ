package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Brace placement, and which bodies are required to have braces at all. */
public final class BraceRules {

    public static final Option<BracePlacement> CLASS_PLACEMENT =
            Option.ofEnum(
                    "braces.class-placement",
                    BracePlacement.END_OF_LINE,
                    "Opening brace position for a type declaration");

    public static final Option<BracePlacement> METHOD_PLACEMENT =
            Option.ofEnum(
                    "braces.method-placement",
                    BracePlacement.END_OF_LINE,
                    "Opening brace position for a method or constructor");

    public static final Option<BracePlacement> CONTROL_PLACEMENT =
            Option.ofEnum(
                    "braces.control-placement",
                    BracePlacement.END_OF_LINE,
                    "Opening brace position for a control statement");

    public static final Option<BracePlacement> LAMBDA_PLACEMENT =
            Option.ofEnum(
                    "braces.lambda-placement",
                    BracePlacement.END_OF_LINE,
                    "Opening brace position for a lambda block body");

    public static final Option<BracePolicy> IF_ELSE =
            Option.ofEnum("braces.if-else", BracePolicy.PRESERVE, "Braces around if and else bodies");

    public static final Option<BracePolicy> FOR_LOOP =
            Option.ofEnum("braces.for-loop", BracePolicy.PRESERVE, "Braces around for and enhanced-for bodies");

    public static final Option<BracePolicy> WHILE_LOOP =
            Option.ofEnum("braces.while-loop", BracePolicy.PRESERVE, "Braces around while and do-while bodies");

    public static final Option<Boolean> ELSE_ON_NEW_LINE =
            Option.ofBoolean("braces.else-on-new-line", false, "Put else on the line after the closing brace");

    public static final Option<Boolean> CATCH_ON_NEW_LINE =
            Option.ofBoolean("braces.catch-on-new-line", false, "Put catch on the line after the closing brace");

    public static final Option<Boolean> FINALLY_ON_NEW_LINE =
            Option.ofBoolean("braces.finally-on-new-line", false, "Put finally on the line after the closing brace");

    public static final Option<EmptyBodyStyle> EMPTY_CLASS_BODY =
            Option.ofEnum("braces.empty-class-body", EmptyBodyStyle.SPACED, "Rendering of an empty type body");

    public static final Option<EmptyBodyStyle> EMPTY_METHOD_BODY =
            Option.ofEnum("braces.empty-method-body", EmptyBodyStyle.SPACED, "Rendering of an empty method body");

    public static final Option<EmptyBodyStyle> EMPTY_CONTROL_BODY =
            Option.ofEnum(
                    "braces.empty-control-body",
                    EmptyBodyStyle.SPACED,
                    "Rendering of an empty control statement body");

    private BraceRules() { }

    /** Fluent view of the {@code braces.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder classPlacement(BracePlacement value) {
            style.set(CLASS_PLACEMENT, value);
            return this;
        }

        public Builder methodPlacement(BracePlacement value) {
            style.set(METHOD_PLACEMENT, value);
            return this;
        }

        public Builder controlPlacement(BracePlacement value) {
            style.set(CONTROL_PLACEMENT, value);
            return this;
        }

        public Builder lambdaPlacement(BracePlacement value) {
            style.set(LAMBDA_PLACEMENT, value);
            return this;
        }

        public Builder ifElse(BracePolicy value) {
            style.set(IF_ELSE, value);
            return this;
        }

        public Builder forLoop(BracePolicy value) {
            style.set(FOR_LOOP, value);
            return this;
        }

        public Builder whileLoop(BracePolicy value) {
            style.set(WHILE_LOOP, value);
            return this;
        }

        public Builder elseOnNewLine(boolean value) {
            style.set(ELSE_ON_NEW_LINE, value);
            return this;
        }

        public Builder catchOnNewLine(boolean value) {
            style.set(CATCH_ON_NEW_LINE, value);
            return this;
        }

        public Builder finallyOnNewLine(boolean value) {
            style.set(FINALLY_ON_NEW_LINE, value);
            return this;
        }

        public Builder emptyClassBody(EmptyBodyStyle value) {
            style.set(EMPTY_CLASS_BODY, value);
            return this;
        }

        public Builder emptyMethodBody(EmptyBodyStyle value) {
            style.set(EMPTY_METHOD_BODY, value);
            return this;
        }

        public Builder emptyControlBody(EmptyBodyStyle value) {
            style.set(EMPTY_CONTROL_BODY, value);
            return this;
        }

    }

}
