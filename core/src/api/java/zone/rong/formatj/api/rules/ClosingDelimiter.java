package zone.rong.formatj.api.rules;

/**
 * Where the closing parenthesis of a wrapped list goes.
 *
 * <p>This governs the parenthesised lists — arguments, parameters, record components, annotation
 * elements, deconstruction patterns, and try resources — and only once the list has actually
 * wrapped. A list that prints on one line has no line for the parenthesis to move to.
 */
public enum ClosingDelimiter {

    /**
     * Keep the parenthesis against the last element.
     *
     * <pre>{@code
     * this.callIsLong(
     *         arg1,
     *         arg2);
     * }</pre>
     */
    ATTACHED,
    /**
     * Give the parenthesis a line of its own, at the indentation of the line that opened the list.
     *
     * <pre>{@code
     * this.callIsLong(
     *         arg1,
     *         arg2
     * );
     * }</pre>
     */
    OWN_LINE

}
