package zone.rong.formatj.api.rules;

/** How an empty block is rendered. */
public enum EmptyBodyStyle {

    /** {@code {}} with nothing between the braces. */
    COMPACT,
    /** {@code { }} with a space between the braces. */
    SPACED,
    /** Opening and closing brace on separate lines. */
    EXPANDED

}
