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
        // The Fabric loader and Fabric API of the fabric@26.3 row, compile only for the Fabric agent (T-M8-05).
        exclusiveContent {
            forRepository { maven("https://maven.fabricmc.net/") }
            filter { includeGroupByRegex("net\\.fabricmc(\\..*)?") }
        }
        // NeoForge, FancyModLoader, the event bus and the Dist annotations of the neoforge@26.3 row, compile only for the
        // NeoForge agent (T-M8-06), each checked by sha256 in its build. Artifact only: no POM or module metadata is read,
        // every jar is named one by one.
        exclusiveContent {
            forRepository {
                maven("https://maven.neoforged.net/releases/") { metadataSources { artifact() } }
            }
            filter {
                includeGroup("net.neoforged")
                includeGroup("net.neoforged.fancymodloader")
            }
        }
        // The vanilla server jar the Fabric agent compiles against (T-M8-05), by Mojang's content address (its sha1).
        // Never shaded, published or committed (AGENTS.md section 3).
        exclusiveContent {
            forRepository {
                ivy("https://piston-data.mojang.com/") {
                    patternLayout { artifact("v1/objects/[revision]/[module].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("com.mojang.piston-data") }
        }
        // CloudburstMC Protocol for the Bedrock driver (T-M9-05, ADR-0018 section 2). No release of the protocol groups exists on
        // Maven Central or the OpenCollab releases repository (checked 2026-11-17), only timestamped snapshot builds, so exactly these
        // groups come from the snapshots repository; every other Cloudburst group (fastutil, nbt) is a Central release. The versions
        // in the catalog are exact timestamped builds, each artefact checked by sha256 in drivers/bedrock (checkCloudburstPins).
        exclusiveContent {
            forRepository { maven("https://repo.opencollab.dev/maven-snapshots/") }
            // Only timestamped builds: MCProtocolLib's release of org.cloudburstmc.math:immutable:2.0 still comes from Central.
            filter {
                for (group in listOf("org.cloudburstmc.protocol", "org.cloudburstmc.netty", "org.cloudburstmc.math")) {
                    includeVersionByRegex(group, ".*", ".*-[0-9]{8}[.][0-9]{6}-[0-9]{1,4}")
                }
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
    "drivers:bedrock",
    "drivers:mcpl",
    "drivers:mineflayer-ref",
    "platforms:agent-core",
    "platforms:bukkit:agent",
    "platforms:bukkit:fixture",
    "platforms:bungee:agent",
    "platforms:velocity:agent",
    "platforms:fabric:agent",
    "platforms:neoforge:agent",
    "platforms:vanilla-view",
    "examples:smoke",
    "examples:loader-datapack",
    "examples:loader-mod",
    "examples:disenchantment-anvil",
    "examples:bad-tests",
    "examples:bedrock-prefix",
    "examples:gradle-paper-plugin",
)
