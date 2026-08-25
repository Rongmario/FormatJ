package zone.rong.formatj.api.rules;

/** Parentheses around a single, untyped lambda parameter. */
public enum LambdaParameterStyle {

    /** Leave the author's choice alone. */
    PRESERVE,
    /** Always parenthesise, as in {@code (x) -> x}. */
    ALWAYS_PARENTHESISE,
    /** Drop the parentheses when the language allows, as in {@code x -> x}. */
    OMIT_WHEN_POSSIBLE

}
