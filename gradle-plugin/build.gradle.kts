import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
    signing
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("formatj-gradle")
}

gradlePlugin {
    website = "https://github.com/Rongmario/FormatJ"
    vcsUrl = "https://github.com/Rongmario/FormatJ.git"
    plugins {
        create("formatj") {
            id = "zone.rong.formatj"
            implementationClass = "zone.rong.formatj.gradle.FormatJPlugin"
            displayName = "FormatJ"
            description = "Formats Java sources with FormatJ, using the same rules as the CLI and Maven plugin."
            tags = listOf("java", "formatter", "format", "style")
        }
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(gradleTestKit())
}

val coreJar = project(":core").tasks.named<Jar>("jar")
tasks.named<Jar>("jar") {
    from(coreJar.map { zipTree(it.archiveFile.get()) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(project(":core").sourceSets.named("main").map { it.allJava })
    from(project(":core").sourceSets.named("apiSources").map { it.allJava })
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "formatj-gradle"
        }
        pom {
            name.set("FormatJ Gradle Plugin")
            description.set("Formats Java sources with FormatJ, using the same rules as the CLI and Maven plugin.")
            url.set("https://github.com/Rongmario/FormatJ")
            developers {
                developer {
                    id.set("Rongmario")
                    name.set("Rongmario")
                    url.set("https://github.com/Rongmario")
                }
            }
            scm {
                url.set("https://github.com/Rongmario/FormatJ")
                connection.set("scm:git:git://github.com/Rongmario/FormatJ.git")
                developerConnection.set("scm:git:ssh://git@github.com/Rongmario/FormatJ.git")
            }
        }
        pom.withXml {
            val dependenciesNodes = asNode().get("dependencies") as NodeList
            if (dependenciesNodes.isEmpty()) {
                return@withXml
            }
            val dependencies = dependenciesNodes[0] as Node
            val embedded = dependencies.children().filterIsInstance<Node>().filter { dep ->
                val artifactId = (dep.get("artifactId") as NodeList).text()
                artifactId == "core" || artifactId == "formatj"
            }
            embedded.forEach { dependencies.remove(it) }
        }
    }
}

val signingKey = providers.gradleProperty("signingKey")
val signingPassword = providers.gradleProperty("signingPassword")
signing {
    setRequired({
        gradle.taskGraph.allTasks.any { it.name == "publishPlugins" }
    })
    if (signingKey.isPresent && signingPassword.isPresent) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
    }
}

tasks.test {
    useJUnitPlatform()
    val testVersion = providers.gradleProperty("formatj.gradle.test.version").orElse("")
    systemProperty("formatj.gradle.version", testVersion.get())
    inputs.property("formatj.gradle.version", testVersion)
}
