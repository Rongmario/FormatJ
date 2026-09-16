package zone.rong.formatj.api.rules;

/** Where a Javadoc paragraph marker sits relative to the paragraph it opens. */
public enum JavadocOpeningTagPosition {

    /** Leave the author's placement alone. */
    PRESERVE,
    /** Marker on its own line, paragraph text starts on the next line. */
    NEW_LINE,
    /** Marker shares its line with the paragraph's first words. */
    SAME_LINE

}
