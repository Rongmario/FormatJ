package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Line length and how each kind of list or expression is broken across lines. */
public final class WrappingRules {

    public static final Option<Integer> MAX_LINE_LENGTH = Option.ofInt(
        "wrapping.max-line-length",
        120,
        1,
        "Maximum columns before a line is wrapped"
    );

    public static final Option<WrapPolicy> METHOD_PARAMETERS = Option.ofEnum(
        "wrapping.method-parameters",
        WrapPolicy.CHOP_DOWN_IF_LONG,
        "Wrapping of a method declaration's parameter list"
    );

    public static final Option<WrapPolicy> METHOD_ARGUMENTS = Option.ofEnum(
        "wrapping.method-arguments",
        WrapPolicy.CHOP_DOWN_IF_LONG,
        "Wrapping of an argument list at a call site"
    );

    public static final Option<WrapPolicy> RECORD_COMPONENTS = Option.ofEnum(
        "wrapping.record-components",
        WrapPolicy.CHOP_DOWN_IF_LONG,
        "Wrapping of a record header's components"
    );

    public static final Option<ChainPolicy> CHAINED_CALLS = Option.ofEnum(
        "wrapping.chained-calls",
        ChainPolicy.BREAK_ALL_WHEN_TOO_LONG,
        "Wrapping of a chain of method calls"
    );

    public static final Option<Integer> CHAIN_THRESHOLD = Option.ofInt(
        "wrapping.chain-threshold",
        2,
        "Chain links required before the chain may be broken at all"
    );

    public static final Option<WrapPolicy> BINARY_OPERATORS = Option.ofEnum(
        "wrapping.binary-operators",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a binary expression"
    );

    public static final Option<OperatorWrap> OPERATOR_POSITION = Option.ofEnum(
        "wrapping.operator-position",
        OperatorWrap.AFTER_OPERATOR,
        "Which line a binary operator lands on when wrapped"
    );

    public static final Option<WrapPolicy> METHOD_REFERENCE = Option.ofEnum(
        "wrapping.method-reference",
        WrapPolicy.NEVER,
        "Wrapping of a method reference at its :: operator"
    );

    public static final Option<OperatorWrap> METHOD_REFERENCE_OPERATOR_POSITION = Option.ofEnum(
        "wrapping.method-reference-operator-position",
        OperatorWrap.BEFORE_OPERATOR,
        "Which line :: lands on when a method reference wraps"
    );

    public static final Option<WrapPolicy> INSTANCEOF = Option.ofEnum(
        "wrapping.instanceof",
        WrapPolicy.PRESERVE,
        "Wrapping of an instanceof test"
    );

    public static final Option<OperatorWrap> INSTANCEOF_OPERATOR_POSITION = Option.ofEnum(
        "wrapping.instanceof-operator-position",
        OperatorWrap.AFTER_OPERATOR,
        "Which line instanceof lands on when its test wraps"
    );

    public static final Option<WrapPolicy> MULTICATCH = Option.ofEnum(
        "wrapping.multicatch",
        WrapPolicy.NEVER,
        "Wrapping of multi-catch alternatives"
    );

    public static final Option<OperatorWrap> MULTICATCH_SEPARATOR_POSITION = Option.ofEnum(
        "wrapping.multicatch-separator-position",
        OperatorWrap.BEFORE_OPERATOR,
        "Which line | lands on when multi-catch alternatives wrap"
    );

    public static final Option<WrapPolicy> INTERSECTION_TYPES = Option.ofEnum(
        "wrapping.intersection-types",
        WrapPolicy.NEVER,
        "Wrapping of intersection type bounds and casts"
    );

    public static final Option<OperatorWrap> INTERSECTION_SEPARATOR_POSITION = Option.ofEnum(
        "wrapping.intersection-separator-position",
        OperatorWrap.BEFORE_OPERATOR,
        "Which line & lands on when intersection types wrap"
    );

    public static final Option<WrapPolicy> DECONSTRUCTION_PATTERNS = Option.ofEnum(
        "wrapping.deconstruction-patterns",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a record deconstruction pattern"
    );

    public static final Option<WrapPolicy> TERNARY = Option.ofEnum(
        "wrapping.ternary",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a conditional expression"
    );

    public static final Option<Boolean> LAMBDA_EXPRESSION_ON_ARROW_LINE = Option.ofBoolean(
        "wrapping.lambda-expression-on-arrow-line",
        true,
        "Keep a single-expression body on the arrow's line"
    );

    public static final Option<WrapPolicy> ASSIGNMENT = Option.ofEnum(
        "wrapping.assignment",
        WrapPolicy.NEVER,
        "Wrapping of the right hand side of an assignment"
    );

    public static final Option<WrapPolicy> ARRAY_INITIALIZERS = Option.ofEnum(
        "wrapping.array-initializers",
        WrapPolicy.CHOP_DOWN_IF_LONG,
        "Wrapping of an array initializer"
    );

    public static final Option<WrapPolicy> EXTENDS_IMPLEMENTS = Option.ofEnum(
        "wrapping.extends-implements",
        WrapPolicy.NEVER,
        "Wrapping of extends and implements clauses"
    );

    public static final Option<WrapPolicy> PERMITS_CLAUSE = Option.ofEnum(
        "wrapping.permits-clause",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a permits clause"
    );

    public static final Option<Boolean> PERMITS_ON_NEW_LINE = Option.ofBoolean(
        "wrapping.permits-on-new-line",
        false,
        "Start the permits clause on its own line"
    );

    public static final Option<WrapPolicy> THROWS_CLAUSE = Option.ofEnum(
        "wrapping.throws-clause",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a throws clause"
    );

    public static final Option<WrapPolicy> ANNOTATION_ARGUMENTS = Option.ofEnum(
        "wrapping.annotation-arguments",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of an annotation's element list"
    );

    /**
     * Where the closing parenthesis of a wrapped list goes.
     *
     * <p>One rule for every parenthesised list, because a file that dangles the parenthesis of a
     * call and hugs the one of the declaration above it reads as two styles rather than one.
     */
    public static final Option<ClosingDelimiter> CLOSING_DELIMITER = Option.ofEnum(
        "wrapping.closing-delimiter",
        ClosingDelimiter.OWN_LINE,
        "Whether a wrapped list's closing parenthesis takes a line of its own"
    );

    public static final Option<AssignmentBreak> ASSIGNMENT_BREAK = Option.ofEnum(
        "wrapping.assignment-break",
        AssignmentBreak.INSIDE_VALUE,
        "Where a long assignment breaks first"
    );

    public static final Option<Boolean> HUG_SOLE_ARGUMENT = Option.ofBoolean(
        "wrapping.hug-sole-argument",
        true,
        "Keep a lone call or creation argument on the line of the parenthesis and break inside it"
    );

    /**
     * Whether a wrapped parenthesised list starts on the line after its opening parenthesis.
     *
     * <p>When false the first element stays on the line of the parenthesis. Where the closing one goes
     * is still {@link #CLOSING_DELIMITER}'s to say.
     */
    public static final Option<Boolean> BREAK_AFTER_OPEN_PAREN = Option.ofBoolean(
        "wrapping.break-after-open-paren",
        true,
        "Start a wrapped parenthesised list on the line after its opening parenthesis"
    );

    public static final Option<WrapPolicy> ENUM_CONSTANTS = Option.ofEnum(
        "wrapping.enum-constants",
        WrapPolicy.CHOP_DOWN_ALWAYS,
        "Wrapping of the constant list of an enum"
    );

    public static final Option<WrapPolicy> FOR_STATEMENT = Option.ofEnum(
        "wrapping.for-statement",
        WrapPolicy.CHOP_DOWN_IF_LONG,
        "Wrapping of the header of a basic for statement"
    );

    public static final Option<WrapPolicy> TRY_RESOURCES = Option.ofEnum(
        "wrapping.try-resources",
        WrapPolicy.NEVER,
        "Wrapping of a try-with-resources resource list"
    );

    public static final Option<WrapPolicy> CASE_LABELS = Option.ofEnum(
        "wrapping.case-labels",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a case label listing several constants"
    );

    public static final Option<Boolean> CASE_GUARD_ON_SAME_LINE = Option.ofBoolean(
        "wrapping.case-guard-on-same-line",
        true,
        "Keep a when guard on the line of its pattern"
    );

    public static final Option<Boolean> ARROW_CASE_BODY_ON_NEW_LINE_WHEN_LONG = Option.ofBoolean(
        "wrapping.arrow-case-body-on-new-line-when-long",
        true,
        "Move a long arrow case body to the next line"
    );

    public static final Option<RecordWithStyle> WITH_BLOCK = Option.ofEnum(
        "wrapping.with-block",
        RecordWithStyle.INLINE_WHEN_SHORT,
        "Layout of a derived record creation with-block"
    );

    /**
     * Whether a string literal that runs past the line length is split into a concatenation.
     *
     * <p>The literal splits after a space or a {@code \n} escape. The pieces denote
     * the same string and the concatenation is still a constant expression. Text blocks, literals with
     * Unicode escapes and literals in a position where {@code +} would bind differently stay whole.
     */
    public static final Option<Boolean> SPLIT_LONG_STRINGS = Option.ofBoolean(
        "wrapping.split-long-strings",
        true,
        "Split a string literal that runs past the line length into a concatenation"
    );

    public static final Option<WrapPolicy> EXPORTS_OPENS_TARGETS = Option.ofEnum(
        "wrapping.exports-opens-targets",
        WrapPolicy.NEVER,
        "Wrapping of target module lists in exports and opens directives"
    );

    public static final Option<WrapPolicy> PROVIDES_IMPLEMENTATIONS = Option.ofEnum(
        "wrapping.provides-implementations",
        WrapPolicy.NEVER,
        "Wrapping of implementation lists in provides directives"
    );

    private WrappingRules() { }

}
