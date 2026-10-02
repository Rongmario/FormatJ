package zone.rong.formatj.core.config;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

/**
 * Include/exclude globs, matched against a path relative to a fixed base directory.
 *
 * <p>An exclude wins over an include; an empty include list means every path passes that gate.
 * Shared by the CLI, Gradle and Maven plugins so a {@code [files]} table in {@code formatj.toml}
 * and each entry point's own include/exclude flags are matched the same way.
 */
public final class FileSelection {

    /** No globs at all: every path matches. */
    public static final FileSelection NONE = new FileSelection(Path.of(""), List.of(), List.of());

    private final Path base;
    private final List<PathMatcher> includes;
    private final List<PathMatcher> excludes;

    public FileSelection(Path base, List<String> includeGlobs, List<String> excludeGlobs) {
        this.base = base.toAbsolutePath().normalize();
        this.includes = matchers(includeGlobs);
        this.excludes = matchers(excludeGlobs);
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>(globs.size());
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return List.copyOf(matchers);
    }

    public boolean isEmpty() {
        return includes.isEmpty() && excludes.isEmpty();
    }

    /** Whether {@code path}, relativized against this selection's base, is selected. */
    public boolean matches(Path path) {
        Path relative = relativize(path);
        for (PathMatcher exclude : excludes) {
            if (exclude.matches(relative)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (PathMatcher include : includes) {
            if (include.matches(relative)) {
                return true;
            }
        }
        return false;
    }

    private Path relativize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        return absolute.startsWith(base) ? base.relativize(absolute) : absolute;
    }

}
