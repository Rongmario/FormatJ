package zone.rong.formatj.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import zone.rong.formatj.api.Preset;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

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
        extension.rules(Map.of("wrapping.max-line-length", 100));

        assertEquals(Map.of("indent.size", "2", "wrapping.max-line-length", "100"), extension.getRules().get());
        assertThrows(IllegalArgumentException.class, () -> extension.rule("indent.siz", 2));
        assertThrows(IllegalArgumentException.class, () -> extension.rule("indent.size", "wide"));
    }

    @Test
    void defaultsMatchTheDocumentedConventions() {
        FormatJExtension extension = extensionOf(ProjectBuilder.builder().build());

        assertEquals(Preset.FORMATJ, extension.getPreset().get());
        assertEquals(Boolean.FALSE, extension.getPreviewFeatures().get());
        assertEquals(Boolean.TRUE, extension.getEnforceOnCheck().get());
    }

}
