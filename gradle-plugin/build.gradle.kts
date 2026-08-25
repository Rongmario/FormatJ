plugins {
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

gradlePlugin {
    plugins {
        create("formatj") {
            id = "zone.rong.formatj"
            implementationClass = "zone.rong.formatj.gradle.FormatJPlugin"
            displayName = "FormatJ"
            description = "Formats Java sources with FormatJ, using the same rules as the CLI and Maven plugin."
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
