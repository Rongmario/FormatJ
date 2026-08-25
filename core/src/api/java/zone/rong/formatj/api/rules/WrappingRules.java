package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Line length and how each kind of list or expression is broken across lines. */
public final class WrappingRules {

    public static final Option<Integer> MAX_LINE_LENGTH =
            Option.ofInt("wrapping.max-line-length", 120, "Maximum columns before a line is wrapped");

    public static final Option<WrapPolicy> METHOD_PARAMETERS =
            Option.ofEnum(
                    "wrapping.method-parameters",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of a method declaration's parameter list");

    public static final Option<WrapPolicy> METHOD_ARGUMENTS =
            Option.ofEnum(
                    "wrapping.method-arguments",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of an argument list at a call site");

    public static final Option<ChainPolicy> CHAINED_CALLS =
            Option.ofEnum(
                    "wrapping.chained-calls",
                    ChainPolicy.BREAK_ALL_IF_MULTILINE,
                    "Wrapping of a chain of method calls");

    public static final Option<Integer> CHAIN_THRESHOLD =
            Option.ofInt("wrapping.chain-threshold", 3, "Chain links required before the chain may be broken at all");

    public static final Option<WrapPolicy> BINARY_OPERATORS =
            Option.ofEnum("wrapping.binary-operators", WrapPolicy.WRAP_IF_LONG, "Wrapping of a binary expression");

    public static final Option<OperatorWrap> OPERATOR_POSITION =
            Option.ofEnum(
                    "wrapping.operator-position",
                    OperatorWrap.BEFORE_OPERATOR,
                    "Which line a binary operator lands on when wrapped");

    public static final Option<WrapPolicy> TERNARY =
            Option.ofEnum("wrapping.ternary", WrapPolicy.WRAP_IF_LONG, "Wrapping of a conditional expression");

    public static final Option<WrapPolicy> ASSIGNMENT =
            Option.ofEnum(
                    "wrapping.assignment",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of the right hand side of an assignment");

    public static final Option<WrapPolicy> ARRAY_INITIALIZERS =
            Option.ofEnum("wrapping.array-initializers", WrapPolicy.WRAP_IF_LONG, "Wrapping of an array initializer");

    public static final Option<WrapPolicy> EXTENDS_IMPLEMENTS =
            Option.ofEnum(
                    "wrapping.extends-implements",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of extends and implements clauses");

    public static final Option<WrapPolicy> THROWS_CLAUSE =
            Option.ofEnum("wrapping.throws-clause", WrapPolicy.WRAP_IF_LONG, "Wrapping of a throws clause");

    public static final Option<WrapPolicy> TYPE_PARAMETERS =
            Option.ofEnum(
                    "wrapping.type-parameters",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of a type parameter or type argument list");

    public static final Option<WrapPolicy> ANNOTATION_ARGUMENTS =
            Option.ofEnum(
                    "wrapping.annotation-arguments",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of an annotation's element list");

    public static final Option<WrapPolicy> ENUM_CONSTANTS =
            Option.ofEnum(
                    "wrapping.enum-constants",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of the constant list of an enum");

    public static final Option<WrapPolicy> RECORD_COMPONENTS =
            Option.ofEnum(
                    "wrapping.record-components",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of a record header's component list");

    public static final Option<WrapPolicy> FOR_STATEMENT =
            Option.ofEnum(
                    "wrapping.for-statement",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of the header of a basic for statement");

    public static final Option<WrapPolicy> TRY_RESOURCES =
            Option.ofEnum(
                    "wrapping.try-resources",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of a try-with-resources resource list");

    public static final Option<Boolean> KEEP_SIMPLE_METHODS_ON_ONE_LINE =
            Option.ofBoolean(
                    "wrapping.keep-simple-methods-on-one-line",
                    false,
                    "Allow a whole short method to stay on one line");

    public static final Option<Boolean> KEEP_SIMPLE_LAMBDAS_ON_ONE_LINE =
            Option.ofBoolean(
                    "wrapping.keep-simple-lambdas-on-one-line",
                    true,
                    "Allow a short lambda body to stay on one line");

    public static final Option<Boolean> KEEP_SIMPLE_CLASSES_ON_ONE_LINE =
            Option.ofBoolean(
                    "wrapping.keep-simple-classes-on-one-line",
                    false,
                    "Allow a short class body to stay on one line");

    private WrappingRules() {}

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

        public Builder annotationArguments(WrapPolicy value) {
            style.set(ANNOTATION_ARGUMENTS, value);
            return this;
        }

        public Builder enumConstants(WrapPolicy value) {
            style.set(ENUM_CONSTANTS, value);
            return this;
        }

        public Builder recordComponents(WrapPolicy value) {
            style.set(RECORD_COMPONENTS, value);
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
