# ADR-0009: Gradle monorepo; artefacts usable from Maven and Gradle

- Status: Accepted
- Date: 2026-11-03

## Context
- Mod agents (Fabric Loom, NeoForge ModDevGradle, Stonecutter for multi-version builds) need Gradle.
- Bukkit, Velocity and core modules could use either build tool.
- One build tool across the repository is simpler for both humans and AI agents.

## Decision
- A single Gradle (Kotlin DSL) monorepo, with:
  - a version catalog;
  - convention plugins in `build-logic/`;
  - configuration cache on.
- Artefacts are published to Maven Central, so Maven users consume them normally.
- A thin `testificate-maven-plugin` (optional) and a `testificate-gradle-plugin` provide integrations:
  - the jar under test;
  - an e2e source set or failsafe profile;
  - report wiring.
- Non-JVM parts (the Mineflayer reference image, the BDS script agent) are Gradle subprojects that call
  `npm` through a pinned Node toolchain plugin. They are built only when their pack is enabled.

## Consequences
- Maven-based plugins such as Disenchantment are first-class consumers, verified by
  `examples/maven-paper-plugin`.

## Alternatives considered
- **Maven monorepo:** mod toolchains are Gradle-only. Rejected.
- **Polyrepo:** cross-repo changes to the wire protocol become painful. Rejected for now. Community packs
  may live in their own repositories and depend on the published SPI.
