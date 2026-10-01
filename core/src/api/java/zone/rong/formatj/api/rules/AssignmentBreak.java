package zone.rong.formatj.api.rules;

/** Where a long assignment breaks first. */
public enum AssignmentBreak {

    /**
     * Break after the operator, giving the value a line of its own.
     *
     * <pre>{@code
     * Result result =
     *         compute(first, second);
     * }</pre>
     */
    AFTER_OPERATOR,
    /**
     * Keep the value on the operator's line while its first line fits, and break inside it.
     *
     * <pre>{@code
     * Result result = compute(
     *         first,
     *         second);
     * }</pre>
     */
    INSIDE_VALUE

}
