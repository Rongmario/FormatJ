package zone.rong.formatj.idea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

/**
 * Style resolution and formatter reuse. Range splicing happens inside core's formatter itself.
 * Kept free of IntelliJ types so it can be tested like the other plugins.
 */
public final class FormatJEngine {

    private static final int MAX_FORMATTERS = 8;

    private final Settings settings;

    // Bounded: every edit of formatj.toml yields a new Style and so a new key.
    private final Map<CacheKey, Formatter> formatters = Collections.synchronizedMap(new LinkedHashMap<>(
        16,
        0.75f,
        true
    ) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Formatter> eldest) {
            return size() > MAX_FORMATTERS;
        }

    });
    private final ConcurrentHashMap<Path, LoadedStyle> styleFiles = new ConcurrentHashMap<>();

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
                builder.apply(load(settings.styleFile()));
            }
            return builder.build();
        }
        Path start = path != null ? path : Path.of("").toAbsolutePath();
        return StyleFiles.discover(start).map(this::load).orElseGet(Style::defaults);
    }

    /** Re-reads a style file only when its modification time changes. */
    private Style load(Path file) {
        FileTime modified;
        try {
            modified = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return StyleFiles.load(file);
        }
        LoadedStyle cached = styleFiles.get(file);
        if (cached != null && cached.lastModified().equals(modified)) {
            return cached.style();
        }
        Style style = StyleFiles.load(file);
        styleFiles.put(file, new LoadedStyle(modified, style));
        return style;
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
        Formatter formatter = formatters.computeIfAbsent(
            new CacheKey(style, request.languageLevel(), request.previewFeatures(), request.rewrites()),
            key -> FormatJ.newFormatter()
                .style(key.style())
                .languageLevel(key.languageLevel())
                .previewFeatures(key.previewFeatures())
                .rewrites(key.rewrites())
                .build()
        );
        FormatResult result = formatter.format(
            FormatRequest.of(request.source()).withName(request.name()).withRanges(request.ranges())
        );
        if (result.hasErrors()) {
            return new Outcome(request.source(), true, result.diagnostics());
        }
        return new Outcome(result.text(), result.isUnchanged(), result.diagnostics());
    }

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
        boolean rewrites
    ) { }

    public record Outcome(String text, boolean unchanged, List<Diagnostic> diagnostics) {

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == Diagnostic.Severity.ERROR);
        }

    }

    private record CacheKey(Style style, LanguageLevel languageLevel, boolean previewFeatures, boolean rewrites) { }

    private record LoadedStyle(FileTime lastModified, Style style) { }

}
