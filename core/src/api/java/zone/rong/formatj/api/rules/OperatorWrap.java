package zone.rong.formatj.api.rules;

/** Which side of a broken operator- or separator-delimited construct the token lands on. */
public enum OperatorWrap {

    /** Operator starts the continuation line. */
    BEFORE_OPERATOR,
    /** Operator ends the line being broken. */
    AFTER_OPERATOR

}
