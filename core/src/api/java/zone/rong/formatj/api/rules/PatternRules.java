package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Type patterns, record deconstruction patterns and guards. */
public final class PatternRules {

    public static final Option<WrapPolicy> DECONSTRUCTION_WRAPPING =
            Option.ofEnum(
                    "patterns.deconstruction-wrapping",
                    WrapPolicy.WRAP_IF_LONG,
                    "Wrapping of a record deconstruction pattern");

    public static final Option<Boolean> KEEP_SIMPLE_PATTERN_INLINE =
            Option.ofBoolean(
                    "patterns.keep-simple-pattern-inline",
                    true,
                    "Keep a short pattern on the line of its test");

    public static final Option<Integer> NESTED_INDENT =
            Option.ofInt("patterns.nested-indent", 8, "Columns a wrapped nested pattern is indented");

    private PatternRules() { }

    /** Fluent view of the {@code patterns.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder deconstructionWrapping(WrapPolicy value) {
            style.set(DECONSTRUCTION_WRAPPING, value);
            return this;
        }

        public Builder keepSimplePatternInline(boolean value) {
            style.set(KEEP_SIMPLE_PATTERN_INLINE, value);
            return this;
        }

        public Builder nestedIndent(int value) {
            style.set(NESTED_INDENT, value);
            return this;
        }

    }

}
