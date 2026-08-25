package zone.rong.formatj.gradle;

import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.Preset;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Applies FormatJ to a Gradle project.
 *
 * <p>Registers {@code formatJavaApply} and {@code formatJavaCheck}, and wires the check task into
 * {@code check} so an unformatted file fails the build the same way a failing test does.
 */
public class FormatJPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "formatJ";
    public static final String APPLY_TASK_NAME = "formatJavaApply";
    public static final String CHECK_TASK_NAME = "formatJavaCheck";
    public static final String TASK_GROUP = "formatting";

    @Override
    public void apply(Project project) {
        FormatJExtension extension = project.getExtensions().create(EXTENSION_NAME, FormatJExtension.class);
        extension.getLanguageLevel().convention(LanguageLevel.LATEST);
        extension.getPreviewFeatures().convention(false);
        extension.getEnforceOnCheck().convention(true);
        extension.getPreset().convention(Preset.FORMATJ);

        Provider<FileCollection> sources = project.provider(() -> javaSources(project, extension));

        TaskProvider<FormatJTask> apply = project.getTasks().register(
                APPLY_TASK_NAME,
                FormatJTask.class,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription("Formats Java sources in place with FormatJ.");
                    configure(task, extension, sources);
                    task.getCheckOnly().set(false);
                    task.getMarkerFile().set(project.getLayout().getBuildDirectory().file("formatj/apply.marker"));
                });

        TaskProvider<FormatJTask> check = project.getTasks().register(
                CHECK_TASK_NAME,
                FormatJTask.class,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.setDescription("Fails if any Java source is not formatted to the configured style.");
                    configure(task, extension, sources);
                    task.getCheckOnly().set(true);
                    task.getMarkerFile().set(project.getLayout().getBuildDirectory().file("formatj/check.marker"));
                });

        project.getPlugins().withType(
                LifecycleBasePlugin.class,
                ignored -> project.getTasks()
                        .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                        .configure(task -> task.dependsOn(
                                project.provider(() -> extension.getEnforceOnCheck().get()
                                        ? List.of(check)
                                        : List.of()))));

        // Applying then checking in one invocation must run in that order, not in parallel.
        check.configure(task -> task.mustRunAfter(apply));
    }

    private static void configure(FormatJTask task, FormatJExtension extension, Provider<FileCollection> sources) {
        task.getSource().from(sources);
        task.getStyleFile().set(extension.getStyleFile());
        task.getPreset().set(extension.getPreset());
        task.getRules().set(extension.getRules());
        task.getLanguageLevel().set(extension.getLanguageLevel());
        task.getPreviewFeatures().set(extension.getPreviewFeatures());
    }

    private static FileCollection javaSources(Project project, FormatJExtension extension) {
        JavaPluginExtension java = project.getExtensions().findByType(JavaPluginExtension.class);
        if (java == null) {
            return project.files();
        }
        SourceSetContainer sourceSets = java.getSourceSets();
        List<String> selected = extension.getSourceSets().getOrNull();
        FileCollection files = project.files();
        for (SourceSet sourceSet : sourceSets) {
            if (selected == null || selected.contains(sourceSet.getName())) {
                files = files.plus(sourceSet.getAllJava().filter(file -> file.getName().endsWith(".java")));
            }
        }
        return files;
    }

}
