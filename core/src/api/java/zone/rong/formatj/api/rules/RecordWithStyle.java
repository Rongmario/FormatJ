package zone.rong.formatj.api.rules;

/** Layout of a derived record creation expression, {@code point with { x = 1; }}. */
public enum RecordWithStyle {

    /** Leave the author's layout alone. */
    PRESERVE,

    /** Always spread the with-block over multiple lines. */
    ALWAYS_BLOCK,

    /** Keep on one line while it fits the line length. */
    INLINE_WHEN_SHORT

}
