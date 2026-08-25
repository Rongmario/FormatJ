package zone.rong.formatj.maven;

import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.core.FormatJ;
import zone.rong.formatj.core.config.StyleFiles;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Shared configuration and file walking for the FormatJ goals.
 *
 * <p>Both goals run the same engine over the same files and differ only in what they do with a file
 * that would change, which is the point: {@code formatj:check} in CI can never disagree with
 * {@code formatj:format} on a developer's machine.
 */
abstract class AbstractFormatJMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /** Style file to read rules from, usually {@code formatj.toml} in the project root. */
    @Parameter(property = "formatj.styleFile")
    protected File styleFile;

    /** Preset to start from: {@code formatj} or {@code google}. */
    @Parameter(property = "formatj.preset", defaultValue = "formatj")
    protected String preset;

    /** Individual rule overrides keyed by dotted option key, applied last. */
    @Parameter
    protected Map<String, String> rules = new LinkedHashMap<>();

    /** Globs limiting which files are formatted. Empty means every Java source. */
    @Parameter
    protected List<String> includes = new ArrayList<>();

    /** Globs excluding files from formatting. */
    @Parameter
    protected List<String> excludes = new ArrayList<>();

    /** Whether to format test sources as well as main sources. */
    @Parameter(property = "formatj.includeTestSources", defaultValue = "true")
    protected boolean includeTestSources;

    /** Java syntax level to parse, e.g. 21. Defaults to the newest FormatJ knows. */
    @Parameter(property = "formatj.languageLevel")
    protected Integer languageLevel;

    /** Whether preview syntax is accepted for that language level. */
    @Parameter(property = "formatj.previewFeatures", defaultValue = "false")
    protected boolean previewFeatures;

    /** Encoding of the source files. */
    @Parameter(property = "formatj.encoding", defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /** Skips the goal entirely. */
    @Parameter(property = "formatj.skip", defaultValue = "false")
    protected boolean skip;

    /** Whether a file that would change is rewritten, or merely reported. */
    protected abstract boolean checkOnly();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("FormatJ is skipped");
            return;
        }
        List<Path> files = sourceFiles();
        if (files.isEmpty()) {
            getLog().info("FormatJ found no Java sources");
            return;
        }

        Formatter formatter = formatter();
        Charset charset = encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int formatted = 0;

        for (Path file : files) {
            String source;
            try {
                source = Files.readString(file, charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot read " + file, e);
            }
            FormatResult result = formatter.format(FormatRequest.of(source).withName(file.toString()));
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
                    failures.add(diagnostic.format(file.toString()));
                } else {
                    getLog().debug(diagnostic.format(file.toString()));
                }
            }
            if (result.isUnchanged() || result.hasErrors()) {
                continue;
            }
            if (checkOnly()) {
                wouldChange.add(file.toString());
                continue;
            }
            try {
                Files.writeString(file, result.text(), charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot write " + file, e);
            }
            formatted++;
            getLog().info("Formatted " + file);
        }

        if (!failures.isEmpty()) {
            throw new MojoExecutionException(
                    "FormatJ could not format " + failures.size() + " file(s):\n" + String.join("\n", failures));
        }
        if (!wouldChange.isEmpty()) {
            throw new MojoFailureException(
                    "FormatJ found " + wouldChange.size() + " file(s) that are not formatted. Run formatj:format.\n"
                            + String.join("\n", wouldChange));
        }
        getLog().info("FormatJ checked " + files.size() + " file(s), formatted " + formatted);
    }

    /** The style these goals apply, resolved from preset, style file and inline rules. */
    protected Style style() {
        StyleBuilder builder = Style.builder();
        if (preset != null && !preset.isBlank()) {
            builder.apply(Preset.of(preset).style());
        }
        if (styleFile != null) {
            builder.apply(StyleFiles.load(styleFile.toPath()));
        }
        rules.forEach(builder::setRaw);
        return builder.build();
    }

    private Formatter formatter() {
        return FormatJ.newFormatter()
                .style(style())
                .languageLevel(languageLevel == null ? LanguageLevel.LATEST : LanguageLevel.ofRelease(languageLevel))
                .previewFeatures(previewFeatures)
                .build();
    }

    /** Every Java source of the project that the include and exclude globs allow. */
    protected List<Path> sourceFiles() throws MojoExecutionException {
        List<String> roots = new ArrayList<>(project.getCompileSourceRoots());
        if (includeTestSources) {
            roots.addAll(project.getTestCompileSourceRoots());
        }
        List<PathMatcher> includeMatchers = matchers(includes);
        List<PathMatcher> excludeMatchers = matchers(excludes);
        List<Path> files = new ArrayList<>();
        for (String root : roots) {
            Path directory = Path.of(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> allowed(path, includeMatchers, excludeMatchers))
                        .sorted()
                        .forEach(files::add);
            } catch (IOException | UncheckedIOException e) {
                throw new MojoExecutionException("Cannot walk " + directory, e);
            }
        }
        return files;
    }

    private static boolean allowed(Path path, List<PathMatcher> includes, List<PathMatcher> excludes) {
        for (PathMatcher exclude : excludes) {
            if (exclude.matches(path)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (PathMatcher include : includes) {
            if (include.matches(path)) {
                return true;
            }
        }
        return false;
    }

    private static List<PathMatcher> matchers(List<String> globs) {
        List<PathMatcher> matchers = new ArrayList<>();
        for (String glob : globs) {
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));
        }
        return matchers;
    }

}
