package zone.rong.formatj.api.rules;

/** How the incidental indentation of a text block is handled when surrounding code moves. */
public enum TextBlockIndentPolicy {

    /** Never touch the inside of a text block. */
    PRESERVE,
    /** Re-indent the closing delimiter and content to follow the enclosing block. */
    REINDENT_TO_BLOCK,
    /** Strip incidental whitespace down to the minimum the content allows. */
    MINIMAL

}
