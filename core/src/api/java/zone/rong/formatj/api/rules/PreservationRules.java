package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** What of the author's layout survives formatting. */
public final class PreservationRules {

    public static final Option<Boolean> KEEP_AUTHOR_BLANK_LINES =
            Option.ofBoolean(
                    "preservation.keep-author-blank-lines",
                    true,
                    "Keep blank lines the author placed inside bodies");

    public static final Option<Integer> MAX_PRESERVED_BLANK_LINES =
            Option.ofInt("preservation.max-preserved-blank-lines", 1, "Most consecutive author blank lines kept");

    public static final Option<Boolean> KEEP_LINE_BREAK_AFTER_OPEN_PAREN =
            Option.ofBoolean(
                    "preservation.keep-line-break-after-open-paren",
                    false,
                    "Keep a break the author put after an opening parenthesis");

    public static final Option<Boolean> KEEP_SIMPLE_BLOCKS_INLINE =
            Option.ofBoolean(
                    "preservation.keep-simple-blocks-inline",
                    true,
                    "Keep a block the author wrote on one line on one line");

    public static final Option<Boolean> KEEP_ARRAY_INITIALIZER_LAYOUT =
            Option.ofBoolean(
                    "preservation.keep-array-initializer-layout",
                    true,
                    "Keep the row layout of a hand-arranged array initializer");

    public static final Option<Boolean> RESPECT_EXISTING_CHAIN_BREAKS =
            Option.ofBoolean(
                    "preservation.respect-existing-chain-breaks",
                    true,
                    "Keep breaks the author placed in a method chain");

    public static final Option<Boolean> NEVER_JOIN_LINES =
            Option.ofBoolean("preservation.never-join-lines", false, "Never merge two lines the author kept apart");

    private PreservationRules() { }

    /** Fluent view of the {@code preservation.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder keepAuthorBlankLines(boolean value) {
            style.set(KEEP_AUTHOR_BLANK_LINES, value);
            return this;
        }

        public Builder maxPreservedBlankLines(int value) {
            style.set(MAX_PRESERVED_BLANK_LINES, value);
            return this;
        }

        public Builder keepLineBreakAfterOpenParen(boolean value) {
            style.set(KEEP_LINE_BREAK_AFTER_OPEN_PAREN, value);
            return this;
        }

        public Builder keepSimpleBlocksInline(boolean value) {
            style.set(KEEP_SIMPLE_BLOCKS_INLINE, value);
            return this;
        }

        public Builder keepArrayInitializerLayout(boolean value) {
            style.set(KEEP_ARRAY_INITIALIZER_LAYOUT, value);
            return this;
        }

        public Builder respectExistingChainBreaks(boolean value) {
            style.set(RESPECT_EXISTING_CHAIN_BREAKS, value);
            return this;
        }

        public Builder neverJoinLines(boolean value) {
            style.set(NEVER_JOIN_LINES, value);
            return this;
        }

    }

}
