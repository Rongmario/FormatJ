package zone.rong.formatj.core.config;

import zone.rong.formatj.api.Option;
import zone.rong.formatj.api.OptionRegistry;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and saves {@code formatj.toml} style files.
 *
 * <p>A style file may open with a top-level {@code preset = "google"}, which chooses the starting
 * point; every other key overrides one rule on top of it.
 */
public final class StyleFiles {

    /** The file name searched for when no style file is named explicitly. */
    public static final String DEFAULT_FILE_NAME = "formatj.toml";

    private static final String PRESET_KEY = "preset";

    private StyleFiles() {}

    /** Reads a style file. */
    public static Style load(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read style file " + file, e);
        }
    }

    /** Parses style file content. */
    public static Style parse(String document) {
        Map<String, String> entries = TomlReader.read(document);
        StyleBuilder builder = Style.builder();
        String preset = entries.remove(PRESET_KEY);
        if (preset != null) {
            Preset.of(preset)
                    .style()
                    .explicitValues()
                    .forEach((key, value) -> {
                        Option<?> option = OptionRegistry.require(key);
                        setChecked(builder, option, value);
                    });
        }
        entries.forEach(builder::setRaw);
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setChecked(StyleBuilder builder, Option<T> option, Object value) {
        builder.set(option, (T) value);
    }

    /** Writes a style out as a commented TOML document. */
    public static void save(Style style, Path file) {
        try {
            Files.writeString(file, TomlWriter.write(style), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write style file " + file, e);
        }
    }

    /** Searches {@code start} and its ancestors for a style file. */
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
