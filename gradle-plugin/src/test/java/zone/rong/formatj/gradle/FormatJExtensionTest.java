package zone.rong.formatj.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatJExtensionTest {

    private static FormatJExtension extensionOf(Project project) {
        project.getPlugins().apply("java");
        project.getPlugins().apply(FormatJPlugin.class);
        return project.getExtensions().getByType(FormatJExtension.class);
    }

    @Test
    void registersBothTasksAndTheExtension() {
        Project project = ProjectBuilder.builder().build();
        extensionOf(project);

        assertTrue(project.getTasks().getNames().contains(FormatJPlugin.APPLY_TASK_NAME));
        assertTrue(project.getTasks().getNames().contains(FormatJPlugin.CHECK_TASK_NAME));
    }

    @Test
    void rulesAreValidatedWhenTheyAreSet() {
        FormatJExtension extension = extensionOf(ProjectBuilder.builder().build());

        extension.rule("indent.size", 2);
        extension.rule("modifiers.order", "canonical");
        extension.rules(Map.of("wrapping.max-line-length", 100));

        assertEquals(
            Map.of("indent.size", "2", "modifiers.order", "canonical", "wrapping.max-line-length", "100"),
            extension.getRules().get()
        );
        assertThrows(IllegalArgumentException.class, () -> extension.rule("indent.siz", 2));
        assertThrows(IllegalArgumentException.class, () -> extension.rule("indent.size", "wide"));
    }

    @Test
    void defaultsMatchTheDocumentedConventions() {
        FormatJExtension extension = extensionOf(ProjectBuilder.builder().build());

        // Left unset, so FormatJTask falls back to discovery and then to FormatJ's own defaults.
        assertFalse(extension.getPreset().isPresent());
        assertEquals(Boolean.FALSE, extension.getPreviewFeatures().get());
        assertEquals(Boolean.TRUE, extension.getEnforceOnCheck().get());
    }

    @Test
    void includeAndExcludeNarrowTheSources() throws IOException {
        Project project = ProjectBuilder.builder().build();
        FormatJExtension extension = extensionOf(project);
        for (String path : List.of("a/Kept.java", "a/skip/Skipped.java", "b/Other.java")) {
            Path file = project.file("src/main/java/" + path).toPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
        extension.include("a/**");
        extension.exclude("**/skip/**");

        FormatJTask check = (FormatJTask) project.getTasks().getByName(FormatJPlugin.CHECK_TASK_NAME);
        assertEquals(Set.of(project.file("src/main/java/a/Kept.java")), check.getSource().getFiles());
    }

}
