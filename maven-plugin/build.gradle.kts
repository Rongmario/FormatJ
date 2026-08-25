plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

description = "Formats Java sources with FormatJ, using the same rules as the CLI and Gradle plugin."

dependencies {
    implementation(project(":core"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The Maven plugin descriptor is written by hand and filtered here. The Gradle community plugin that
// generates it from annotations does not run on Gradle 9, and shelling out to Maven would defeat the
// point of a build that needs nothing but a JDK. MavenPluginDescriptorTest keeps it honest by
// checking it against the @Mojo and @Parameter annotations on every build.
tasks.test {
    useJUnitPlatform()
    systemProperty(
        "formatj.descriptor",
        layout.projectDirectory.file("src/main/resources/META-INF/maven/plugin.xml").asFile.path,
    )
    systemProperty("formatj.version", project.version.toString())
}
