import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.GradleException

plugins {
    `java-library`
}

apply(from = rootProject.file("gradle/cleanroom-publishing.gradle.kts"))

java {
    withSourcesJar()
}

base {
    archivesName.set("formatj")
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
    manifest {
        attributes("Automatic-Module-Name" to "zone.rong.formatj")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(apiSources.allJava)
}

tasks.javadoc {
    source(apiSources.allJava)
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
    exclude("**/ExternalCorpusInvariantTest.class")
    exclude("**/LanguageLevelCompileTest.class")
}

val languageLevelTest = tasks.register<Test>("languageLevelTest") {
    group = "verification"
    description = "Compiles what the gated rewrite rules write with the JDK of each release they are gated at."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/LanguageLevelCompileTest.class")
    listOf(8, 9, 13, 14, 15).forEach { release ->
        val javac = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(release) }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Dformatj.javac.$release=${javac.get().executablePath}")
        })
    }
}

tasks.check {
    dependsOn(languageLevelTest)
}

tasks.register<Test>("externalCorpusTest") {
    group = "verification"
    description = "Runs formatter invariants over an external Java source tree."
    val corpus = providers.gradleProperty("formatj.external.corpus")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/ExternalCorpusInvariantTest.class")
    inputs.dir(corpus).optional()
    doFirst {
        if (!corpus.isPresent) {
            throw GradleException("-Pformatj.external.corpus must name a Java source tree")
        }
        systemProperty("formatj.external.corpus", corpus.get())
    }
}

tasks.register<JavaExec>("benchmarkStages") {
    group = "verification"
    description = "Times lex, parse, verify, and layout over this repository's Java sources."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("zone.rong.formatj.core.pipeline.StageTimer")
    workingDir = rootProject.layout.projectDirectory.asFile
    args(".", "**/build/**", "**/src/test/resources/**")
}
