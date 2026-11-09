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
        // Proofstone's own artefacts, published by `publishToMavenLocal` (T-M6-04). Only this group may come from here,
        // so a local build never shadows a third-party artefact.
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroup("io.github.h7kz.proofstone") }
        }
        // Paper's API for the example plugin (T-M6-04), Velocity's API for the Velocity agent (T-M7-05) and Mojang's
        // brigadier, which Velocity's command types name. Stable releases pinned in the catalog; only these groups come from here.
        exclusiveContent {
            forRepository { maven("https://repo.papermc.io/repository/maven-public/") }
            filter {
                includeGroup("io.papermc.paper")
                includeGroup("com.velocitypowered")
                includeGroup("com.mojang")
            }
        }
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
    "platforms:agent-core",
    "platforms:bukkit:agent",
    "platforms:bukkit:fixture",
    "platforms:bungee:agent",
    "platforms:velocity:agent",
    "examples:smoke",
    "examples:disenchantment-anvil",
    "examples:bad-tests",
    "examples:bedrock-prefix",
    "examples:gradle-paper-plugin",
)
