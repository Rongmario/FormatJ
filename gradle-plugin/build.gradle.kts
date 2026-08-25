plugins {
    `java-gradle-plugin`
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

tasks.test {
    useJUnitPlatform()
}
