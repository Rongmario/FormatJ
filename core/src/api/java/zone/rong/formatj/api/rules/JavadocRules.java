package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Javadoc-specific layout, separate from ordinary block comments. */
public final class JavadocRules {

    public static final Option<Boolean> WRAP =
            Option.ofBoolean("javadoc.wrap", false, "Wrap Javadoc prose to the configured line length");

    public static final Option<JavadocTagOrder> TAG_ORDER =
            Option.ofEnum("javadoc.tag-order", JavadocTagOrder.PRESERVE, "Ordering of Javadoc block tags");

    public static final Option<Boolean> BLANK_LINE_BEFORE_TAGS =
            Option.ofBoolean(
                    "javadoc.blank-line-before-tags",
                    true,
                    "Blank line between the description and the first block tag");

    public static final Option<Boolean> ALIGN_TAG_DESCRIPTIONS =
            Option.ofBoolean("javadoc.align-tag-descriptions", false, "Align the descriptions following block tags");

    public static final Option<Boolean> ADD_PARAGRAPH_TAGS =
            Option.ofBoolean("javadoc.add-paragraph-tags", false, "Insert <p> on blank description lines");

    public static final Option<Boolean> KEEP_SINGLE_LINE =
            Option.ofBoolean("javadoc.keep-single-line", true, "Leave a one-line Javadoc comment on one line");

    public static final Option<Integer> TAG_CONTINUATION_INDENT =
            Option.ofInt("javadoc.tag-continuation-indent", 8, "Columns a wrapped block tag description is indented");

    public static final Option<JavadocClosingTagForm> CLOSING_TAG_FORM =
            Option.ofEnum(
                    "javadoc.closing-tag-form",
                    JavadocClosingTagForm.PRESERVE,
                    "Written form of a Javadoc paragraph closer");

    public static final Option<JavadocOpeningTagPosition> OPENING_TAG_POSITION =
            Option.ofEnum(
                    "javadoc.opening-tag-position",
                    JavadocOpeningTagPosition.PRESERVE,
                    "Placement of a Javadoc paragraph marker relative to its paragraph");

    private JavadocRules() { }

    /** Fluent view of the {@code javadoc.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder wrap(boolean value) {
            style.set(WRAP, value);
            return this;
        }

        public Builder tagOrder(JavadocTagOrder value) {
            style.set(TAG_ORDER, value);
            return this;
        }

        public Builder blankLineBeforeTags(boolean value) {
            style.set(BLANK_LINE_BEFORE_TAGS, value);
            return this;
        }

        public Builder alignTagDescriptions(boolean value) {
            style.set(ALIGN_TAG_DESCRIPTIONS, value);
            return this;
        }

        public Builder addParagraphTags(boolean value) {
            style.set(ADD_PARAGRAPH_TAGS, value);
            return this;
        }

        public Builder keepSingleLine(boolean value) {
            style.set(KEEP_SINGLE_LINE, value);
            return this;
        }

        public Builder tagContinuationIndent(int value) {
            style.set(TAG_CONTINUATION_INDENT, value);
            return this;
        }

        public Builder closingTagForm(JavadocClosingTagForm value) {
            style.set(CLOSING_TAG_FORM, value);
            return this;
        }

        public Builder openingTagPosition(JavadocOpeningTagPosition value) {
            style.set(OPENING_TAG_POSITION, value);
            return this;
        }

    }

}
