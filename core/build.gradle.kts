import org.gradle.api.artifacts.type.ArtifactTypeDefinition

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

val apiSources: SourceSet = sourceSets.create("apiSources")

sourceSets {
    main {
        compileClasspath += apiSources.output
        runtimeClasspath += apiSources.output
    }
    test {
        compileClasspath += apiSources.output
        runtimeClasspath += apiSources.output
    }
}

// The public, dependency-free API lives in src/api/java and is published inside the core jar.
apiSources.java.setSrcDirs(listOf("src/api/java"))

tasks.jar {
    from(apiSources.output)
}

tasks.named<Jar>("sourcesJar") {
    from(apiSources.allJava)
}

// Consumers resolve a project dependency to its class directories, not its jar, so the api source
// set's output has to be published into both outgoing variants or dependants see only src/main.
val apiClasses = tasks.named<JavaCompile>("compileApiSourcesJava").flatMap { it.destinationDirectory }

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.variants.named("classes") {
            artifact(apiClasses) {
                type = ArtifactTypeDefinition.JVM_CLASS_DIRECTORY
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
