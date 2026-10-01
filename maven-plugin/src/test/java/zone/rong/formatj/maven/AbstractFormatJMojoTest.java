package zone.rong.formatj.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import zone.rong.formatj.api.rules.IndentRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Style resolution shared by {@link FormatMojo} and {@link CheckMojo}. */
class AbstractFormatJMojoTest {

    @Test
    void discoversTheNearestStyleFileWhenNeitherPresetNorStyleFileIsSet(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());

        assertEquals(6, mojo.style().get(IndentRules.SIZE));
    }

    @Test
    void anExplicitPresetSkipsDiscovery(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("formatj.toml"), "[indent]\nsize = 6\n");
        FormatMojo mojo = new FormatMojo();
        mojo.project = new MavenProject();
        mojo.project.setFile(root.resolve("pom.xml").toFile());
        mojo.preset = "google";

        assertEquals(2, mojo.style().get(IndentRules.SIZE));
    }

}
