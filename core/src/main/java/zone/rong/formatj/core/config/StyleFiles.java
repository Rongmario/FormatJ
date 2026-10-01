package zone.rong.formatj.core.config;

import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and saves {@code formatj.toml} style files.
 *
 * <p>A style file may open with a top-level {@code preset = "google"}, which chooses the starting
 * point; every other key overrides one rule on top of it. A {@code [files]} table with
 * {@code include}/{@code exclude} glob arrays steers which files are formatted rather than how;
 * see {@link #fileSelection(Path)}.
 */
public final class StyleFiles {

    /** The file name searched for when no style file is named explicitly. */
    public static final String DEFAULT_FILE_NAME = "formatj.toml";

    private static final String PRESET_KEY = "preset";
    private static final String FILES_INCLUDE_KEY = "files.include";
    private static final String FILES_EXCLUDE_KEY = "files.exclude";

    private StyleFiles() {}

    /** Reads a style file. */
    public static Style load(Path file) {
        try {
            return parse(read(file));
        } catch (TomlReader.TomlException e) {
            throw e.forFile(file);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(file + ": " + e.getMessage(), e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new UncheckedIOException("cannot read style file " + file + ": no such file", e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read style file " + file + ": " + e.getMessage(), e);
        }
    }

    /** Parses style file content. */
    public static Style parse(String document) {
        Map<String, String> entries = TomlReader.read(document);
        StyleBuilder builder = Style.builder();
        String preset = entries.remove(PRESET_KEY);
        if (preset != null) {
            builder.apply(Preset.of(preset).style());
        }
        // [files] is peeled off like preset: it steers which files are formatted rather than how,
        // so it is not a registered Option and must not trip "Unknown option" below.
        entries.remove(FILES_INCLUDE_KEY);
        entries.remove(FILES_EXCLUDE_KEY);
        entries.forEach(builder::setRaw);
        return builder.build();
    }

    /** The {@code [files] include/exclude} globs a style file declares, relative to its directory. */
    public static FileSelection fileSelection(Path file) {
        Map<String, String> entries = TomlReader.read(read(file));
        Path base = file.toAbsolutePath().getParent();
        return new FileSelection(
                base,
                globArray(entries.get(FILES_INCLUDE_KEY)),
                globArray(entries.get(FILES_EXCLUDE_KEY)));
    }

    /** The file selection of the nearest style file to {@code start}, or none when there is none. */
    public static FileSelection discoverFileSelection(Path start) {
        return discover(start).map(StyleFiles::fileSelection).orElse(FileSelection.NONE);
    }

    /** A TOML string array, as {@code files.include}/{@code files.exclude} write it. */
    private static List<String> globArray(String raw) {
        if (raw == null) {
            return List.of();
        }
        String body = raw.trim();
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        return TomlReader.splitTopLevel(body, ',').stream()
                .map(String::trim)
                .filter(element -> !element.isEmpty())
                .map(element -> TomlReader.unquote(element, 0))
                .toList();
    }

    /** Writes a style out as a commented TOML document. */
    public static void save(Style style, Path file) {
        try {
            Files.writeString(file, TomlWriter.write(style), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write style file " + file, e);
        }
    }

    /**
     * Searches {@code start} and its ancestors for a style file.
     *
     * <p>The nearest file wins; parent style files are not merged in. A multi-module repository
     * that wants one shared style keeps a single {@code formatj.toml} at the root and no other
     * module overrides it, rather than relying on merging.
     */
    public static Optional<Path> discover(Path start) {
        Path directory = Files.isDirectory(start) ? start : start.getParent();
        while (directory != null) {
            Path candidate = directory.resolve(DEFAULT_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            directory = directory.getParent();
        }
        return Optional.empty();
    }

    /** The nearest style file to {@code start}, or the built-in defaults when there is none. */
    public static Style discoverOrDefault(Path start) {
        return discover(start).map(StyleFiles::load).orElseGet(Style::defaults);
    }

}
