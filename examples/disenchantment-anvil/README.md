# examples:disenchantment-anvil

## Purpose

A real third-party plugin scenario: `AnvilDisenchantE2E` puts a sword (Sharpness V, Unbreaking III) and a book into an
anvil on the newest Paper row, with the released Disenchantment plugin installed. Both oracles check the enchanted book
and the stripped sword, an event probe reads `PostDisenchantEvent.xpCost` through the agent, and the experience level
must equal `30 - xpCost`. It is the proof of the M2 exit criterion.

## Boundaries

- Not part of `check`: its tests boot a real server, so they need Docker.
- The Disenchantment jar is downloaded into `build/disenchantment/` by `downloadDisenchantment`, pinned by release
  and SHA-256 in `build.gradle.kts`. It is never committed. A checksum mismatch fails the build.
- Only the anvil scenario lives here. The other Disenchantment scenarios belong to the M6 dogfood card.

## Public surface

None. The test class is the example to copy.

## How to test

Read <https://aka.ms/MinecraftEULA> first. The harness never accepts the EULA for you.

```bash
PROOFSTONE_ACCEPT_EULA=true ./gradlew :examples:disenchantment-anvil:test
```

Needs Docker, a Java 21+ toolchain and network access to download the plugin on the first run. Without the EULA
variable or without Docker the test is skipped with that reason. A skip is not a pass.
