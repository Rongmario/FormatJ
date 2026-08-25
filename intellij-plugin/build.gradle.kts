import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    alias(libs.plugins.intellij.platform)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":core"))
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // The sandbox's JUnit 5 session listener still loads junit.framework.TestCase
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "zone.rong.formatj"
        name = "FormatJ"
        version = provider { project.version.toString() }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
