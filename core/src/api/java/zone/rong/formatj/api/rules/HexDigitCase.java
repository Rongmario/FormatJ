package zone.rong.formatj.api.rules;

/** Case of the digits {@code a} to {@code f} in hexadecimal literals. */
public enum HexDigitCase {

    /** Keep the digits as written. */
    PRESERVE,
    /** Write the digits as {@code A} to {@code F}. */
    UPPER,
    /** Write the digits as {@code a} to {@code f}. */
    LOWER

}
