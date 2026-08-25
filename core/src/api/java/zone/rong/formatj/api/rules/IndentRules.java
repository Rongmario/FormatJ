package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Indentation widths and which constructs earn an extra level. */
public final class IndentRules {

    public static final Option<Integer> SIZE =
            Option.ofInt("indent.size", 4, "Columns of indentation per nesting level");

    public static final Option<Boolean> USE_TABS =
            Option.ofBoolean("indent.use-tabs", false, "Indent with tab characters instead of spaces");

    public static final Option<Integer> CONTINUATION =
            Option.ofInt("indent.continuation", 8, "Columns added to a wrapped continuation line");

    public static final Option<Integer> CHAINED_CALL =
            Option.ofInt("indent.chained-call", 8, "Columns added to a wrapped method chain link");

    public static final Option<Integer> ARRAY_INITIALIZER =
            Option.ofInt("indent.array-initializer", 4, "Columns added inside a wrapped array initializer");

    public static final Option<Integer> TERNARY =
            Option.ofInt("indent.ternary", 8, "Columns added to a wrapped ternary branch");

    public static final Option<Integer> THROWS_CLAUSE =
            Option.ofInt("indent.throws-clause", 8, "Columns added to a wrapped throws clause");

    public static final Option<Boolean> SWITCH_CASE_LABELS =
            Option.ofBoolean("indent.switch-case-labels", true, "Indent case labels one level inside the switch block");

    public static final Option<Boolean> SWITCH_CASE_BODY =
            Option.ofBoolean("indent.switch-case-body", true, "Indent a colon-label case body past its label");

    public static final Option<Boolean> BLANK_LINES =
            Option.ofBoolean("indent.blank-lines", false, "Emit indentation whitespace on otherwise blank lines");

    private IndentRules() { }

    /** Fluent view of the {@code indent.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder size(int value) {
            style.set(SIZE, value);
            return this;
        }

        public Builder useTabs(boolean value) {
            style.set(USE_TABS, value);
            return this;
        }

        public Builder continuation(int value) {
            style.set(CONTINUATION, value);
            return this;
        }

        public Builder chainedCall(int value) {
            style.set(CHAINED_CALL, value);
            return this;
        }

        public Builder arrayInitializer(int value) {
            style.set(ARRAY_INITIALIZER, value);
            return this;
        }

        public Builder ternary(int value) {
            style.set(TERNARY, value);
            return this;
        }

        public Builder throwsClause(int value) {
            style.set(THROWS_CLAUSE, value);
            return this;
        }

        public Builder switchCaseLabels(boolean value) {
            style.set(SWITCH_CASE_LABELS, value);
            return this;
        }

        public Builder switchCaseBody(boolean value) {
            style.set(SWITCH_CASE_BODY, value);
            return this;
        }

        public Builder blankLines(boolean value) {
            style.set(BLANK_LINES, value);
            return this;
        }

    }

}
