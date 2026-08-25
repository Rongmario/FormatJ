package zone.rong.formatj.gradle;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.Incremental;
import org.gradle.work.InputChanges;

/**
 * Formats or checks a set of Java sources.
 *
 * <p>Incremental by file: only sources Gradle reports as changed are re-formatted, and every rule is
 * an input, so changing one option invalidates the results rather than leaving stale output behind.
 */
@CacheableTask
public abstract class FormatJTask extends DefaultTask {

    /** The Java sources to process. */
    @InputFiles
    @Incremental
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSource();

    /** Style file applied on top of the preset. */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getStyleFile();

    @Input
    @Optional
    public abstract Property<Preset> getPreset();

    @Input
    public abstract MapProperty<String, String> getRules();

    @Input
    public abstract Property<LanguageLevel> getLanguageLevel();

    @Input
    public abstract Property<Boolean> getPreviewFeatures();

    /** When true the task reports files that would change instead of rewriting them. */
    @Input
    public abstract Property<Boolean> getCheckOnly();

    /**
     * A marker written when the task completes.
     *
     * <p>Formatting rewrites its own inputs and produces no other output, but Gradle needs an output
     * to track incremental state against, so the task declares this one.
     */
    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void execute(InputChanges changes) {
        Formatter formatter = FormatJ.newFormatter()
                .style(resolveStyle())
                .languageLevel(getLanguageLevel().get())
                .previewFeatures(getPreviewFeatures().get())
                .build();

        List<String> wouldChange = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int formatted = 0;

        for (var change : changes.getFileChanges(getSource())) {
            if (change.getChangeType() == ChangeType.REMOVED || !change.getFile().isFile()) {
                continue;
            }
            File file = change.getFile();
            if (!file.getName().endsWith(".java")) {
                continue;
            }
            FormatResult result = format(formatter, file);
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.severity() == Diagnostic.Severity.ERROR) {
                    failures.add(diagnostic.format(file.getPath()));
                } else {
                    getLogger().info("{}", diagnostic.format(file.getPath()));
                }
            }
            if (result.isUnchanged() || result.hasErrors()) {
                continue;
            }
            if (getCheckOnly().get()) {
                wouldChange.add(file.getPath());
            } else {
                write(file.toPath(), result.text());
                formatted++;
                getLogger().lifecycle("formatted {}", file.getPath());
            }
        }

        if (!failures.isEmpty()) {
            throw new GradleException(
                    "FormatJ could not format " + failures.size() + " file(s):\n" + String.join("\n", failures));
        }
        if (!wouldChange.isEmpty()) {
            throw new GradleException(
                    "FormatJ found " + wouldChange.size() + " file(s) that are not formatted. Run formatJavaApply.\n"
                            + String.join("\n", wouldChange));
        }
        getLogger().info("FormatJ formatted {} file(s)", formatted);
        writeMarker(formatted);
    }

    private void writeMarker(int formatted) {
        Path marker = getMarkerFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "formatted " + formatted + " file(s)\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + marker, e);
        }
    }

    private Style resolveStyle() {
        StyleBuilder builder = Style.builder();
        if (getPreset().isPresent()) {
            builder.apply(getPreset().get().style());
        }
        if (getStyleFile().isPresent()) {
            builder.apply(StyleFiles.load(getStyleFile().get().getAsFile().toPath()));
        }
        Map<String, String> rules = getRules().get();
        rules.forEach(builder::setRaw);
        return builder.build();
    }

    private static FormatResult format(Formatter formatter, File file) {
        try {
            String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return formatter.format(FormatRequest.of(source).withName(file.getPath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

}
