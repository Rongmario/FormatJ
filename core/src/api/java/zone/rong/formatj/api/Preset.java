package zone.rong.formatj.api;

import java.util.List;
import java.util.Locale;

import zone.rong.formatj.api.rules.AlignmentPolicy;
import zone.rong.formatj.api.rules.AlignmentRules;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AnnotationRules;
import zone.rong.formatj.api.rules.AssignmentBreak;
import zone.rong.formatj.api.rules.BlankLineRules;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BraceRules;
import zone.rong.formatj.api.rules.BracketStyle;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.DeclarationRules;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.HexDigitCase;
import zone.rong.formatj.api.rules.ImportRules;
import zone.rong.formatj.api.rules.IndentRules;
import zone.rong.formatj.api.rules.JavadocRules;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.LambdaParameterStyle;
import zone.rong.formatj.api.rules.LambdaRules;
import zone.rong.formatj.api.rules.LiteralRules;
import zone.rong.formatj.api.rules.LongSuffix;
import zone.rong.formatj.api.rules.MemberOrder;
import zone.rong.formatj.api.rules.MemberRules;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.ModifierRules;
import zone.rong.formatj.api.rules.OperatorWrap;
import zone.rong.formatj.api.rules.PreservationRules;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.SpacingRules;
import zone.rong.formatj.api.rules.StaticImportPlacement;
import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.SwitchRules;
import zone.rong.formatj.api.rules.WrapPolicy;
import zone.rong.formatj.api.rules.WrappingRules;
import zone.rong.formatj.api.rules.YieldStyle;

/**
 * A named starting point for a style.
 *
 * <p>A preset is nothing but a pre-populated {@link StyleBuilder}, so any rule it sets can be
 * overridden afterwards.
 */
public enum Preset {

    /**
     * FormatJ's own style: four-space indent, 120 columns, and the author's own blank lines and
     * chain breaks preserved wherever they do not conflict with a rule.
     */
    FORMATJ {

        @Override
        void applyTo(StyleBuilder style) {
            // Every rule already defaults to this style; the preset exists so callers can name it.
        }

    },
    /**
     * Google Java Style, the best documented external style, which doubles as a conformance target
     * for the engine.
     */
    GOOGLE {

        @Override
        void applyTo(StyleBuilder style) {
            style.set(IndentRules.SIZE, 2)
                .set(IndentRules.CONTINUATION, 4)
                .set(IndentRules.NESTED_PATTERN, 8)
                .set(IndentRules.SWITCH_CASE_LABELS, true)
                .set(IndentRules.SWITCH_CASE_BODY, true);

            style.set(WrappingRules.MAX_LINE_LENGTH, 100)
                .set(WrappingRules.METHOD_PARAMETERS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.METHOD_ARGUMENTS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.CHAINED_CALLS, ChainPolicy.BREAK_ALL_IF_MULTILINE)
                .set(WrappingRules.CHAIN_THRESHOLD, 2)
                .set(WrappingRules.OPERATOR_POSITION, OperatorWrap.BEFORE_OPERATOR)
                .set(WrappingRules.ASSIGNMENT, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.ARRAY_INITIALIZERS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.EXTENDS_IMPLEMENTS, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.ENUM_CONSTANTS, WrapPolicy.CHOP_DOWN_IF_LONG)
                .set(WrappingRules.FOR_STATEMENT, WrapPolicy.WRAP_IF_LONG)
                .set(WrappingRules.TRY_RESOURCES, WrapPolicy.CHOP_DOWN_IF_LONG)
                .set(WrappingRules.CLOSING_DELIMITER, ClosingDelimiter.ATTACHED)
                .set(WrappingRules.ASSIGNMENT_BREAK, AssignmentBreak.AFTER_OPERATOR)
                .set(WrappingRules.HUG_SOLE_ARGUMENT, false);

            style.set(BraceRules.CLASS_PLACEMENT, BracePlacement.END_OF_LINE)
                .set(BraceRules.METHOD_PLACEMENT, BracePlacement.END_OF_LINE)
                .set(BraceRules.CONTROL_PLACEMENT, BracePlacement.END_OF_LINE)
                .set(BraceRules.IF_ELSE, BracePolicy.ALWAYS)
                .set(BraceRules.FOR_LOOP, BracePolicy.ALWAYS)
                .set(BraceRules.WHILE_LOOP, BracePolicy.ALWAYS)
                .set(BraceRules.LAMBDA_BODY, BracePolicy.PRESERVE)
                .set(BraceRules.ARROW_CASE_BODY, BracePolicy.PRESERVE)
                .set(BraceRules.ELSE_ON_NEW_LINE, false)
                .set(BraceRules.CATCH_ON_NEW_LINE, false)
                .set(BraceRules.FINALLY_ON_NEW_LINE, false)
                .set(BraceRules.EMPTY_CLASS_BODY, EmptyBodyStyle.COMPACT)
                .set(BraceRules.EMPTY_METHOD_BODY, EmptyBodyStyle.COMPACT)
                .set(BraceRules.EMPTY_CONTROL_BODY, EmptyBodyStyle.COMPACT);

            style.set(SpacingRules.WITHIN_ARRAY_INITIALIZER_BRACES, false);

            style.set(BlankLineRules.MAX_CONSECUTIVE, 1)
                .set(BlankLineRules.AFTER_PACKAGE, 1)
                .set(BlankLineRules.AFTER_IMPORTS, 1)
                .set(BlankLineRules.BEFORE_METHOD, 1)
                .set(BlankLineRules.BEFORE_CLASS, 1)
                .set(BlankLineRules.AFTER_CLASS_OPENING_BRACE, 0)
                .set(BlankLineRules.BEFORE_CLASS_CLOSING_BRACE, 0)
                .set(BlankLineRules.BEFORE_FIRST_ENUM_CONSTANT, 0)
                .set(BlankLineRules.BETWEEN_MEMBER_GROUPS, 0);

            style.set(AlignmentRules.CONSECUTIVE_FIELDS, AlignmentPolicy.NONE)
                .set(AlignmentRules.CONSECUTIVE_VARIABLES, AlignmentPolicy.NONE)
                .set(AlignmentRules.CONSECUTIVE_ASSIGNMENTS, AlignmentPolicy.NONE)
                .set(AlignmentRules.METHOD_CHAINS, AlignmentPolicy.NONE)
                .set(AlignmentRules.TRAILING_COMMENTS, AlignmentPolicy.NONE)
                .set(AlignmentRules.TERNARY_BRANCHES, AlignmentPolicy.NONE);

            style.set(AnnotationRules.DECLARATION_PLACEMENT, AnnotationPlacement.PRESERVE)
                .set(AnnotationRules.FIELD_PLACEMENT, AnnotationPlacement.PRESERVE);

            style.set(JavadocRules.WRAP, true)
                .set(JavadocRules.TAG_ORDER, JavadocTagOrder.CANONICAL)
                .set(JavadocRules.BLANK_LINE_BEFORE_TAGS, true)
                .set(JavadocRules.ADD_PARAGRAPH_TAGS, true)
                .set(JavadocRules.KEEP_SINGLE_LINE, true)
                .set(JavadocRules.TAG_CONTINUATION_INDENT, 4);

            style.set(PreservationRules.KEEP_LINE_BREAK_AFTER_OPEN_PAREN, false)
                .set(PreservationRules.RESPECT_EXISTING_CHAIN_BREAKS, false)
                .set(PreservationRules.KEEP_ARRAY_INITIALIZER_LAYOUT, true)
                .set(PreservationRules.KEEP_SIMPLE_BLOCKS_INLINE, false)
                .set(PreservationRules.KEEP_SIMPLE_METHODS_INLINE, false)
                .set(PreservationRules.KEEP_SIMPLE_LAMBDAS_INLINE, true)
                .set(PreservationRules.KEEP_SIMPLE_CLASSES_INLINE, false);

            style.set(ImportRules.GROUPS, List.of(List.of("*")))
                .set(ImportRules.ORDER, SortOrder.ASCENDING)
                .set(ImportRules.STATIC_PLACEMENT, StaticImportPlacement.FIRST)
                .set(ImportRules.BLANK_LINE_BETWEEN_GROUPS, true);

            // Google Java Style leaves these tokens as written, so every token rewrite stays off.
            style.set(MemberRules.ORDER, MemberOrder.PRESERVE)
                .set(ModifierRules.ORDER, ModifierOrder.PRESERVE)
                .set(ModifierRules.REMOVE_REDUNDANT, false)
                .set(DeclarationRules.ARRAY_BRACKETS, BracketStyle.PRESERVE)
                .set(DeclarationRules.REMOVE_REDUNDANT_SEMICOLONS, false)
                .set(LiteralRules.LONG_SUFFIX, LongSuffix.PRESERVE)
                .set(LiteralRules.HEX_DIGITS, HexDigitCase.PRESERVE)
                .set(LambdaRules.PARAMETER_STYLE, LambdaParameterStyle.PRESERVE)
                .set(SwitchRules.CASE_STYLE, SwitchCaseStyle.PRESERVE)
                .set(SwitchRules.YIELD_STYLE, YieldStyle.PRESERVE);
        }

    };

    /** Looks up a preset by its CLI or config name, e.g. {@code google}. */
    public static Preset of(String name) {
        String normalised = name.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (Preset preset : values()) {
            if (preset.name().equals(normalised)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown preset '" + name + "'");
    }

    /** This preset as a finished style. */
    public Style style() {
        StyleBuilder builder = Style.builder();
        applyTo(builder);
        return builder.build();
    }

    /** Writes this preset's rules into the builder. */
    abstract void applyTo(StyleBuilder style);

}
