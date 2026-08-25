package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/**
 * Text block indentation and escapes.
 *
 * <p>Two of the three rules here change the string the program produces, so both are off by default.
 * Re-indenting a text block does not, because the language throws away the indentation every line
 * shares; moving the closing delimiter and escaping trailing spaces both do, and a formatter that
 * altered a string constant without being asked would be a formatter nobody could run over a
 * codebase they had not read.
 */
public final class TextBlockRules {

    public static final Option<TextBlockIndentPolicy> INDENT_POLICY =
            Option.ofEnum(
                    "text-blocks.indent-policy",
                    TextBlockIndentPolicy.PRESERVE,
                    "How incidental indentation is handled");

    public static final Option<Boolean> CLOSING_DELIMITER_ON_OWN_LINE =
            Option.ofBoolean(
                    "text-blocks.closing-delimiter-on-own-line",
                    false,
                    "Put the closing delimiter on its own line, adding the line terminator that implies");

    public static final Option<Boolean> ESCAPE_TRAILING_SPACES =
            Option.ofBoolean(
                    "text-blocks.escape-trailing-spaces",
                    false,
                    "Escape trailing spaces with \\s, making the ones the language would discard significant");

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
