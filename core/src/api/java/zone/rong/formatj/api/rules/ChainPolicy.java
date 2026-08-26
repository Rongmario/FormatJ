package zone.rong.formatj.api.rules;

/** How a chain of method calls such as {@code a.b().c().d()} is broken across lines. */
public enum ChainPolicy {

    /** Keep the author's line breaks. */
    PRESERVE,
    /** Keep on one line, or put every link on its own line. */
    BREAK_ALL_IF_MULTILINE,
    /**
     * Put every link on its own line, but only once the chain's own line is too long.
     *
     * <p>The difference from {@link #BREAK_ALL_IF_MULTILINE} is what counts as too much for one line.
     * A chain ending in a call that takes a block lambda spans several lines whatever the margin says,
     * and under {@code BREAK_ALL_IF_MULTILINE} that alone breaks every link. Here the argument's own
     * lines are the argument's business: only the text up to the first break it forces is measured, so
     * a chain whose head fits keeps its head on one line.
     */
    BREAK_ALL_WHEN_TOO_LONG,
    /** Break only as many links as needed to fit the line length. */
    BREAK_WHEN_TOO_LONG,
    /** Never introduce a break inside a chain, however long it gets. */
    NEVER_BREAK

}
