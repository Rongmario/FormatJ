package zone.rong.formatj.api.rules;

/** Where the access modifier sits among a declaration's other modifiers. */
public enum VisibilityPosition {

    /** Before every other modifier, as the Java Language Specification orders them. */
    FIRST,
    /** After every other modifier. */
    LAST

}
