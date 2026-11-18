package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Indentation widths and which constructs earn an extra level. */
public final class IndentRules {

    public static final Option<Integer> SIZE = Option.ofInt(
        "indent.size",
        4,
        "Columns of indentation per nesting level"
    );

    public static final Option<Boolean> USE_TABS = Option.ofBoolean(
        "indent.use-tabs",
        false,
        "Indent with tab characters instead of spaces"
    );

    public static final Option<Integer> CONTINUATION = Option.ofInt(
        "indent.continuation",
        4,
        "Columns added to a wrapped continuation line"
    );

    public static final Option<Integer> CHAINED_CALL = Option.ofInt(
        "indent.chained-call",
        CONTINUATION,
        "Columns added to a wrapped method chain link"
    );

    public static final Option<Integer> ARRAY_INITIALIZER = Option.ofInt(
        "indent.array-initializer",
        SIZE,
        "Columns added inside a wrapped array initializer"
    );

    public static final Option<Integer> TERNARY = Option.ofInt(
        "indent.ternary",
        CONTINUATION,
        "Columns added to a wrapped ternary branch"
    );

    public static final Option<Integer> THROWS_CLAUSE = Option.ofInt(
        "indent.throws-clause",
        CONTINUATION,
        "Columns added to a wrapped throws clause"
    );

    public static final Option<Integer> NESTED_PATTERN = Option.ofInt(
        "indent.nested-pattern",
        CONTINUATION,
        "Columns a wrapped nested pattern is indented"
    );

    public static final Option<Boolean> SWITCH_CASE_LABELS = Option.ofBoolean(
        "indent.switch-case-labels",
        true,
        "Indent case labels one level inside the switch block"
    );

    public static final Option<Boolean> SWITCH_CASE_BODY = Option.ofBoolean(
        "indent.switch-case-body",
        true,
        "Indent a colon-label case body past its label"
    );

    public static final Option<Boolean> BLANK_LINES = Option.ofBoolean(
        "indent.blank-lines",
        false,
        "Emit indentation whitespace on otherwise blank lines"
    );

    private IndentRules() { }

}
