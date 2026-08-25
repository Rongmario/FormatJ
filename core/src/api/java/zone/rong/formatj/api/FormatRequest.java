package zone.rong.formatj.api;

import java.util.List;
import java.util.Objects;

/**
 * One unit of work: the source text, a display name for diagnostics, and optionally the ranges to
 * format rather than the whole file.
 */
public final class FormatRequest {

    private final String source;
    private final String name;
    private final List<SourceRange> ranges;

    private FormatRequest(String source, String name, List<SourceRange> ranges) {
        this.source = Objects.requireNonNull(source, "source");
        this.name = Objects.requireNonNull(name, "name");
        this.ranges = List.copyOf(ranges);
    }

    /** A request to format the whole of {@code source}. */
    public static FormatRequest of(String source) {
        return new FormatRequest(source, "<source>", List.of());
    }

    /** The same request with a name used in diagnostics, usually a file path. */
    public FormatRequest withName(String name) {
        return new FormatRequest(source, name, ranges);
    }

    /** The same request restricted to the given ranges; an empty list means the whole file. */
    public FormatRequest withRanges(List<SourceRange> ranges) {
        return new FormatRequest(source, name, ranges);
    }

    public String source() {
        return source;
    }

    public String name() {
        return name;
    }

    /** The ranges to format; empty means the whole file. */
    public List<SourceRange> ranges() {
        return ranges;
    }

    public boolean isWholeFile() {
        return ranges.isEmpty();
    }

}
