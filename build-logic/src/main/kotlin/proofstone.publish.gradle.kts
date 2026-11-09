// Local-only publication (T-M6-04): `publishToMavenLocal` is the one target. Nothing here publishes to a remote repository.
// Maven Central, GHCR and staging belong to the release cards (T-M6-01, T-M6-02) and need the maintainer's approval.
// Examples consume these artefacts through mavenLocal (settings.gradle.kts, exclusiveContent), never a snapshot.
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the

plugins {
    `maven-publish`
}

val libs = the<VersionCatalogsExtension>().named("libs")

group = "io.github.h7kz.proofstone"
version = libs.findVersion("proofstone").get().requiredVersion

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // ":drivers:mcpl" becomes "proofstone-drivers-mcpl"; ":proofstone-api" stays "proofstone-api".
            artifactId = project.path.removePrefix(":").replace(":", "-")
                .let { if (it.startsWith("proofstone")) it else "proofstone-$it" }
        }
    }
}
