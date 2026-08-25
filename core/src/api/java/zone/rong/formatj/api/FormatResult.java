package zone.rong.formatj.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of formatting one request.
 *
 * <p>A failed result still carries usable text: FormatJ returns the original source rather than a
 * half-formatted file, so a caller writing {@link #text()} back to disk can never corrupt it.
 */
public final class FormatResult {

    private final String text;
    private final boolean unchanged;
    private final List<Diagnostic> diagnostics;

    private FormatResult(String text, boolean unchanged, List<Diagnostic> diagnostics) {
        this.text = Objects.requireNonNull(text, "text");
        this.unchanged = unchanged;
        this.diagnostics = List.copyOf(diagnostics);
    }

    /** The source was already formatted; nothing needs writing. */
    public static FormatResult unchanged(String source) {
        return new FormatResult(source, true, List.of());
    }

    /** The source was reformatted to {@code formatted}. */
    public static FormatResult formatted(String source, String formatted) {
        return new FormatResult(formatted, source.equals(formatted), List.of());
    }

    /** Formatting was abandoned; the original source is returned with the reason attached. */
    public static FormatResult failed(String source, List<Diagnostic> diagnostics) {
        return new FormatResult(source, true, diagnostics);
    }

    /** The same result with extra diagnostics attached. */
    public FormatResult withDiagnostics(List<Diagnostic> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        List<Diagnostic> merged = new ArrayList<>(diagnostics);
        merged.addAll(extra);
        return new FormatResult(text, unchanged, merged);
    }

    /** The text to write back: either the formatted source or, on failure, the original. */
    public String text() {
        return text;
    }

    /** Whether the text is identical to the input. */
    public boolean isUnchanged() {
        return unchanged;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    /** Whether any diagnostic is an error, meaning the source was left untouched. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }

}
