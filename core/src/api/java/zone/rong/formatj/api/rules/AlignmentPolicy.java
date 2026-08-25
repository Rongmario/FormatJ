package zone.rong.formatj.api.rules;

/** Column alignment of related constructs on consecutive lines. */
public enum AlignmentPolicy {

    /** No column alignment; single spaces only. */
    NONE,
    /** Pad to a shared column across a run of consecutive lines. */
    ALIGN_ON_COLUMN,
    /** Align only when the construct is already spread over several lines. */
    ALIGN_WHEN_MULTILINE

}
