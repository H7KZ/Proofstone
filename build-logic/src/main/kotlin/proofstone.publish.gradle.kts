// Publication of Proofstone's artefacts (T-M6-04, T-M6-01, ADR-0012). Local by default: `publishToMavenLocal` and the
// `centralStaging` file repository (build/central-staging). Nothing here uploads anywhere. The upload is
// scripts/release-maven.sh --publish, run by the maintainer by hand (docs/release.md).
// Signing keys come only from the environment (ORG_GRADLE_PROJECT_signingKey, ORG_GRADLE_PROJECT_signingPassword).
// Without a key, signing is skipped with a warning. The root build fails instead when -PproofstoneRequireSigning=true.
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.kotlin.dsl.the

plugins {
    `java-library`
    `maven-publish`
    signing
}

val libs = the<VersionCatalogsExtension>().named("libs")

// The licence is the maintainer's decision (ADR-0011, still Proposed). While this is null the POM carries a TODO, and
// scripts/release-maven.sh refuses --publish. Set it only together with ADR-0011 becoming Accepted.
val decidedLicence: Pair<String, String>? = null

group = "io.github.h7kz.proofstone"
version = libs.findVersion("proofstone").get().requiredVersion

java {
    withSourcesJar()
    withJavadocJar()
}

// Sources are UTF-8 (java-host sets it for javac). Javadoc otherwise uses the platform charset, which breaks on Windows.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).encoding = "UTF-8"
}

val signingKey: String? = providers.gradleProperty("signingKey").orNull?.takeIf { it.isNotBlank() }
val signingPassword: String? = providers.gradleProperty("signingPassword").orNull

if (signingKey == null) {
    logger.warn(
        "proofstone: signing SKIPPED for ${project.path}: ORG_GRADLE_PROJECT_signingKey is not set. " +
            "Fine for publishToMavenLocal and the dry run; scripts/release-maven.sh --publish refuses without it.",
    )
} else {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}

publishing {
    repositories {
        // Local file repository for the dry run and for the bundle that --publish uploads. Shared by all modules.
        maven {
            name = "centralStaging"
            url = rootProject.file("build/central-staging").toURI()
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // proofstone-protocol exposes its test fixtures as Gradle variants. They are not part of the Maven POM by design.
            suppressPomMetadataWarningsFor("testFixturesApiElements")
            suppressPomMetadataWarningsFor("testFixturesRuntimeElements")
            // ":drivers:mcpl" becomes "proofstone-drivers-mcpl"; ":proofstone-api" stays "proofstone-api".
            artifactId = project.path.removePrefix(":").replace(":", "-")
                .let { if (it.startsWith("proofstone")) it else "proofstone-$it" }
            pom {
                name.set(artifactId)
                url.set("https://github.com/H7KZ/Proofstone")
                licenses {
                    license {
                        // The literal "TODO" is intentional: scripts/release-maven.sh greps the POM for it and refuses to publish.
                        name.set(decidedLicence?.first ?: "TODO: licence not decided (ADR-0011 is Proposed)")
                        url.set(decidedLicence?.second ?: "TODO")
                    }
                }
                developers {
                    developer {
                        id.set("H7KZ")
                        name.set("H7KZ")
                        url.set("https://github.com/H7KZ")
                    }
                }
                scm {
                    url.set("https://github.com/H7KZ/Proofstone")
                    connection.set("scm:git:https://github.com/H7KZ/Proofstone.git")
                    developerConnection.set("scm:git:ssh://git@github.com/H7KZ/Proofstone.git")
                }
            }
        }
    }
}

if (signingKey != null) {
    signing {
        sign(publishing.publications["maven"])
    }
}

// The module sets `description` after this plugin is applied, so the POM reads it once the project is evaluated.
afterEvaluate {
    val moduleDescription = description
        ?: throw GradleException("${path} applies proofstone.publish but sets no `description`.")
    publishing.publications.named<MavenPublication>("maven") {
        pom.description.set(moduleDescription)
    }
}
