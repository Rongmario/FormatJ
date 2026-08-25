package zone.rong.formatj.api.rules;

/** How a comma-separated or operator-separated list is wrapped. */
public enum WrapPolicy {

    /** Keep the author's line breaks. */
    PRESERVE,
    /** Fill lines, breaking only where the line length demands it. */
    WRAP_IF_LONG,
    /** One element per line as soon as the list does not fit. */
    CHOP_DOWN_IF_LONG,
    /** One element per line, always. */
    CHOP_DOWN_ALWAYS,
    /** Never break; let the line run long. */
    NEVER

}
