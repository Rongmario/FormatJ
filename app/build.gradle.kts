plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    applicationName = "formatj"
    mainClass = "zone.rong.formatj.cli.Main"
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "formatj",
            "Implementation-Version" to version.toString(),
        )
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
