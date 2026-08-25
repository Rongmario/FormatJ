package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.StyleBuilder;

/** Comment placement, re-wrapping and the formatter-off escape hatch. */
public final class CommentRules {

    public static final Option<CommentReflow> REFLOW =
            Option.ofEnum(
                    "comments.reflow",
                    CommentReflow.PRESERVE,
                    "Whether line and block comment prose may be re-wrapped");

    public static final Option<Boolean> BLOCK_COMMENT_STAR_ALIGNMENT =
            Option.ofBoolean(
                    "comments.block-comment-star-alignment",
                    true,
                    "Align the leading stars of a block comment");

    public static final Option<Integer> TRAILING_COMMENT_MIN_SPACES =
            Option.ofInt("comments.trailing-comment-min-spaces", 1, "Spaces between code and a comment trailing it");

    public static final Option<Integer> TRAILING_COMMENT_COLUMN =
            Option.ofInt("comments.trailing-comment-column", 0, "Column trailing comments are padded to; 0 disables");

    public static final Option<Boolean> KEEP_FIRST_COLUMN_COMMENTS =
            Option.ofBoolean(
                    "comments.keep-first-column-comments",
                    false,
                    "Leave a comment starting in column one where it is");

    public static final Option<Boolean> INDENT_WITH_CODE =
            Option.ofBoolean("comments.indent-with-code", true, "Indent comments to match the code that follows them");

    public static final Option<Boolean> HONOUR_FORMATTER_OFF =
            Option.ofBoolean("comments.honour-formatter-off", true, "Respect @formatter:off and @formatter:on markers");

    public static final Option<String> OFF_MARKER =
            Option.ofString(
                    "comments.off-marker",
                    "formatj:off",
                    "Marker that suspends formatting until the on-marker");

    public static final Option<String> ON_MARKER =
            Option.ofString("comments.on-marker", "formatj:on", "Marker that resumes formatting");

    private CommentRules() { }

    /** Fluent view of the {@code comments.*} rules. */
    public static final class Builder {

        private final StyleBuilder style;

        public Builder(StyleBuilder style) {
            this.style = style;
        }

        public Builder reflow(CommentReflow value) {
            style.set(REFLOW, value);
            return this;
        }

        public Builder blockCommentStarAlignment(boolean value) {
            style.set(BLOCK_COMMENT_STAR_ALIGNMENT, value);
            return this;
        }

        public Builder trailingCommentMinSpaces(int value) {
            style.set(TRAILING_COMMENT_MIN_SPACES, value);
            return this;
        }

        public Builder trailingCommentColumn(int value) {
            style.set(TRAILING_COMMENT_COLUMN, value);
            return this;
        }

        public Builder keepFirstColumnComments(boolean value) {
            style.set(KEEP_FIRST_COLUMN_COMMENTS, value);
            return this;
        }

        public Builder indentWithCode(boolean value) {
            style.set(INDENT_WITH_CODE, value);
            return this;
        }

        public Builder honourFormatterOff(boolean value) {
            style.set(HONOUR_FORMATTER_OFF, value);
            return this;
        }

        public Builder offMarker(String value) {
            style.set(OFF_MARKER, value);
            return this;
        }

        public Builder onMarker(String value) {
            style.set(ON_MARKER, value);
            return this;
        }

    }

}
