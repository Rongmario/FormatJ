package zone.rong.formatj.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the packaged Maven plugin against a real Maven project.
 *
 * <p>{@link MavenPluginDescriptorTest} keeps the hand-written descriptor honest. This test is the
 * slower check that Maven actually loads that descriptor, puts the plugin on its classpath, and
 * that {@code formatj:format} / {@code formatj:check} change a Java source the way a user would see.
 */
class MavenPluginFixtureTest {

    private static final String MAVEN_VERSION = System.getProperty("formatj.maven.version", "3.9.16");
    private static final String MAVEN_ARCHIVE = "apache-maven-" + MAVEN_VERSION + "-bin.tar.gz";
    private static final String MAVEN_URL = "https://archive.apache.org/dist/maven/maven-3/" + MAVEN_VERSION
            + "/binaries/" + MAVEN_ARCHIVE;

    @TempDir
    Path temp;

    @Test
    void formatGoalRewritesAndCheckThenPasses() throws Exception {
        String version = requiredProperty("formatj.version");
        Path pluginJar = Path.of(requiredProperty("formatj.maven.plugin.jar"));
        Path coreJar = Path.of(requiredProperty("formatj.core.jar"));
        assertTrue(Files.isRegularFile(pluginJar), () -> "missing plugin jar: " + pluginJar);
        assertTrue(Files.isRegularFile(coreJar), () -> "missing core jar: " + coreJar);

        Path fixture = temp.resolve("fixture");
        copyFixture(fixture);
        replaceVersion(fixture.resolve("pom.xml"), version);
        Path sample = fixture.resolve("src/main/java/sample/Sample.java");
        String unformatted = Files.readString(sample, StandardCharsets.UTF_8);
        assertTrue(unformatted.contains("int x=1;"), unformatted);

        Path localRepo = temp.resolve("repo");
        installArtifact(localRepo, "zone.rong.formatj", "formatj", version, coreJar, corePom(version));
        installArtifact(localRepo, "zone.rong.formatj", "formatj-maven-plugin", version, pluginJar, pluginPom(version));

        Path mavenHome = mavenHome();
        Run mavenVersion =
                runProcess(
                        List.of(mavenHome.resolve("bin/mvn").toString(), "--version"),
                        fixture,
                        Duration.ofMinutes(1));
        assertEquals(0, mavenVersion.exitCode, mavenVersion.out + mavenVersion.err);
        assertTrue(
                mavenVersion.out.contains("Apache Maven " + MAVEN_VERSION),
                () -> "expected Maven " + MAVEN_VERSION + ":\n" + mavenVersion.out + mavenVersion.err);
        String plugin = "zone.rong.formatj:formatj-maven-plugin:" + version;
        Run checkDirty = maven(mavenHome, localRepo, fixture, version, plugin + ":check");
        assertNotEquals(0, checkDirty.exitCode, checkDirty.out + checkDirty.err);
        assertTrue(
                (checkDirty.out + checkDirty.err).toLowerCase().contains("not formatted")
                        || (checkDirty.out + checkDirty.err).contains("Sample.java"),
                checkDirty.out + checkDirty.err);

        Run format = maven(mavenHome, localRepo, fixture, version, plugin + ":format");
        assertEquals(0, format.exitCode, format.out + format.err);
        String formatted = Files.readString(sample, StandardCharsets.UTF_8);
        assertTrue(formatted.contains("int x = 1;"), formatted);
        assertTrue(formatted.contains("class Sample"), formatted);

        Run checkClean = maven(mavenHome, localRepo, fixture, version, plugin + ":check");
        assertEquals(0, checkClean.exitCode, checkClean.out + checkClean.err);
        assertEquals(formatted, Files.readString(sample, StandardCharsets.UTF_8));
    }

    private static Run maven(Path mavenHome, Path localRepo, Path fixture, String version, String goal)
            throws Exception {
        Path mvn = mavenHome.resolve("bin/mvn");
        assertTrue(Files.isRegularFile(mvn), () -> "missing mvn: " + mvn);
        List<String> command = new ArrayList<>();
        command.add(mvn.toString());
        command.add("-B");
        command.add("-e");
        command.add("-Dformatj.version=" + version);
        command.add("-Dmaven.repo.local=" + localRepo);
        command.add(goal);
        return runProcess(command, fixture, Duration.ofMinutes(5));
    }

    private static Path mavenHome() throws Exception {
        Path cache = Path.of(System.getProperty("formatj.maven.cache", "build/maven-dist"));
        Path unpacked = cache.resolve("apache-maven-" + MAVEN_VERSION);
        if (Files.isRegularFile(unpacked.resolve("bin/mvn"))) {
            return unpacked;
        }
        Files.createDirectories(cache);
        Path archive = cache.resolve(MAVEN_ARCHIVE);
        if (!Files.isRegularFile(archive) || Files.size(archive) == 0) {
            download(URI.create(MAVEN_URL), archive);
        }
        Run unpack =
                runProcess(
                        List.of("tar", "-xzf", archive.toString(), "-C", cache.toString()),
                        cache,
                        Duration.ofMinutes(2));
        if (unpack.exitCode != 0) {
            Files.deleteIfExists(archive);
            throw new IOException("failed to unpack Maven from " + archive + "\n" + unpack.out + unpack.err);
        }
        assertTrue(Files.isRegularFile(unpacked.resolve("bin/mvn")), () -> "unpacked Maven missing: " + unpacked);
        unpacked.resolve("bin/mvn").toFile().setExecutable(true);
        return unpacked;
    }

    private static void download(URI uri, Path destination) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(destination);
            throw new IOException("download failed: " + uri + " -> HTTP " + response.statusCode());
        }
    }

    private static void installArtifact(Path repo, String group, String artifact, String version, Path jar, String pom)
            throws IOException {
        Path directory = repo;
        for (String part : group.split("\\.")) {
            directory = directory.resolve(part);
        }
        directory = directory.resolve(artifact).resolve(version);
        Files.createDirectories(directory);
        Files.copy(jar, directory.resolve(artifact + "-" + version + ".jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(directory.resolve(artifact + "-" + version + ".pom"), pom, StandardCharsets.UTF_8);
        Files.writeString(
                directory.getParent().resolve("maven-metadata-local.xml"),
                metadata(group, artifact, version),
                StandardCharsets.UTF_8);
    }

    private static String corePom(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>zone.rong.formatj</groupId>
                  <artifactId>formatj</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(
                version);
    }

    private static String pluginPom(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>zone.rong.formatj</groupId>
                  <artifactId>formatj-maven-plugin</artifactId>
                  <version>%s</version>
                  <packaging>maven-plugin</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>zone.rong.formatj</groupId>
                      <artifactId>formatj</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </project>
                """.formatted(
                version,
                version);
    }

    private static String metadata(String group, String artifact, String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <release>%s</release>
                    <versions><version>%s</version></versions>
                  </versioning>
                </metadata>
                """.formatted(
                group,
                artifact,
                version,
                version);
    }

    private static void copyFixture(Path destination) throws IOException {
        Path source = Path.of("src/test/resources/maven-fixture");
        assertTrue(Files.isDirectory(source), () -> "missing fixture: " + source.toAbsolutePath());
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = destination.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
            }
        }
    }

    private static void replaceVersion(Path pom, String version) throws IOException {
        String text = Files.readString(pom, StandardCharsets.UTF_8);
        Files.writeString(pom, text.replace("FORMATJ_VERSION", version), StandardCharsets.UTF_8);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("missing system property " + name);
        }
        return value;
    }

    private static Run runProcess(List<String> command, Path directory, Duration timeout) throws Exception {
        Path outFile = Files.createTempFile("formatj-maven-", ".out");
        Path errFile = Files.createTempFile("formatj-maven-", ".err");
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.redirectOutput(outFile.toFile());
            builder.redirectError(errFile.toFile());
            Process process = builder.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new IOException(
                        "process timed out: " + command + "\n" + Files.readString(outFile) + Files.readString(errFile));
            }
            return new Run(
                    process.exitValue(),
                    Files.readString(outFile, StandardCharsets.UTF_8),
                    Files.readString(errFile, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(outFile);
            Files.deleteIfExists(errFile);
        }
    }

    private record Run(int exitCode, String out, String err) { }

}
