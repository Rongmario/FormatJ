package zone.rong.formatj.api;

import java.util.List;
import java.util.Locale;

import zone.rong.formatj.api.rules.AlignmentPolicy;
import zone.rong.formatj.api.rules.AnnotationPlacement;
import zone.rong.formatj.api.rules.AssignmentBreak;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.BracketStyle;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.EmptyBodyStyle;
import zone.rong.formatj.api.rules.HexDigitCase;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.LambdaParameterStyle;
import zone.rong.formatj.api.rules.LongSuffix;
import zone.rong.formatj.api.rules.MemberOrder;
import zone.rong.formatj.api.rules.ModifierOrder;
import zone.rong.formatj.api.rules.OperatorWrap;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.StaticImportPlacement;
import zone.rong.formatj.api.rules.SwitchCaseStyle;
import zone.rong.formatj.api.rules.WrapPolicy;
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
            style.indent(indent -> indent.size(2)
                .continuation(4)
                .chainedCall(4)
                .arrayInitializer(2)
                .ternary(4)
                .throwsClause(4)
                .switchCaseLabels(true)
                .switchCaseBody(true))
                .wrapping(wrapping -> wrapping.maxLineLength(100)
                    .methodParameters(WrapPolicy.WRAP_IF_LONG)
                    .methodArguments(WrapPolicy.WRAP_IF_LONG)
                    .chainedCalls(ChainPolicy.BREAK_ALL_IF_MULTILINE)
                    .chainThreshold(2)
                    .operatorPosition(OperatorWrap.BEFORE_OPERATOR)
                    .assignment(WrapPolicy.WRAP_IF_LONG)
                    .arrayInitializers(WrapPolicy.WRAP_IF_LONG)
                    .extendsImplements(WrapPolicy.WRAP_IF_LONG)
                    .typeParameters(WrapPolicy.WRAP_IF_LONG)
                    .enumConstants(WrapPolicy.CHOP_DOWN_IF_LONG)
                    .forStatement(WrapPolicy.WRAP_IF_LONG)
                    .tryResources(WrapPolicy.CHOP_DOWN_IF_LONG)
                    .closingDelimiter(ClosingDelimiter.ATTACHED)
                    .assignmentBreak(AssignmentBreak.AFTER_OPERATOR)
                    .hugSoleArgument(false)
                    .keepSimpleMethodsOnOneLine(false)
                    .keepSimpleLambdasOnOneLine(true)
                    .keepSimpleClassesOnOneLine(false))
                .braces(braces -> braces.classPlacement(BracePlacement.END_OF_LINE)
                    .methodPlacement(BracePlacement.END_OF_LINE)
                    .controlPlacement(BracePlacement.END_OF_LINE)
                    .ifElse(BracePolicy.ALWAYS)
                    .forLoop(BracePolicy.ALWAYS)
                    .whileLoop(BracePolicy.ALWAYS)
                    .elseOnNewLine(false)
                    .catchOnNewLine(false)
                    .finallyOnNewLine(false)
                    .emptyClassBody(EmptyBodyStyle.COMPACT)
                    .emptyMethodBody(EmptyBodyStyle.COMPACT)
                    .emptyControlBody(EmptyBodyStyle.COMPACT))
                .spacing(spacing -> spacing.withinArrayInitializerBraces(false))
                .annotations(annotations -> annotations.declarationPlacement(AnnotationPlacement.PRESERVE)
                    .fieldPlacement(AnnotationPlacement.PRESERVE))
                .lambdas(lambdas -> lambdas.bodyBraces(BracePolicy.PRESERVE)
                    .parameterStyle(LambdaParameterStyle.PRESERVE))
                .switches(switches -> switches.caseStyle(SwitchCaseStyle.PRESERVE)
                    .arrowCaseBraces(BracePolicy.PRESERVE)
                    .yieldStyle(YieldStyle.PRESERVE))
                .modifiers(modifiers -> modifiers.order(ModifierOrder.PRESERVE).removeRedundant(false))
                .literals(literals -> literals.longSuffix(LongSuffix.PRESERVE).hexDigits(HexDigitCase.PRESERVE))
                .semicolons(semicolons -> semicolons.removeRedundant(false))
                .arrays(arrays -> arrays.cStyleBrackets(BracketStyle.PRESERVE))
                .members(members -> members.order(MemberOrder.PRESERVE))
                .patterns(patterns -> patterns.nestedIndent(8))
                .blankLines(blank -> blank.maxConsecutive(1)
                    .afterPackage(1)
                    .afterImports(1)
                    .beforeMethod(1)
                    .beforeClass(1)
                    .afterClassOpeningBrace(0)
                    .beforeClassClosingBrace(0)
                    .beforeFirstEnumConstant(0)
                    .betweenMemberGroups(0))
                .alignment(alignment -> alignment.consecutiveFields(AlignmentPolicy.NONE)
                    .consecutiveVariables(AlignmentPolicy.NONE)
                    .consecutiveAssignments(AlignmentPolicy.NONE)
                    .methodChains(AlignmentPolicy.NONE)
                    .trailingComments(AlignmentPolicy.NONE)
                    .ternaryBranches(AlignmentPolicy.NONE))
                .imports(imports -> imports.groups(List.of(List.of("*")))
                    .order(SortOrder.ASCENDING)
                    .staticPlacement(StaticImportPlacement.FIRST)
                    .blankLineBetweenGroups(true))
                .javadoc(javadoc -> javadoc.wrap(true)
                    .tagOrder(JavadocTagOrder.CANONICAL)
                    .blankLineBeforeTags(true)
                    .addParagraphTags(true)
                    .keepSingleLine(true)
                    .tagContinuationIndent(4))
                .preservation(preservation -> preservation.keepAuthorBlankLines(true)
                    .maxPreservedBlankLines(1)
                    .keepLineBreakAfterOpenParen(false)
                    .respectExistingChainBreaks(false)
                    .keepArrayInitializerLayout(true)
                    .keepSimpleBlocksInline(false));
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

    /** Writes this preset's rules into the builder. */
    abstract void applyTo(StyleBuilder style);

    /** This preset as a finished style. */
    public Style style() {
        StyleBuilder builder = Style.builder();
        applyTo(builder);
        return builder.build();
    }

}
