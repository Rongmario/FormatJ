package zone.rong.formatj.api.rules;

/** Whether comment text may be re-wrapped. */
public enum CommentReflow {

    /** Never re-wrap; only move comments with the code they belong to. */
    PRESERVE,

    /** Re-wrap prose to the configured line length. */
    REFLOW_TO_LINE_LENGTH

}
