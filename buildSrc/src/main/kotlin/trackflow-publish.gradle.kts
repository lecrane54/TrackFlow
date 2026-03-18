/**
 * Convention plugin that configures Maven Central publishing and GPG signing
 * for all TrackFlow Android library modules.
 *
 * Apply via:  `id("trackflow-publish")` in the module's build.gradle.kts
 *
 * Required environment variables (set in CI secrets):
 *   OSSRH_USERNAME       – Sonatype OSSRH / Maven Central portal username
 *   OSSRH_PASSWORD       – Sonatype OSSRH / Maven Central portal password
 *   GPG_SIGNING_KEY      – ASCII-armored GPG private key (export via: gpg --armor --export-secret-keys <KEY_ID>)
 *   GPG_SIGNING_PASSWORD – Passphrase for the GPG key
 *
 * Required gradle.properties:
 *   trackflow.version    – SDK version (e.g., 1.0.0)
 *   trackflow.groupId    – Maven group ID (e.g., com.trackflow)
 */

plugins {
    `maven-publish`
    signing
}

group = findProperty("trackflow.groupId") as String
version = findProperty("trackflow.version") as String

// Determine artifact ID from the Gradle project name
// e.g. ":trackflow-core" -> "trackflow-core"
val artifactId = project.name

afterEvaluate {
    publishing {
        publications {
            // Android library modules produce an AAR via the "release" component
            // KMP modules produce publications automatically via the multiplatform plugin
            val hasAndroidComponent = components.findByName("release") != null
            val hasKmpPublications = publications.any { it.name != "maven" }

            if (hasAndroidComponent && !hasKmpPublications) {
                create<MavenPublication>("maven") {
                    from(components["release"])

                    groupId = project.group.toString()
                    this.artifactId = artifactId
                    this.version = project.version.toString()

                    configurePom()
                }
            }

            // For all publications (including KMP-generated ones), ensure POM is configured
            publications.withType<MavenPublication>().configureEach {
                configurePom()
            }
        }

        repositories {
            maven {
                name = "MavenCentral"
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = System.getenv("OSSRH_USERNAME") ?: ""
                    password = System.getenv("OSSRH_PASSWORD") ?: ""
                }
            }
        }
    }

    signing {
        val signingKey = System.getenv("GPG_SIGNING_KEY")
        val signingPassword = System.getenv("GPG_SIGNING_PASSWORD")
        if (!signingKey.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(publishing.publications)
        }
    }
}

fun MavenPublication.configurePom() {
    pom {
        name.set(artifactId)
        description.set("TrackFlow Analytics SDK – $artifactId")
        url.set("https://github.com/lecrane54/TrackFlow")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }

        developers {
            developer {
                id.set("lecrane54")
                name.set("Kyle Lefebvre")
                url.set("https://github.com/lecrane54")
            }
        }

        scm {
            url.set("https://github.com/lecrane54/TrackFlow")
            connection.set("scm:git:git://github.com/lecrane54/TrackFlow.git")
            developerConnection.set("scm:git:ssh://github.com/lecrane54/TrackFlow.git")
        }
    }
}
