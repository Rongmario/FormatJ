package zone.rong.formatj.api.rules;

/** Ordering of block tags inside a Javadoc comment. */
public enum JavadocTagOrder {

    /** Leave the author's order. */
    PRESERVE,

    /** The order recommended by the Javadoc tool: param, return, throws, then the rest. */
    CANONICAL

}
