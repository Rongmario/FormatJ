package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

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

    public static final Option<WrapPolicy> TERNARY = Option.ofEnum(
        "wrapping.ternary",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a conditional expression"
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

    public static final Option<WrapPolicy> THROWS_CLAUSE = Option.ofEnum(
        "wrapping.throws-clause",
        WrapPolicy.WRAP_IF_LONG,
        "Wrapping of a throws clause"
    );

    public static final Option<WrapPolicy> TYPE_PARAMETERS = Option.ofEnum(
        "wrapping.type-parameters",
        WrapPolicy.NEVER,
        "Wrapping of a type parameter or type argument list"
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

    public static final Option<WrapPolicy> ENUM_CONSTANTS = Option.ofEnum(
        "wrapping.enum-constants",
        WrapPolicy.CHOP_DOWN_ALWAYS,
        "Wrapping of the constant list of an enum"
    );

    /**
     * Whether a no-argument enum must end its constant list with {@code ;}.
     *
     * <p>The language already requires the semicolon when any constant has arguments, or when
     * fields, methods or nested types follow. This option only governs the optional case: a
     * no-argument constant list with nothing after it.
     */
    public static final Option<Boolean> REQUIRE_ENUM_CONSTANT_SEMICOLON = Option.ofBoolean(
        "wrapping.require-enum-constant-semicolon",
        false,
        "Always write a semicolon after the last no-argument enum constant"
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

    public static final Option<Boolean> KEEP_SIMPLE_METHODS_ON_ONE_LINE = Option.ofBoolean(
        "wrapping.keep-simple-methods-on-one-line",
        false,
        "Allow a whole short method to stay on one line"
    );

    public static final Option<Boolean> KEEP_SIMPLE_LAMBDAS_ON_ONE_LINE = Option.ofBoolean(
        "wrapping.keep-simple-lambdas-on-one-line",
        false,
        "Allow a short lambda body to stay on one line"
    );

    public static final Option<Boolean> KEEP_SIMPLE_CLASSES_ON_ONE_LINE = Option.ofBoolean(
        "wrapping.keep-simple-classes-on-one-line",
        false,
        "Allow a short class body to stay on one line"
    );

    private WrappingRules() { }

    /** Fluent view of the {@code wrapping.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder maxLineLength(int value) {
            style.set(MAX_LINE_LENGTH, value);
            return this;
        }

        public Builder methodParameters(WrapPolicy value) {
            style.set(METHOD_PARAMETERS, value);
            return this;
        }

        public Builder methodArguments(WrapPolicy value) {
            style.set(METHOD_ARGUMENTS, value);
            return this;
        }

        public Builder chainedCalls(ChainPolicy value) {
            style.set(CHAINED_CALLS, value);
            return this;
        }

        public Builder chainThreshold(int value) {
            style.set(CHAIN_THRESHOLD, value);
            return this;
        }

        public Builder binaryOperators(WrapPolicy value) {
            style.set(BINARY_OPERATORS, value);
            return this;
        }

        public Builder operatorPosition(OperatorWrap value) {
            style.set(OPERATOR_POSITION, value);
            return this;
        }

        public Builder methodReference(WrapPolicy value) {
            style.set(METHOD_REFERENCE, value);
            return this;
        }

        public Builder methodReferenceOperatorPosition(OperatorWrap value) {
            style.set(METHOD_REFERENCE_OPERATOR_POSITION, value);
            return this;
        }

        public Builder instanceofExpression(WrapPolicy value) {
            style.set(INSTANCEOF, value);
            return this;
        }

        public Builder instanceofOperatorPosition(OperatorWrap value) {
            style.set(INSTANCEOF_OPERATOR_POSITION, value);
            return this;
        }

        public Builder multicatch(WrapPolicy value) {
            style.set(MULTICATCH, value);
            return this;
        }

        public Builder multicatchSeparatorPosition(OperatorWrap value) {
            style.set(MULTICATCH_SEPARATOR_POSITION, value);
            return this;
        }

        public Builder intersectionTypes(WrapPolicy value) {
            style.set(INTERSECTION_TYPES, value);
            return this;
        }

        public Builder intersectionSeparatorPosition(OperatorWrap value) {
            style.set(INTERSECTION_SEPARATOR_POSITION, value);
            return this;
        }

        public Builder ternary(WrapPolicy value) {
            style.set(TERNARY, value);
            return this;
        }

        public Builder assignment(WrapPolicy value) {
            style.set(ASSIGNMENT, value);
            return this;
        }

        public Builder arrayInitializers(WrapPolicy value) {
            style.set(ARRAY_INITIALIZERS, value);
            return this;
        }

        public Builder extendsImplements(WrapPolicy value) {
            style.set(EXTENDS_IMPLEMENTS, value);
            return this;
        }

        public Builder throwsClause(WrapPolicy value) {
            style.set(THROWS_CLAUSE, value);
            return this;
        }

        public Builder typeParameters(WrapPolicy value) {
            style.set(TYPE_PARAMETERS, value);
            return this;
        }

        public Builder closingDelimiter(ClosingDelimiter value) {
            style.set(CLOSING_DELIMITER, value);
            return this;
        }

        public Builder assignmentBreak(AssignmentBreak value) {
            style.set(ASSIGNMENT_BREAK, value);
            return this;
        }

        public Builder hugSoleArgument(boolean value) {
            style.set(HUG_SOLE_ARGUMENT, value);
            return this;
        }

        public Builder annotationArguments(WrapPolicy value) {
            style.set(ANNOTATION_ARGUMENTS, value);
            return this;
        }

        public Builder enumConstants(WrapPolicy value) {
            style.set(ENUM_CONSTANTS, value);
            return this;
        }

        public Builder requireEnumConstantSemicolon(boolean value) {
            style.set(REQUIRE_ENUM_CONSTANT_SEMICOLON, value);
            return this;
        }

        public Builder forStatement(WrapPolicy value) {
            style.set(FOR_STATEMENT, value);
            return this;
        }

        public Builder tryResources(WrapPolicy value) {
            style.set(TRY_RESOURCES, value);
            return this;
        }

        public Builder keepSimpleMethodsOnOneLine(boolean value) {
            style.set(KEEP_SIMPLE_METHODS_ON_ONE_LINE, value);
            return this;
        }

        public Builder keepSimpleLambdasOnOneLine(boolean value) {
            style.set(KEEP_SIMPLE_LAMBDAS_ON_ONE_LINE, value);
            return this;
        }

        public Builder keepSimpleClassesOnOneLine(boolean value) {
            style.set(KEEP_SIMPLE_CLASSES_ON_ONE_LINE, value);
            return this;
        }

    }

}
