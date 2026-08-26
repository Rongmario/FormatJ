package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Switch statements and expressions, including patterns and guards. */
public final class SwitchRules {

    public static final Option<SwitchCaseStyle> CASE_STYLE =
            Option.ofEnum("switch.case-style", SwitchCaseStyle.PRESERVE, "Arrow or colon case labels");

    public static final Option<BracePolicy> ARROW_CASE_BRACES =
            Option.ofEnum("switch.arrow-case-braces", BracePolicy.PRESERVE, "Braces around the body of an arrow case");

    public static final Option<YieldStyle> YIELD_STYLE =
            Option.ofEnum("switch.yield-style", YieldStyle.PRESERVE, "How the value of an arrow case body is written");

    public static final Option<WrapPolicy> MULTI_LABEL_WRAPPING =
            Option.ofEnum(
                    "switch.multi-label-wrapping",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of a case label listing several constants");

    public static final Option<Boolean> NULL_DEFAULT_ON_ONE_LINE =
            Option.ofBoolean("switch.null-default-on-one-line", true, "Keep case null, default on a single line");

    public static final Option<Boolean> GUARD_ON_SAME_LINE =
            Option.ofBoolean("switch.guard-on-same-line", true, "Keep a when guard on the line of its pattern");

    public static final Option<Boolean> ARROW_BODY_ON_NEW_LINE_WHEN_LONG =
            Option.ofBoolean(
                    "switch.arrow-body-on-new-line-when-long",
                    true,
                    "Move a long arrow case body to the next line");

    private SwitchRules() { }

    /** Fluent view of the {@code switch.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder caseStyle(SwitchCaseStyle value) {
            style.set(CASE_STYLE, value);
            return this;
        }

        public Builder arrowCaseBraces(BracePolicy value) {
            style.set(ARROW_CASE_BRACES, value);
            return this;
        }

        public Builder yieldStyle(YieldStyle value) {
            style.set(YIELD_STYLE, value);
            return this;
        }

        public Builder multiLabelWrapping(WrapPolicy value) {
            style.set(MULTI_LABEL_WRAPPING, value);
            return this;
        }

        public Builder nullDefaultOnOneLine(boolean value) {
            style.set(NULL_DEFAULT_ON_ONE_LINE, value);
            return this;
        }

        public Builder guardOnSameLine(boolean value) {
            style.set(GUARD_ON_SAME_LINE, value);
            return this;
        }

        public Builder arrowBodyOnNewLineWhenLong(boolean value) {
            style.set(ARROW_BODY_ON_NEW_LINE_WHEN_LONG, value);
            return this;
        }

    }

}
