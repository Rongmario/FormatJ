package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** What of the author's layout survives formatting. */
public final class PreservationRules {

    public static final Option<Boolean> KEEP_LINE_BREAK_AFTER_OPEN_PAREN = Option.ofBoolean(
        "preservation.keep-line-break-after-open-paren",
        false,
        "Keep a break the author put after an opening parenthesis"
    );

    public static final Option<Boolean> KEEP_SIMPLE_BLOCKS_INLINE = Option.ofBoolean(
        "preservation.keep-simple-blocks-inline",
        false,
        "Keep a block the author wrote on one line on one line"
    );

    public static final Option<Boolean> KEEP_SIMPLE_METHODS_INLINE = Option.ofBoolean(
        "preservation.keep-simple-methods-inline",
        false,
        "Allow a whole short method to stay on one line"
    );

    public static final Option<Boolean> KEEP_SIMPLE_LAMBDAS_INLINE = Option.ofBoolean(
        "preservation.keep-simple-lambdas-inline",
        false,
        "Allow a short lambda body to stay on one line"
    );

    public static final Option<Boolean> KEEP_SIMPLE_CLASSES_INLINE = Option.ofBoolean(
        "preservation.keep-simple-classes-inline",
        false,
        "Allow a short class body to stay on one line"
    );

    public static final Option<Boolean> KEEP_CASE_NULL_DEFAULT_INLINE = Option.ofBoolean(
        "preservation.keep-case-null-default-inline",
        true,
        "Keep case null, default on a single line"
    );

    public static final Option<Boolean> KEEP_ARRAY_INITIALIZER_LAYOUT = Option.ofBoolean(
        "preservation.keep-array-initializer-layout",
        false,
        "Keep the row layout of a hand-arranged array initializer"
    );

    public static final Option<Boolean> RESPECT_EXISTING_CHAIN_BREAKS = Option.ofBoolean(
        "preservation.respect-existing-chain-breaks",
        false,
        "Keep breaks the author placed in a method chain"
    );

    public static final Option<Boolean> NEVER_JOIN_LINES = Option.ofBoolean(
        "preservation.never-join-lines",
        false,
        "Never merge two lines the author kept apart"
    );

    private PreservationRules() { }

}
