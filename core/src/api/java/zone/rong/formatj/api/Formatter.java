package zone.rong.formatj.api;

/**
 * Formats Java source text.
 *
 * <p>Implementations are immutable and safe to share across threads, so one formatter can be reused
 * for a whole project and handed to a thread pool.
 */
public interface Formatter {

    /** Formats one request. */
    FormatResult format(FormatRequest request);

    /** The style this formatter applies. */
    Style style();

    /** The syntax level this formatter parses. */
    LanguageLevel languageLevel();

    /** Convenience for formatting a string with no name and no range restriction. */
    default String format(String source) {
        return format(FormatRequest.of(source)).text();
    }

}
