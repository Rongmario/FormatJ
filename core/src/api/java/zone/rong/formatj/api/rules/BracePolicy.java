package zone.rong.formatj.api.rules;

/** Whether a body that could be written without braces gets them. */
public enum BracePolicy {

    /** Always add braces. */
    ALWAYS,
    /** Remove braces whenever the language permits. */
    NEVER,
    /** Braces only when the body holds more than one statement. */
    WHEN_MULTI_STATEMENT,
    /** Leave the author's choice alone. */
    PRESERVE

}
