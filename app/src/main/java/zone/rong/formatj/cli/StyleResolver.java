package zone.rong.formatj.cli;

import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.core.config.FileSelection;
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
    private final FileSelection explicitFileSelection;
    private final Map<Path, Style> byDirectory = new HashMap<>();
    private final Map<Path, FileSelection> fileSelectionByDirectory = new HashMap<>();

    StyleResolver(CliOptions options) {
        this.options = options;
        StyleBuilder builder = Style.builder();
        options.preset().map(Preset::style).ifPresent(builder::apply);
        options.styleFile().map(StyleFiles::load).ifPresent(builder::apply);
        options.overrides().forEach(builder::setRaw);
        this.explicit = builder.build();
        this.explicitFileSelection = options.styleFile().map(StyleFiles::fileSelection).orElse(FileSelection.NONE);
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

    /** The {@code [files]} include/exclude globs that apply to the given file, if any. */
    synchronized FileSelection fileSelectionFor(Path file) {
        if (options.styleFile().isPresent()) {
            return explicitFileSelection;
        }
        if (options.preset().isPresent()) {
            // No style file is in play at all; only the CLI's own --include/--exclude apply.
            return FileSelection.NONE;
        }
        Path directory = file.toAbsolutePath().getParent();
        return fileSelectionByDirectory.computeIfAbsent(directory, StyleFiles::discoverFileSelection);
    }

    /** The rules used when there is no file, as with {@code --stdin} or {@code --dump-config}. */
    Style forStandardInput() {
        if (options.styleFile().isPresent() || options.preset().isPresent()) {
            return explicit;
        }
        return StyleFiles.discoverOrDefault(stdinNameDirectory()).mergedWith(explicit);
    }

    /**
     * The directory to discover from for {@code --stdin}: the directory of {@code --stdin-name}
     * when it names a path, falling back to the working directory for the unnamed default.
     */
    private Path stdinNameDirectory() {
        Path parent = Path.of(options.stdinName()).toAbsolutePath().getParent();
        return parent != null ? parent : Path.of("").toAbsolutePath();
    }

}
