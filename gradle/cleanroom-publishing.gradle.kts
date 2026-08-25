import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication

pluginManager.apply("maven-publish")
extensions.getByType<JavaPluginExtension>().withJavadocJar()

fun pom(name: String): String =
    requireNotNull(findProperty(name)?.toString()) { "missing property $name" }

extensions.configure<PublishingExtension> {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        artifactId = pom("POM_ARTIFACT_ID")
        pom {
            name.set(pom("POM_NAME"))
            description.set(pom("POM_DESCRIPTION"))
            url.set(pom("POM_URL"))
            inceptionYear.set(pom("POM_INCEPTION_YEAR"))
            developers {
                developer {
                    id.set(pom("POM_DEVELOPER_ID"))
                    name.set(pom("POM_DEVELOPER_NAME"))
                    url.set(pom("POM_DEVELOPER_URL"))
                }
            }
            scm {
                url.set(pom("POM_SCM_URL"))
                connection.set(pom("POM_SCM_CONNECTION"))
                developerConnection.set(pom("POM_SCM_DEV_CONNECTION"))
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
