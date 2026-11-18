# Compatibility and deprecation policy

What you can rely on when you depend on Proofstone. Plugin projects pin a Proofstone version, so these rules decide how risky an
upgrade is.

## Versioning

Proofstone follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

- **0.x (now).** Semver allows anything to change before `1.0.0`. We narrow that promise: a **minor** (`0.2.0`) may break the public
  API, a **patch** (`0.1.1`) never does. Every break is announced in the [changelog](../CHANGELOG.md) under "Breaking changes", with the
  migration step.
- **1.0.0 and later.** Breaking changes only in a major. Minor releases add. Patch releases fix.

## What is public API

| Surface | Public? | Promise |
|---|---|---|
| `proofstone-api` types (`@Proofstone`, `@Matrix`, `@CellTest`, `Agent`, `Client`, `Item`, ...) | Yes | The rules above. A baseline test guards the signatures. |
| The Gradle and Maven coordinates `io.github.h7kz.proofstone:*` | Yes | Stable names. |
| `-Dproofstone.*` system properties and `PROOFSTONE_*` environment variables documented in the guides | Yes | Same as the API. |
| The verdict names (`PASS`, `FAIL`, `INVALID`, `LEAK`, `VACUOUS`, `FLAKY`, `DISAGREE`, `SKIPPED`) and their meaning | Yes | Meaning does not change in a minor. |
| `proofstone-core`, `proofstone-protocol`, `drivers/*` internals | No | May change in any release. Do not depend on them. |
| The agent wire protocol | Versioned, see below | |
| `versions.yml` | A data contract, see below | |
| Repro bundle file layout | Best effort | Documented in the guides, may gain files. |

## Deprecation

1. A feature is first marked `@Deprecated(forRemoval = true)` with a replacement named in the Javadoc and the changelog.
2. It stays for **at least one minor release** and **at least 60 days**, whichever is longer.
3. It is removed in the next minor (while 0.x) or the next major (from 1.0).
4. A security fix may shorten this. The advisory says so.

## Wire protocol (host ↔ agent)

The protocol has its own version, checked on every agent handshake. A mismatch fails the cell loudly (`INVALID`), never silently.

- The agent and the harness core must come from the **same Proofstone release**. The harness installs the matching agent for you.
- Within a major, protocol additions are optional fields. Removing or renaming a field is a major change.
- Golden files in `proofstone-protocol` guard the schema. Changing the schema bumps the protocol version
  ([AGENTS.md](../AGENTS.md) section 3).

## versions.yml and rows

`versions.yml` is the data matrix of platforms, versions, images and pins.

- A row is `required` or `experimental` ([ADR-0008](adr/0008-versions-yml-single-source-of-truth.md), [ADR-0015](adr/0015-local-evidence-row-promotion.md)).
  `required` rows are promised to pass the conformance suite. `experimental` rows are not.
- A row is never removed in a patch release. Removal or demotion of a `required` row is announced one minor ahead.
- Pins (image digests, server build numbers) change in minors and patches. They are data, not API.
- The schema of `versions.yml` has its own version. A newer schema than the harness understands is an error.

## Supported runtime

| Needs | Supported |
|---|---|
| JDK for the harness and your tests | JDK 21 runs the real-server suites, JDK 17 builds host-side modules, JDK 26 is needed only to compile a Paper 26.x plugin ([Gradle quickstart](user/quickstart-gradle.md)). Gradle finds them as toolchains; it does not download them |
| Build tools | Gradle with JUnit 5, and Maven with failsafe (Proofstone is a library, not a Gradle plugin) |
| Docker | Docker Engine or Docker Desktop with Linux containers |
| Server JDK | Chosen per row by the harness (`versions.yml`), you do not install it |
| Minecraft EULA | You accept it explicitly (`PROOFSTONE_ACCEPT_EULA=true` or `acceptEula = true`) |

See [limits](limits.md) for what is not supported.
