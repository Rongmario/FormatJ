package zone.rong.formatj.idea;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.SourceRange;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.core.FormatJ;
import zone.rong.formatj.core.config.StyleFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Style resolution, formatter reuse and range splicing.
 * Kept free of IntelliJ types so it can be tested like the other plugins.
 */
public final class FormatJEngine {

    /**
     * Explicit style pins from the IDE. A null style file and null preset means walk up for
     * {@code formatj.toml}, the same as the CLI with no flags.
     */
    public record Settings(Path styleFile, Preset preset) {

        public static Settings discover() {
            return new Settings(null, null);
        }

        boolean explicit() {
            return styleFile != null || preset != null;
        }

    }

    public record Request(
            String source,
            String name,
            Path path,
            List<SourceRange> ranges,
            LanguageLevel languageLevel,
            boolean previewFeatures,
            boolean rewrites) { }

    public record Outcome(String text, boolean unchanged, List<Diagnostic> diagnostics) {

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR);
        }

    }

    private record CacheKey(Style style, LanguageLevel languageLevel, boolean previewFeatures, boolean rewrites) { }

    private final Settings settings;
    private final ConcurrentHashMap<CacheKey, Formatter> formatters = new ConcurrentHashMap<>();

    public FormatJEngine(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Settings settings() {
        return settings;
    }

    /**
     * The rules the given file (or directory) should be formatted with.
     *
     * <p>An explicit preset or style file wins outright, matching the CLI's {@code --preset} /
     * {@code --style}. Otherwise the nearest {@code formatj.toml} above the path is used.
     */
    public Style styleFor(Path path) {
        if (settings.explicit()) {
            StyleBuilder builder = Style.builder();
            if (settings.preset() != null) {
                builder.apply(settings.preset().style());
            }
            if (settings.styleFile() != null) {
                builder.apply(StyleFiles.load(settings.styleFile()));
            }
            return builder.build();
        }
        Path start = path != null ? path : Path.of("").toAbsolutePath();
        return StyleFiles.discoverOrDefault(start);
    }

    /** A one-line description of which style would apply, for the settings page. */
    public String describeStyle(Path path) {
        if (settings.styleFile() != null) {
            return "Using " + settings.styleFile();
        }
        if (settings.preset() != null) {
            return "Using preset " + settings.preset().name().toLowerCase(Locale.ROOT);
        }
        Path start = path != null ? path : Path.of("").toAbsolutePath();
        return StyleFiles.discover(start).map(file -> "Using " + file).orElse("Using built-in defaults");
    }

    public Outcome format(Request request) {
        Objects.requireNonNull(request, "request");
        Style style = styleFor(request.path());
        Formatter formatter =
                formatters.computeIfAbsent(
                        new CacheKey(style, request.languageLevel(), request.previewFeatures(), request.rewrites()),
                        key -> FormatJ.newFormatter()
                                .style(key.style())
                                .languageLevel(key.languageLevel())
                                .previewFeatures(key.previewFeatures())
                                .rewrites(key.rewrites())
                                .build());
        FormatResult result =
                formatter.format(
                        FormatRequest.of(request.source()).withName(request.name()).withRanges(request.ranges()));
        if (result.hasErrors()) {
            return new Outcome(request.source(), true, result.diagnostics());
        }
        String formatted = result.text();
        if (!wholeFile(request.source(), request.ranges())) {
            formatted = LineDiffer.splice(request.source(), formatted, request.ranges());
        }
        return new Outcome(formatted, formatted.equals(request.source()), result.diagnostics());
    }

    static boolean wholeFile(String source, List<SourceRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return true;
        }
        if (ranges.size() != 1) {
            return false;
        }
        SourceRange range = ranges.getFirst();
        return range.startOffset() == 0 && range.endOffset() >= source.length();
    }

}
