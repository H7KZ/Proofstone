// Gradle monorepo layout: docs/architecture.md §4, ADR-0009.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // MCProtocolLib is published only here (ADR-0004).
        maven("https://repo.opencollab.dev/main/")
    }
}

rootProject.name = "proofstone"

include(
    "proofstone-api",
    "proofstone-core",
    "proofstone-protocol",
    "conformance",
)
