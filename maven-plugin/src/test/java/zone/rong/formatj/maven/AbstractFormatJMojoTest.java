package zone.rong.formatj.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zone.rong.formatj.api.LanguageLevel;
import zone.rong.formatj.api.rules.IndentRules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Style resolution shared by {@link FormatMojo} and {@link CheckMojo}. */
class AbstractFormatJMojoTest {

    @Test
    void discoversTheNearestStyleFileWhenNeitherPresetNorStyleFileIsSet(@TempDir Path root)
        throws IOException,
        MojoExecutionException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());

        assertEquals(6, mojo.style().get(IndentRules.SIZE));
    }

    @Test
    void theLanguageLevelDefaultsToWhatTheCompilerTargets() {
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        assertEquals(LanguageLevel.LATEST, mojo.projectLanguageLevel());

        mojo.project.getProperties().setProperty("maven.compiler.source", "1.8");
        assertEquals(LanguageLevel.JAVA_8, mojo.projectLanguageLevel());

        mojo.project.getProperties().setProperty("maven.compiler.release", "17");
        assertEquals(LanguageLevel.JAVA_17, mojo.projectLanguageLevel());
    }

    @Test
    void theCompilerPluginsOwnConfigurationWinsOverTheProperty() {
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.getProperties().setProperty("maven.compiler.release", "17");

        Xpp3Dom release = new Xpp3Dom("release");
        release.setValue("11");
        Xpp3Dom configuration = new Xpp3Dom("configuration");
        configuration.addChild(release);
        Plugin compiler = new Plugin();
        compiler.setArtifactId("maven-compiler-plugin");
        compiler.setConfiguration(configuration);
        mojo.project.getBuild().addPlugin(compiler);
        assertEquals(LanguageLevel.JAVA_11, mojo.projectLanguageLevel());
    }

    @Test
    void anExplicitPresetSkipsDiscovery(@TempDir Path root) throws IOException, MojoExecutionException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.preset = "google";

        assertEquals(2, mojo.style().get(IndentRules.SIZE));
    }

    @Test
    void includesAreRelativeToTheSourceRootNotTheAbsolutePath(@TempDir Path root)
        throws IOException,
        MojoExecutionException {
        Path sourceRoot = Files.createDirectories(root.resolve("src/main/java"));
        Path kept = Files.createDirectories(sourceRoot.resolve("kept"));
        Path skipped = Files.createDirectories(sourceRoot.resolve("skipped"));
        Files.writeString(kept.resolve("Kept.java"), "");
        Files.writeString(skipped.resolve("Skipped.java"), "");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.project.addCompileSourceRoot(sourceRoot.toString());
        mojo.includeTestSources = false;
        mojo.includes = List.of("kept/**");

        assertEquals(List.of(kept.resolve("Kept.java")), mojo.sourceFiles());
    }

    @Test
    void fileSelectionFromTheStyleFileAppliesAcrossEverySourceRoot(@TempDir Path root)
        throws IOException,
        MojoExecutionException {
        Path sourceRoot = Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(sourceRoot.resolve("Kept.java"), "");
        Path generated = Files.createDirectories(sourceRoot.resolve("generated"));
        Files.writeString(generated.resolve("Skipped.java"), "");
        Files.writeString(root.resolve("formatj.toml"), "[files]\nexclude = [\"**/generated/**\"]\n");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.project.addCompileSourceRoot(sourceRoot.toString());
        mojo.includeTestSources = false;

        assertEquals(List.of(sourceRoot.resolve("Kept.java")), mojo.sourceFiles());
    }

    @Test
    void generatedSourceRootsUnderTheBuildDirectoryAreSkipped(@TempDir Path root)
        throws IOException,
        MojoExecutionException {
        Path sourceRoot = Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(sourceRoot.resolve("Kept.java"), "");
        Path generatedRoot = Files.createDirectories(root.resolve("target/generated-sources/annotations"));
        Files.writeString(generatedRoot.resolve("Generated.java"), "");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.project.getBuild().setDirectory(root.resolve("target").toString());
        mojo.project.addCompileSourceRoot(sourceRoot.toString());
        mojo.project.addCompileSourceRoot(generatedRoot.toString());
        mojo.includeTestSources = false;

        assertEquals(List.of(sourceRoot.resolve("Kept.java")), mojo.sourceFiles());
    }

    @Test
    void anUnknownPresetIsAMojoExecutionException() {
        FormatMojo mojo = new FormatMojo();
        mojo.preset = "nope";

        assertThrows(MojoExecutionException.class, mojo::style);
    }

    @Test
    void anUnknownEncodingNamesTheValue(@TempDir Path root) throws IOException {
        Path sourceRoot = Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(sourceRoot.resolve("A.java"), "");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.project.addCompileSourceRoot(sourceRoot.toString());
        mojo.includeTestSources = false;
        mojo.encoding = "no-such-charset";

        MojoExecutionException failure = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("no-such-charset"), failure.getMessage());
    }

}
