pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "formatj"

include("core")
include("app")
include("gradle-plugin")
include("maven-plugin")
