package zone.rong.formatj.api.rules;

/** Case of the {@code F} and {@code D} suffixes on floating-point literals. */
public enum FloatSuffix {

    /** Keep the suffix as written. */
    PRESERVE,
    /** Write the suffix as {@code F} or {@code D}. */
    UPPER,
    /** Write the suffix as {@code f} or {@code d}. */
    LOWER

}
