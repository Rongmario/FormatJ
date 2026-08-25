package zone.rong.formatj.api.rules;

/** Where an opening brace is placed relative to the construct that owns it. */
public enum BracePlacement {

    /** Same line as the declaration or statement header. */
    END_OF_LINE,
    /** Own line, at the owner's indentation level. */
    NEXT_LINE,
    /** Own line, indented one level past the owner. */
    NEXT_LINE_INDENTED

}
