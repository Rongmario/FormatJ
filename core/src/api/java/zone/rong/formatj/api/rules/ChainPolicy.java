package zone.rong.formatj.api.rules;

/** How a chain of method calls such as {@code a.b().c().d()} is broken across lines. */
public enum ChainPolicy {

    /** Keep the author's line breaks. */
    PRESERVE,

    /** Keep on one line, or put every link on its own line. */
    BREAK_ALL_IF_MULTILINE,

    /** Break only as many links as needed to fit the line length. */
    BREAK_WHEN_TOO_LONG,

    /** Never introduce a break inside a chain, however long it gets. */
    NEVER_BREAK

}
