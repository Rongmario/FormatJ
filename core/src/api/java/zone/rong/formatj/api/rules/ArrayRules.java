package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Array declarations. */
public final class ArrayRules {

    public static final Option<BracketStyle> C_STYLE_BRACKETS = Option.ofEnum(
        "arrays.c-style-brackets",
        BracketStyle.PRESERVE,
        "Placement of array brackets on declared variables"
    );

    private ArrayRules() { }

    /** Fluent view of the {@code arrays.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder cStyleBrackets(BracketStyle value) {
            style.set(C_STYLE_BRACKETS, value);
            return this;
        }

    }

}
