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
    "--exclude", "**/.*/**",
)

mapOf(
    "format" to ("--write" to "Formats this repository's Java sources with FormatJ."),
    "formatCheck" to ("--check" to "Fails if this repository's Java sources are not formatted with FormatJ."),
).forEach { (name, mode) ->
    tasks.register<JavaExec>(name) {
        group = "formatting"
        description = mode.second
        classpath = formatjCli
        mainClass.set("zone.rong.formatj.cli.Main")
        workingDir = layout.projectDirectory.asFile
        args(listOf(mode.first, ".") + formatExcludes)
    }
}

tasks.named("check") {
    dependsOn("formatCheck")
}
