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

tasks.register<JavaExec>("format") {
    group = "formatting"
    description = "Formats this repository's Java sources with FormatJ."
    classpath = formatjCli
    mainClass.set("zone.rong.formatj.cli.Main")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    workingDir = layout.projectDirectory.asFile
    // Walk the repo, but skip generated output and the golden-test fixtures.
    args(
        "--write",
        ".",
        "--exclude", "**/build/**",
        "--exclude", "**/src/test/resources/**",
    )
}
