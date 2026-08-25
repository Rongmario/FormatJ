package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Records, compact constructors and derived record creation. */
public final class RecordRules {

    public static final Option<WrapPolicy> COMPONENT_WRAPPING =
            Option.ofEnum(
                    "records.component-wrapping",
                    WrapPolicy.CHOP_DOWN_IF_LONG,
                    "Wrapping of a record header's components");

    public static final Option<Boolean> SINGLE_LINE_EMPTY_BODY =
            Option.ofBoolean("records.single-line-empty-body", false, "Render an empty record body as {}");

    public static final Option<Boolean> COMPACT_CONSTRUCTOR_BLANK_LINE =
            Option.ofBoolean(
                    "records.compact-constructor-blank-line",
                    false,
                    "Blank line inside a compact canonical constructor");

    public static final Option<RecordWithStyle> WITH_STYLE =
            Option.ofEnum(
                    "records.with-style",
                    RecordWithStyle.INLINE_WHEN_SHORT,
                    "Layout of a derived record creation with-block");

    public static final Option<Boolean> SPACE_BEFORE_WITH_BLOCK =
            Option.ofBoolean("records.space-before-with-block", true, "Space between the with keyword and its block");

    private RecordRules() { }

    /** Fluent view of the {@code records.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder componentWrapping(WrapPolicy value) {
            style.set(COMPONENT_WRAPPING, value);
            return this;
        }

        public Builder singleLineEmptyBody(boolean value) {
            style.set(SINGLE_LINE_EMPTY_BODY, value);
            return this;
        }

        public Builder compactConstructorBlankLine(boolean value) {
            style.set(COMPACT_CONSTRUCTOR_BLANK_LINE, value);
            return this;
        }

        public Builder withStyle(RecordWithStyle value) {
            style.set(WITH_STYLE, value);
            return this;
        }

        public Builder spaceBeforeWithBlock(boolean value) {
            style.set(SPACE_BEFORE_WITH_BLOCK, value);
            return this;
        }

    }

}
