import org.gradle.api.GradleException
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    base
    `jvm-toolchains`
    alias(libs.plugins.cleanroom.versioning)
}

allprojects {
    group = "zone.rong.formatj"
}

subprojects {
    version = rootProject.version
    plugins.withType<JavaPlugin>().configureEach {
        tasks.withType<JavaCompile>().configureEach {
            // Published artifacts target Java 21. Test sources compile to the JDK running the build
            // so the floor-and-current CI matrix can exercise both.
            if (!name.contains("Test", ignoreCase = true)) {
                options.release.set(21)
            }
        }
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
}

val formatjCli = configurations.create("formatjCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    formatjCli(project(":app"))
}

val formatExcludes = listOf(
    "--exclude", "**/build/**",
    "--exclude", "**/src/test/resources/**",
)

tasks.register<JavaExec>("format") {
    group = "formatting"
    description = "Formats this repository's Java sources with FormatJ."
    classpath = formatjCli
    mainClass.set("zone.rong.formatj.cli.Main")
    workingDir = layout.projectDirectory.asFile
    args(listOf("--write", ".") + formatExcludes)
}

tasks.register<JavaExec>("formatCheck") {
    group = "formatting"
    description = "Fails if this repository's Java sources are not formatted with FormatJ."
    classpath = formatjCli
    mainClass.set("zone.rong.formatj.cli.Main")
    workingDir = layout.projectDirectory.asFile
    args(listOf("--check", ".") + formatExcludes)
}

tasks.register("verifyArtifacts") {
    group = "verification"
    description = "Fails if published CLI and plugin distribution artifacts are missing or empty."
    dependsOn(
        ":app:distZip",
        ":app:distTar",
        ":app:installDist",
        ":core:jar",
        ":gradle-plugin:jar",
        ":maven-plugin:jar",
    )
    val rootDir = layout.projectDirectory
    doLast {
        data class Check(val directory: String, val match: (java.io.File) -> Boolean)

        val checks = listOf(
            Check("app/build/distributions") {
                it.name.startsWith("formatj-") && it.name.endsWith(".zip")
            },
            Check("app/build/distributions") {
                it.name.startsWith("formatj-") && it.name.endsWith(".tar")
            },
            Check("app/build/install/formatj/bin") { it.name == "formatj" },
            Check("core/build/libs") {
                it.name.startsWith("formatj-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
            Check("gradle-plugin/build/libs") {
                it.name.startsWith("formatj-gradle-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
            Check("maven-plugin/build/libs") {
                it.name.startsWith("formatj-maven-plugin-")
                    && it.name.endsWith(".jar")
                    && !it.name.contains("-sources")
                    && !it.name.contains("-javadoc")
            },
        )
        val missing = checks.filter { check ->
            val folder = rootDir.dir(check.directory).asFile
            folder.listFiles().orEmpty().none { it.isFile && it.length() > 0L && check.match(it) }
        }.map { it.directory }
        if (missing.isNotEmpty()) {
            throw GradleException("empty or missing distribution artifacts in: $missing")
        }
    }
}

tasks.named("check") {
    dependsOn("formatCheck", "verifyArtifacts")
}
