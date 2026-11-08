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
        // Spigot API 1.8.8 is published only as a snapshot here (T-M1-03). The version is a frozen timestamped build.
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

rootProject.name = "proofstone"

include(
    "proofstone-api",
    "proofstone-core",
    "proofstone-protocol",
    "conformance",
    "drivers:mcpl",
    "drivers:mineflayer-ref",
    "platforms:bukkit:agent",
    "platforms:bukkit:fixture",
    "examples:smoke",
    "examples:disenchantment-anvil",
    "examples:bad-tests",
    "examples:bedrock-prefix",
)
