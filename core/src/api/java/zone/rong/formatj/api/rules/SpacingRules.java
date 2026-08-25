package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Single-space toggles around punctuation, keywords and operators. */
public final class SpacingRules {

    public static final Option<Boolean> BEFORE_METHOD_DECLARATION_PARENTHESIS =
            Option.ofBoolean(
                    "spacing.before-method-declaration-parenthesis",
                    false,
                    "Space between a method name and its parameter list");

    public static final Option<Boolean> BEFORE_METHOD_CALL_PARENTHESIS =
            Option.ofBoolean(
                    "spacing.before-method-call-parenthesis",
                    false,
                    "Space between a called name and its argument list");

    public static final Option<Boolean> BEFORE_IF_PARENTHESIS =
            Option.ofBoolean("spacing.before-if-parenthesis", true, "Space between if and its condition");

    public static final Option<Boolean> BEFORE_FOR_PARENTHESIS =
            Option.ofBoolean("spacing.before-for-parenthesis", true, "Space between for and its header");

    public static final Option<Boolean> BEFORE_WHILE_PARENTHESIS =
            Option.ofBoolean("spacing.before-while-parenthesis", true, "Space between while and its condition");

    public static final Option<Boolean> BEFORE_SWITCH_PARENTHESIS =
            Option.ofBoolean("spacing.before-switch-parenthesis", true, "Space between switch and its selector");

    public static final Option<Boolean> BEFORE_CATCH_PARENTHESIS =
            Option.ofBoolean("spacing.before-catch-parenthesis", true, "Space between catch and its parameter");

    public static final Option<Boolean> BEFORE_SYNCHRONIZED_PARENTHESIS =
            Option.ofBoolean(
                    "spacing.before-synchronized-parenthesis",
                    true,
                    "Space between synchronized and its monitor");

    public static final Option<Boolean> WITHIN_PARENTHESES =
            Option.ofBoolean("spacing.within-parentheses", false, "Spaces just inside parentheses");

    public static final Option<Boolean> WITHIN_BRACKETS =
            Option.ofBoolean("spacing.within-brackets", false, "Spaces just inside array brackets");

    public static final Option<Boolean> WITHIN_ARRAY_INITIALIZER_BRACES =
            Option.ofBoolean(
                    "spacing.within-array-initializer-braces",
                    false,
                    "Spaces just inside array initializer braces");

    public static final Option<Boolean> WITHIN_ANGLE_BRACKETS =
            Option.ofBoolean("spacing.within-angle-brackets", false, "Spaces just inside type argument angle brackets");

    public static final Option<Boolean> AROUND_ASSIGNMENT_OPERATORS =
            Option.ofBoolean(
                    "spacing.around-assignment-operators",
                    true,
                    "Spaces around = and compound assignment operators");

    public static final Option<Boolean> AROUND_BINARY_OPERATORS =
            Option.ofBoolean("spacing.around-binary-operators", true, "Spaces around binary operators");

    public static final Option<Boolean> AROUND_UNARY_OPERATORS =
            Option.ofBoolean(
                    "spacing.around-unary-operators",
                    false,
                    "Spaces between a unary operator and its operand");

    public static final Option<Boolean> AROUND_LAMBDA_ARROW =
            Option.ofBoolean("spacing.around-lambda-arrow", true, "Spaces around the lambda arrow");

    public static final Option<Boolean> AROUND_TERNARY_OPERATORS =
            Option.ofBoolean(
                    "spacing.around-ternary-operators",
                    true,
                    "Spaces around the ? and : of a conditional expression");

    public static final Option<Boolean> AFTER_COMMA =
            Option.ofBoolean("spacing.after-comma", true, "Space after a comma");

    public static final Option<Boolean> BEFORE_COMMA =
            Option.ofBoolean("spacing.before-comma", false, "Space before a comma");

    public static final Option<Boolean> AFTER_SEMICOLON_IN_FOR =
            Option.ofBoolean("spacing.after-semicolon-in-for", true, "Space after the semicolons of a for header");

    public static final Option<Boolean> BEFORE_SEMICOLON =
            Option.ofBoolean("spacing.before-semicolon", false, "Space before a statement-terminating semicolon");

    public static final Option<Boolean> AFTER_TYPE_CAST =
            Option.ofBoolean("spacing.after-type-cast", true, "Space between a cast and its operand");

    public static final Option<Boolean> BEFORE_COLON_IN_ENHANCED_FOR =
            Option.ofBoolean("spacing.before-colon-in-enhanced-for", true, "Space before the colon of an enhanced for");

    public static final Option<Boolean> AFTER_COLON_IN_ENHANCED_FOR =
            Option.ofBoolean("spacing.after-colon-in-enhanced-for", true, "Space after the colon of an enhanced for");

    public static final Option<Boolean> BEFORE_COLON_IN_CASE_LABEL =
            Option.ofBoolean("spacing.before-colon-in-case-label", false, "Space before the colon of a case label");

    public static final Option<Boolean> AROUND_CASE_ARROW =
            Option.ofBoolean("spacing.around-case-arrow", true, "Spaces around the arrow of a case label");

    public static final Option<Boolean> BEFORE_ANNOTATION_PARENTHESIS =
            Option.ofBoolean(
                    "spacing.before-annotation-parenthesis",
                    false,
                    "Space between an annotation name and its elements");

    public static final Option<Boolean> BEFORE_ARRAY_BRACKETS =
            Option.ofBoolean("spacing.before-array-brackets", false, "Space between a type and its array brackets");

    public static final Option<Boolean> AFTER_VARARGS_ELLIPSIS =
            Option.ofBoolean(
                    "spacing.after-varargs-ellipsis",
                    true,
                    "Space between a varargs ellipsis and the parameter name");

    private SpacingRules() { }

    /** Fluent view of the {@code spacing.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder beforeMethodDeclarationParenthesis(boolean value) {
            style.set(BEFORE_METHOD_DECLARATION_PARENTHESIS, value);
            return this;
        }

        public Builder beforeMethodCallParenthesis(boolean value) {
            style.set(BEFORE_METHOD_CALL_PARENTHESIS, value);
            return this;
        }

        public Builder beforeIfParenthesis(boolean value) {
            style.set(BEFORE_IF_PARENTHESIS, value);
            return this;
        }

        public Builder beforeForParenthesis(boolean value) {
            style.set(BEFORE_FOR_PARENTHESIS, value);
            return this;
        }

        public Builder beforeWhileParenthesis(boolean value) {
            style.set(BEFORE_WHILE_PARENTHESIS, value);
            return this;
        }

        public Builder beforeSwitchParenthesis(boolean value) {
            style.set(BEFORE_SWITCH_PARENTHESIS, value);
            return this;
        }

        public Builder beforeCatchParenthesis(boolean value) {
            style.set(BEFORE_CATCH_PARENTHESIS, value);
            return this;
        }

        public Builder beforeSynchronizedParenthesis(boolean value) {
            style.set(BEFORE_SYNCHRONIZED_PARENTHESIS, value);
            return this;
        }

        public Builder withinParentheses(boolean value) {
            style.set(WITHIN_PARENTHESES, value);
            return this;
        }

        public Builder withinBrackets(boolean value) {
            style.set(WITHIN_BRACKETS, value);
            return this;
        }

        public Builder withinArrayInitializerBraces(boolean value) {
            style.set(WITHIN_ARRAY_INITIALIZER_BRACES, value);
            return this;
        }

        public Builder withinAngleBrackets(boolean value) {
            style.set(WITHIN_ANGLE_BRACKETS, value);
            return this;
        }

        public Builder aroundAssignmentOperators(boolean value) {
            style.set(AROUND_ASSIGNMENT_OPERATORS, value);
            return this;
        }

        public Builder aroundBinaryOperators(boolean value) {
            style.set(AROUND_BINARY_OPERATORS, value);
            return this;
        }

        public Builder aroundUnaryOperators(boolean value) {
            style.set(AROUND_UNARY_OPERATORS, value);
            return this;
        }

        public Builder aroundLambdaArrow(boolean value) {
            style.set(AROUND_LAMBDA_ARROW, value);
            return this;
        }

        public Builder aroundTernaryOperators(boolean value) {
            style.set(AROUND_TERNARY_OPERATORS, value);
            return this;
        }

        public Builder afterComma(boolean value) {
            style.set(AFTER_COMMA, value);
            return this;
        }

        public Builder beforeComma(boolean value) {
            style.set(BEFORE_COMMA, value);
            return this;
        }

        public Builder afterSemicolonInFor(boolean value) {
            style.set(AFTER_SEMICOLON_IN_FOR, value);
            return this;
        }

        public Builder beforeSemicolon(boolean value) {
            style.set(BEFORE_SEMICOLON, value);
            return this;
        }

        public Builder afterTypeCast(boolean value) {
            style.set(AFTER_TYPE_CAST, value);
            return this;
        }

        public Builder beforeColonInEnhancedFor(boolean value) {
            style.set(BEFORE_COLON_IN_ENHANCED_FOR, value);
            return this;
        }

        public Builder afterColonInEnhancedFor(boolean value) {
            style.set(AFTER_COLON_IN_ENHANCED_FOR, value);
            return this;
        }

        public Builder beforeColonInCaseLabel(boolean value) {
            style.set(BEFORE_COLON_IN_CASE_LABEL, value);
            return this;
        }

        public Builder aroundCaseArrow(boolean value) {
            style.set(AROUND_CASE_ARROW, value);
            return this;
        }

        public Builder beforeAnnotationParenthesis(boolean value) {
            style.set(BEFORE_ANNOTATION_PARENTHESIS, value);
            return this;
        }

        public Builder beforeArrayBrackets(boolean value) {
            style.set(BEFORE_ARRAY_BRACKETS, value);
            return this;
        }

        public Builder afterVarargsEllipsis(boolean value) {
            style.set(AFTER_VARARGS_ELLIPSIS, value);
            return this;
        }

    }

}
