package zone.rong.formatj.api.rules;

/**
 * Column alignment of related constructs on consecutive lines.
 *
 * <p>{@link #ALIGN_ON_COLUMN} and {@link #ALIGN_WHEN_MULTILINE} currently ask for the same output,
 * and that is not an oversight in the implementation. Alignment is padding placed in front of
 * something, and padding is only visible on a line that follows a break: a run needs two lines before
 * there is a shared column to pad to, and a chain or a conditional that stayed on one line has no
 * continuation line for its own alignment to apply to. So there is no construct either value can
 * reach that the other cannot, and the distinction the second name promises does not exist.
 */
public enum AlignmentPolicy {

    /** No column alignment; single spaces only. */
    NONE,
    /** Pad to a shared column across a run of consecutive lines. */
    ALIGN_ON_COLUMN,
    /**
     * Align only when the construct is already spread over several lines.
     *
     * <p>Which is every case in which alignment shows at all; see the note on the enum itself.
     */
    ALIGN_WHEN_MULTILINE

}
