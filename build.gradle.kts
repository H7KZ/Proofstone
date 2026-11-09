// Root project has no sources. Gate tasks (check, docsCheck) come from build-logic.
plugins {
    id("proofstone.gates")
}

// Local release staging (T-M6-01, docs/release.md). Stages every published module into build/central-staging, no upload.
// Called by scripts/release-maven.sh. Module tasks are resolved lazily, after all projects are configured.
tasks.register("centralStaging") {
    group = "publishing"
    description = "Stages every Proofstone artefact in build/central-staging. Uploads nothing."
    dependsOn(java.util.concurrent.Callable {
        subprojects.filter { it.plugins.hasPlugin("proofstone.publish") }
            .map { "${it.path}:publishAllPublicationsToCentralStagingRepository" }
    })
}

// The release script passes -PproofstoneRequireSigning=true for --publish. A real release must never run unsigned.
if (providers.gradleProperty("proofstoneRequireSigning").orNull == "true") {
    val hasSigningKey = providers.gradleProperty("signingKey").orNull?.isNotBlank() == true
    gradle.taskGraph.whenReady {
        if (!hasSigningKey) {
            throw GradleException("proofstoneRequireSigning=true but ORG_GRADLE_PROJECT_signingKey is not set. Refusing an unsigned release.")
        }
    }
}
