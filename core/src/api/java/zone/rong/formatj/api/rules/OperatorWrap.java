package zone.rong.formatj.api.rules;

/** Which side of a broken binary expression the operator lands on. */
public enum OperatorWrap {

    /** Operator starts the continuation line. */
    BEFORE_OPERATOR,
    /** Operator ends the line being broken. */
    AFTER_OPERATOR

}
