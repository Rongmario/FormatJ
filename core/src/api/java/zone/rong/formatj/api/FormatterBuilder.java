package zone.rong.formatj.api;

/**
 * Builds a {@link Formatter}. Obtained from {@code FormatJ.newFormatter()} in the core module.
 *
 * <pre>{@code
 * Formatter formatter = FormatJ.newFormatter()
 *         .style(Style.preset(Preset.FORMATJ)
 *                 .indent(i -> i.size(4))
 *                 .wrapping(w -> w.maxLineLength(120))
 *                 .build())
 *         .languageLevel(LanguageLevel.LATEST)
 *         .build();
 * }</pre>
 */
public interface FormatterBuilder {

    /** Sets the rules to format with. Defaults to {@link Style#defaults()}. */
    FormatterBuilder style(Style style);

    /** Sets the syntax level to parse. Defaults to {@link LanguageLevel#LATEST}. */
    FormatterBuilder languageLevel(LanguageLevel languageLevel);

    /** Enables preview syntax for the chosen language level. Defaults to {@code false}. */
    FormatterBuilder previewFeatures(boolean previewFeatures);

    /**
     * Verifies every result before returning it: formatting must reach a fixed point and must not
     * change the token stream. Costs a second pass. Defaults to {@code true}.
     */
    FormatterBuilder verify(boolean verify);

    /** Builds an immutable, thread-safe formatter. */
    Formatter build();

}
