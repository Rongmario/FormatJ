package zone.rong.formatj.api.rules;

/** Which written form a Javadoc paragraph closer takes. */
public enum JavadocClosingTagForm {

    /** Leave the author's form alone. */
    PRESERVE,
    /** Write closers slash-first: {@code <p/>} becomes {@code </p>}. Openers are untouched. */
    SLASH_FIRST

}
