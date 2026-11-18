package zone.rong.formatj.api.rules;

import zone.rong.formatj.api.Option;

/** Comment placement, re-wrapping and the formatter-off escape hatch. */
public final class CommentRules {

    public static final Option<CommentReflow> REFLOW = Option.ofEnum(
        "comments.reflow",
        CommentReflow.PRESERVE,
        "Whether line and block comment prose may be re-wrapped"
    );

    /**
     * Whether a {@code //} comment gets a space after its slashes.
     *
     * <p>Only prose qualifies, meaning text that starts with a letter or digit and does not end like
     * a statement or block. Commented-out code, divider lines and tool markers such as
     * {@code //noinspection} or {@code //$NON-NLS-1$} keep their form.
     */
    public static final Option<Boolean> SPACE_AFTER_LINE_COMMENT = Option.ofBoolean(
        "comments.space-after-line-comment",
        false,
        "Put a space between the slashes of a line comment and its prose"
    );

    public static final Option<Boolean> BLOCK_COMMENT_STAR_ALIGNMENT = Option.ofBoolean(
        "comments.block-comment-star-alignment",
        true,
        "Align the leading stars of a block comment"
    );

    public static final Option<Integer> TRAILING_COMMENT_MIN_SPACES = Option.ofInt(
        "comments.trailing-comment-min-spaces",
        1,
        "Spaces between code and a comment trailing it"
    );

    public static final Option<Integer> TRAILING_COMMENT_COLUMN = Option.ofInt(
        "comments.trailing-comment-column",
        0,
        "Column trailing comments are padded to; 0 disables"
    );

    public static final Option<Boolean> KEEP_FIRST_COLUMN_COMMENTS = Option.ofBoolean(
        "comments.keep-first-column-comments",
        false,
        "Leave a comment starting in column one where it is"
    );

    public static final Option<Boolean> INDENT_WITH_CODE = Option.ofBoolean(
        "comments.indent-with-code",
        true,
        "Indent comments to match the code that follows them"
    );

    public static final Option<Boolean> HONOUR_FORMATTER_OFF = Option.ofBoolean(
        "comments.honour-formatter-off",
        true,
        "Respect @formatter:off and @formatter:on markers"
    );

    public static final Option<String> OFF_MARKER = Option.ofString(
        "comments.off-marker",
        "@formatter:off",
        "Marker that suspends formatting until the on-marker"
    );

    public static final Option<String> ON_MARKER = Option.ofString(
        "comments.on-marker",
        "@formatter:on",
        "Marker that resumes formatting"
    );

    private CommentRules() { }

}
