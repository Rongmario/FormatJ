package zone.rong.formatj.maven;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import zone.rong.formatj.api.Diagnostic;
import zone.rong.formatj.api.FormatRequest;
import zone.rong.formatj.api.FormatResult;
import zone.rong.formatj.api.Formatter;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import zone.rong.formatj.api.Style;
import zone.rong.formatj.api.StyleBuilder;
import zone.rong.formatj.api.rules.FileRules;
import zone.rong.formatj.core.FormatJ;
import zone.rong.formatj.core.config.FileSelection;
import zone.rong.formatj.core.config.StyleFiles;
import zone.rong.formatj.core.io.SourceFiles;

/**
 * Shared configuration and file walking for the FormatJ goals.
 *
 * <p>Both goals run the same engine over the same files and differ only in what they do with a file
 * that would change, which is the point: {@code formatj:check} in CI can never disagree with
 * {@code formatj:format} on a developer's machine.
 */
abstract class AbstractFormatJMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true) protected MavenProject project;

    /** Style file to read rules from, usually {@code formatj.toml} in the project root. */
    @Parameter(property = "formatj.styleFile") protected File styleFile;

    /**
     * Preset to start from: {@code formatj} or {@code google}.
     *
     * <p>Left unset, and with no {@link #styleFile} either, the nearest {@code formatj.toml} above
     * the project directory is discovered and used; failing that, the {@code formatj} preset.
     */
    @Parameter(property = "formatj.preset") protected String preset;

    /** Individual rule overrides keyed by dotted option key, applied last. */
    @Parameter protected Map<String, String> rules = new LinkedHashMap<>();

    /** Globs limiting which files are formatted. Empty means every Java source. */
    @Parameter protected List<String> includes = new ArrayList<>();

    /** Globs excluding files from formatting. */
    @Parameter protected List<String> excludes = new ArrayList<>();

    /** Whether to format test sources as well as main sources. */
    @Parameter(property = "formatj.includeTestSources", defaultValue = "true") protected boolean includeTestSources;

    /**
     * Java syntax level to parse and to write, e.g. 21. Defaults to the compiler plugin's {@code release}, then its
     * {@code source}, then the newest FormatJ knows.
     */
    @Parameter(property = "formatj.languageLevel") protected Integer languageLevel;

    /** Whether preview syntax is accepted for that language level. */
    @Parameter(property = "formatj.previewFeatures", defaultValue = "false") protected boolean previewFeatures;

    /** Encoding of the source files. */
    @Parameter(
        property = "formatj.encoding", defaultValue = "${project.build.sourceEncoding}"
    ) protected String encoding;

    /** Skips the goal entirely. */
    @Parameter(property = "formatj.skip", defaultValue = "false") protected boolean skip;

    private static Charset charset(String name) throws MojoExecutionException {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("Unknown <encoding> '" + name + "'", e);
        }
    }

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
        // An explicit <encoding> wins; otherwise the style's own file.charset decides.
        Charset charset = encoding == null || encoding.isBlank()
            ? FileRules.charset(formatter.style())
            : charset(encoding);
        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int formatted = 0;

        for (Path file : files) {
            String source;
            try {
                source = SourceFiles.readString(file, charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot read " + file + ": " + e.getMessage(), e);
            }
            FormatResult result = formatter.format(FormatRequest.of(source).withName(file.toString()));
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
                    failures.add(diagnostic.format(file.toString()));
                } else {
                    getLog().warn(diagnostic.format(file.toString()));
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
                SourceFiles.writeAtomic(file, result.text(), charset);
            } catch (IOException e) {
                throw new MojoExecutionException("Cannot write " + file, e);
            }
            formatted++;
            getLog().info("Formatted " + file);
        }

        if (!failures.isEmpty()) {
            throw new MojoExecutionException(
                "FormatJ could not format " + failures.size() + " file(s):\n" + String.join("\n", failures)
            );
        }
        if (!wouldChange.isEmpty()) {
            throw new MojoFailureException(
                "FormatJ found " + wouldChange.size() + " file(s) that are not formatted. Run formatj:format.\n" +
                    String.join("\n", wouldChange)
            );
        }
        getLog().info("FormatJ checked " + files.size() + " file(s), formatted " + formatted);
    }

    /**
     * The style these goals apply, resolved from preset, style file and inline rules.
     *
     * <p>Neither {@link #preset} nor {@link #styleFile} was set: discover the nearest
     * {@code formatj.toml} above the project directory, the same lookup the CLI does.
     */
    protected Style style() throws MojoExecutionException {
        StyleBuilder builder = Style.builder();
        boolean explicit = (preset != null && !preset.isBlank()) || styleFile != null;
        if (!explicit) {
            StyleFiles.discover(project.getBasedir().toPath()).ifPresent(file -> builder.apply(StyleFiles.load(file)));
        }
        if (preset != null && !preset.isBlank()) {
            try {
                builder.apply(Preset.of(preset).style());
            } catch (IllegalArgumentException e) {
                throw new MojoExecutionException(e.getMessage() + ". Use formatj or google.", e);
            }
        }
        if (styleFile != null) {
            builder.apply(StyleFiles.load(styleFile.toPath()));
        }
        rules.forEach(builder::setRaw);
        return builder.build();
    }

    /** The style file driving {@link #style()}, if any: explicit, or the nearest discovered one. */
    private Optional<Path> styleFileInUse() {
        if (styleFile != null) {
            return Optional.of(styleFile.toPath());
        }
        if (preset != null && !preset.isBlank()) {
            return Optional.empty();
        }
        return StyleFiles.discover(project.getBasedir().toPath());
    }

    private Formatter formatter() throws MojoExecutionException {
        return FormatJ.newFormatter()
            .style(style())
            .languageLevel(languageLevel == null ? projectLanguageLevel() : LanguageLevel.ofRelease(languageLevel))
            .previewFeatures(previewFeatures)
            .build();
    }

    LanguageLevel projectLanguageLevel() {
        String release = compilerSetting("release");
        if (release == null) {
            release = compilerSetting("source");
        }
        if (release == null) {
            return LanguageLevel.LATEST;
        }
        try {
            // 1.8 is the old spelling of 8.
            int feature = Integer.parseInt(release.startsWith("1.") ? release.substring(2) : release);
            return LanguageLevel.ofRelease(Math.max(feature, LanguageLevel.JAVA_8.release()));
        } catch (IllegalArgumentException e) {
            return LanguageLevel.LATEST;
        }
    }

    /** A setting of the compiler plugin, from its configuration or else from its user property. */
    private String compilerSetting(String name) {
        Plugin compiler = project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compiler != null && compiler.getConfiguration() instanceof Xpp3Dom configuration) {
            Xpp3Dom setting = configuration.getChild(name);
            if (setting != null && setting.getValue() != null) {
                return setting.getValue();
            }
        }
        return project.getProperties().getProperty("maven.compiler." + name);
    }

    /** Every Java source of the project that the include and exclude globs allow. */
    protected List<Path> sourceFiles() throws MojoExecutionException {
        List<String> roots = new ArrayList<>(project.getCompileSourceRoots());
        if (includeTestSources) {
            roots.addAll(project.getTestCompileSourceRoots());
        }
        // The style file's own [files] table, relative to its directory; a style's includes/excludes
        // apply everywhere, unlike <includes>/<excludes> below which are relative to each source root.
        FileSelection tomlSelection = styleFileInUse().map(StyleFiles::fileSelection).orElse(FileSelection.NONE);
        String buildDirectoryProperty = project.getBuild() == null ? null : project.getBuild().getDirectory();
        Path buildDirectory = buildDirectoryProperty == null
            ? null
            : Path.of(buildDirectoryProperty).toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        for (String root : roots) {
            Path directory = Path.of(root);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            if (buildDirectory != null && directory.toAbsolutePath().normalize().startsWith(buildDirectory)) {
                // e.g. target/generated-sources: not something a developer wrote, so not FormatJ's to touch.
                getLog().warn("FormatJ skipping generated source root " + directory);
                continue;
            }
            FileSelection rootSelection = new FileSelection(directory, includes, excludes);
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> rootSelection.matches(path) && tomlSelection.matches(path))
                    .sorted()
                    .forEach(files::add);
            } catch (IOException | UncheckedIOException e) {
                throw new MojoExecutionException("Cannot walk " + directory, e);
            }
        }
        return files;
    }

}
