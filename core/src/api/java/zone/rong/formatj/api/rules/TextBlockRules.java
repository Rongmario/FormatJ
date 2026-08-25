package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Text block indentation and escapes. */
public final class TextBlockRules {

    public static final Option<TextBlockIndentPolicy> INDENT_POLICY =
            Option.ofEnum(
                    "text-blocks.indent-policy",
                    TextBlockIndentPolicy.PRESERVE,
                    "How incidental indentation is handled");

    public static final Option<Boolean> CLOSING_DELIMITER_ON_OWN_LINE =
            Option.ofBoolean(
                    "text-blocks.closing-delimiter-on-own-line",
                    true,
                    "Keep the closing delimiter on its own line");

    public static final Option<Boolean> ESCAPE_TRAILING_SPACES =
            Option.ofBoolean("text-blocks.escape-trailing-spaces", true, "Escape significant trailing spaces with \\s");

    private TextBlockRules() { }

    /** Fluent view of the {@code text-blocks.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder indentPolicy(TextBlockIndentPolicy value) {
            style.set(INDENT_POLICY, value);
            return this;
        }

        public Builder closingDelimiterOnOwnLine(boolean value) {
            style.set(CLOSING_DELIMITER_ON_OWN_LINE, value);
            return this;
        }

        public Builder escapeTrailingSpaces(boolean value) {
            style.set(ESCAPE_TRAILING_SPACES, value);
            return this;
        }

    }

}
