package zone.rong.formatj.api.rules;

/** The line terminator written to formatted output. */
public enum LineEnding {

    /** Use whatever the file already uses, falling back to LF for a new file. */
    PRESERVE,
    /** Unix line endings. */
    LF,
    /** Windows line endings. */
    CRLF,
    /** The platform default of the JVM running the formatter. */
    SYSTEM

}
