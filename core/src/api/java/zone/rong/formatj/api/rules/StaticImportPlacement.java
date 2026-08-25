package zone.rong.formatj.api.rules;

/** Where static imports sit relative to ordinary imports. */
public enum StaticImportPlacement {

    /** Before every ordinary import. */
    FIRST,

    /** After every ordinary import. */
    LAST,

    /** Interleaved with ordinary imports by package name. */
    INLINE

}
