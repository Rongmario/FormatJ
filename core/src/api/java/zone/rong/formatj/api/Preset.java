package zone.rong.formatj.api;

import zone.rong.formatj.api.rules.AlignmentPolicy;
import zone.rong.formatj.api.rules.BracePlacement;
import zone.rong.formatj.api.rules.BracePolicy;
import zone.rong.formatj.api.rules.ChainPolicy;
import zone.rong.formatj.api.rules.ClosingDelimiter;
import zone.rong.formatj.api.rules.CommentReflow;
import zone.rong.formatj.api.rules.JavadocTagOrder;
import zone.rong.formatj.api.rules.SortOrder;
import zone.rong.formatj.api.rules.StaticImportPlacement;
import zone.rong.formatj.api.rules.WrapPolicy;
import java.util.List;
import java.util.Locale;

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
                            .closingDelimiter(ClosingDelimiter.ATTACHED)
                            .keepSimpleMethodsOnOneLine(false)
                            .keepSimpleClassesOnOneLine(false))
                    .braces(braces -> braces.classPlacement(BracePlacement.END_OF_LINE)
                            .methodPlacement(BracePlacement.END_OF_LINE)
                            .controlPlacement(BracePlacement.END_OF_LINE)
                            .ifElse(BracePolicy.ALWAYS)
                            .forLoop(BracePolicy.ALWAYS)
                            .whileLoop(BracePolicy.ALWAYS)
                            .elseOnNewLine(false)
                            .catchOnNewLine(false)
                            .finallyOnNewLine(false))
                    .blankLines(blank -> blank.maxConsecutive(1)
                            .afterPackage(1)
                            .afterImports(1)
                            .beforeMethod(1)
                            .beforeClass(1)
                            .afterClassOpeningBrace(0)
                            .beforeClassClosingBrace(0))
                    .alignment(alignment -> alignment.consecutiveFields(AlignmentPolicy.NONE)
                            .consecutiveVariables(AlignmentPolicy.NONE)
                            .consecutiveAssignments(AlignmentPolicy.NONE)
                            .methodChains(AlignmentPolicy.NONE)
                            .trailingComments(AlignmentPolicy.NONE)
                            .ternaryBranches(AlignmentPolicy.NONE))
                    .imports(imports -> imports.groups(List.of("*"))
                            .order(SortOrder.ASCENDING)
                            .staticPlacement(StaticImportPlacement.FIRST)
                            .blankLineBetweenGroups(true))
                    .javadoc(javadoc -> javadoc.wrap(true)
                            .tagOrder(JavadocTagOrder.CANONICAL)
                            .blankLineBeforeTags(true)
                            .addParagraphTags(true)
                            .tagContinuationIndent(4))
                    .comments(comments -> comments.reflow(CommentReflow.REFLOW_TO_LINE_LENGTH))
                    .preservation(preservation -> preservation.keepAuthorBlankLines(true)
                            .maxPreservedBlankLines(1)
                            .keepLineBreakAfterOpenParen(false)
                            .respectExistingChainBreaks(false)
                            .keepSimpleBlocksInline(false));
        }

    }
    ;

    /** Writes this preset's rules into the builder. */
    abstract void applyTo(StyleBuilder style);

    /** This preset as a finished style. */
    public Style style() {
        StyleBuilder builder = Style.builder();
        applyTo(builder);
        return builder.build();
    }

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

}
