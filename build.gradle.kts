import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication
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
    val sub = this
    version = rootProject.version
    plugins.withType<JavaPlugin>().configureEach {
        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }
    }
    // core and the Maven plugin: java-library, published to maven.cleanroommc.com
    plugins.withId("java-library") {
        sub.pluginManager.apply("maven-publish")
        sub.extensions.getByType<JavaPluginExtension>().withJavadocJar()
        val props = sub.providers
        sub.extensions.configure<PublishingExtension> {
            publications.create<MavenPublication>("maven") {
                from(sub.components["java"])
                artifactId = props.gradleProperty("POM_ARTIFACT_ID").get()
                pom {
                    name.set(props.gradleProperty("POM_NAME"))
                    description.set(props.gradleProperty("POM_DESCRIPTION"))
                    url.set(props.gradleProperty("POM_URL"))
                    inceptionYear.set(props.gradleProperty("POM_INCEPTION_YEAR"))
                    licenses {
                        license {
                            name.set(props.gradleProperty("POM_LICENSE_NAME"))
                            url.set(props.gradleProperty("POM_LICENSE_URL"))
                            distribution.set(props.gradleProperty("POM_LICENSE_DIST"))
                        }
                    }
                    developers {
                        developer {
                            id.set(props.gradleProperty("POM_DEVELOPER_ID"))
                            name.set(props.gradleProperty("POM_DEVELOPER_NAME"))
                            url.set(props.gradleProperty("POM_DEVELOPER_URL"))
                        }
                    }
                    scm {
                        url.set(props.gradleProperty("POM_SCM_URL"))
                        connection.set(props.gradleProperty("POM_SCM_CONNECTION"))
                        developerConnection.set(props.gradleProperty("POM_SCM_DEV_CONNECTION"))
                    }
                }
            }
            repositories {
                maven {
                    name = "CleanroomMaven"
                    url = uri("https://maven.cleanroommc.com")
                    credentials(PasswordCredentials::class)
                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            }
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
