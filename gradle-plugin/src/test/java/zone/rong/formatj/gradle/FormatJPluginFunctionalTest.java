package zone.rong.formatj.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatJPluginFunctionalTest {

    @TempDir
    Path projectDirectory;

    @BeforeEach
    void writeProject() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle.kts"), "rootProject.name = \"fixture\"\n");
        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("zone.rong.formatj")
                }

                formatJ {
                    rule("indent.size", 4)
                    sourceSets("main")
                }
                """);
        Path source = Files.createDirectories(projectDirectory.resolve("src/main/java/sample"));
        Files.writeString(
                source.resolve("Sample.java"),
                """
                package sample;

                class Sample {
                    void run() {}
                }
                """);
    }

    private BuildResult run(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .build();
    }

    @Test
    void checkTaskRunsAndIsUpToDateOnASecondInvocation() {
        BuildResult first = run("formatJavaCheck");
        assertEquals(TaskOutcome.SUCCESS, first.task(":formatJavaCheck").getOutcome());

        BuildResult second = run("formatJavaCheck");
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":formatJavaCheck").getOutcome());
    }

    @Test
    void applyTaskLeavesAlreadyFormattedSourcesAlone() throws IOException {
        Path source = projectDirectory.resolve("src/main/java/sample/Sample.java");
        String before = Files.readString(source);

        BuildResult result = run("formatJavaApply");

        assertEquals(TaskOutcome.SUCCESS, result.task(":formatJavaApply").getOutcome());
        assertEquals(before, Files.readString(source));
    }

    @Test
    void checkTaskIsWiredIntoTheLifecycleCheckTask() {
        BuildResult result = run("check");
        assertTrue(result.getOutput().contains("formatJavaCheck"), result.getOutput());
    }

    @Test
    void tasksAreListedUnderTheFormattingGroup() {
        BuildResult result = run("tasks", "--group", "formatting");
        assertTrue(result.getOutput().contains("formatJavaApply"), result.getOutput());
        assertTrue(result.getOutput().contains("formatJavaCheck"), result.getOutput());
    }

    @Test
    void changingARuleInvalidatesTheTaskAndTheNewRuleApplies() throws IOException {
        assertEquals(TaskOutcome.SUCCESS, run("formatJavaCheck").task(":formatJavaCheck").getOutcome());

        Files.writeString(
                projectDirectory.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("zone.rong.formatj")
                }

                formatJ {
                    rule("indent.size", 2)
                    sourceSets("main")
                }
                """);

        // The four-space fixture no longer matches, so the task must run again and fail.
        BuildResult failure = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("formatJavaCheck")
                .buildAndFail();
        assertEquals(TaskOutcome.FAILED, failure.task(":formatJavaCheck").getOutcome());
        assertTrue(failure.getOutput().contains("not formatted"), failure.getOutput());

        assertEquals(TaskOutcome.SUCCESS, run("formatJavaApply").task(":formatJavaApply").getOutcome());
        assertTrue(
                Files.readString(projectDirectory.resolve("src/main/java/sample/Sample.java"))
                        .contains("\n  void run()"));
        assertEquals(TaskOutcome.SUCCESS, run("formatJavaCheck").task(":formatJavaCheck").getOutcome());
    }

}
