import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
}

apply(from = rootProject.file("gradle/cleanroom-publishing.gradle.kts"))

java {
    withSourcesJar()
}

base {
    archivesName.set("formatj-maven-plugin")
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
val projectVersion = version.toString()
tasks.processResources {
    val tokens = mapOf("version" to projectVersion)
    inputs.property("version", projectVersion)
    filesMatching("META-INF/maven/plugin.xml") {
        filter(mapOf("tokens" to tokens), ReplaceTokens::class.java)
    }
}

tasks.test {
    useJUnitPlatform()
    val mavenVersion = providers.gradleProperty("formatj.maven.test.version").orElse("3.9.16")
    val pluginJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val coreJar = project(":core").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    dependsOn(tasks.named("jar"), project(":core").tasks.named("jar"))
    inputs.files(pluginJar, coreJar)
    systemProperty(
        "formatj.descriptor",
        layout.buildDirectory.file("resources/main/META-INF/maven/plugin.xml").get().asFile.absolutePath,
    )
    systemProperty("formatj.version", projectVersion)
    systemProperty("formatj.maven.version", mavenVersion.get())
    systemProperty("formatj.maven.plugin.jar", pluginJar.get().asFile.absolutePath)
    systemProperty("formatj.core.jar", coreJar.get().asFile.absolutePath)
    systemProperty(
        "formatj.maven.cache",
        layout.buildDirectory.dir("maven-dist").get().asFile.absolutePath,
    )
    inputs.property("formatj.maven.version", mavenVersion)
}
