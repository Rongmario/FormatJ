package zone.rong.formatj.api.rules;

/** How the value of an arrow switch case body is written. */
public enum YieldStyle {

    /** Leave the author's choice alone. */
    PRESERVE,

    /** Collapse {@code -> { yield x; }} to {@code -> x} when the body is a lone yield. */
    EXPRESSION_WHEN_POSSIBLE,

    /** Always use a block with an explicit {@code yield}. */
    ALWAYS_BLOCK

}
