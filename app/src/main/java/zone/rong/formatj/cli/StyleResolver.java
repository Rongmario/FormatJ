package zone.rong.formatj.cli;

import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.core.config.StyleFiles;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Works out which rules apply to a file.
 *
 * <p>Precedence, strongest first: {@code --set}, then {@code --preset} or {@code --style}, then the
 * nearest {@code formatj.toml} above the file, then the built-in defaults. Per-directory resolution
 * matters in a multi-module repository where one module deliberately differs.
 */
final class StyleResolver {

    private final CliOptions options;
    private final Style explicit;
    private final Map<Path, Style> byDirectory = new HashMap<>();

    StyleResolver(CliOptions options) {
        this.options = options;
        StyleBuilder builder = Style.builder();
        options.preset().map(Preset::style).ifPresent(builder::apply);
        options.styleFile().map(StyleFiles::load).ifPresent(builder::apply);
        options.overrides().forEach(builder::setRaw);
        this.explicit = builder.build();
    }

    /** The rules the given file should be formatted with. */
    synchronized Style forFile(Path file) {
        if (options.styleFile().isPresent() || options.preset().isPresent()) {
            // An explicitly named style wins outright; discovery would only muddy it.
            return explicit;
        }
        Path directory = file.toAbsolutePath().getParent();
        Style discovered = byDirectory.computeIfAbsent(directory, StyleFiles::discoverOrDefault);
        return discovered.mergedWith(explicit);
    }

    /** The rules used when there is no file, as with {@code --stdin} or {@code --dump-config}. */
    Style forStandardInput() {
        if (options.styleFile().isPresent() || options.preset().isPresent()) {
            return explicit;
        }
        return StyleFiles.discoverOrDefault(Path.of("").toAbsolutePath()).mergedWith(explicit);
    }

}
