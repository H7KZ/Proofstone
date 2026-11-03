# Vision

## Problem

Minecraft plugin, mod and add-on authors ship code that runs inside a server they can't easily test.

- **Unit tools fake the server.** MockBukkit, for example, never touches NMS code, real inventories,
  packets, threading models such as Folia, or what a client actually receives.
- **Manual testing doesn't scale across versions.** Authors support 1.8 → 26.x, several server forks,
  proxies and Bedrock bridges.
- **Existing e2e attempts are one-offs.** They are per-project shell scripts, or tied to a single build
  tool, language, platform or protocol version (see the [survey](research/2026-11-03-e2e-survey.md)).

## Goal

A single framework where an author writes a test once and gets a **trustworthy** verdict on every
platform and version they support.

- **Trustworthy** means a green result only when the behaviour is really correct, checked by two
  independent oracles, on a harness that has proven itself on that exact cell.
- **Every platform and version** means Java and Bedrock, plugins, mods, data packs, proxies, and every
  version from the oldest still in use to the newest release.

## Users

1. **Plugin and mod authors.** They add one dependency, write JUnit tests and run them locally and in CI.
2. **Platform maintainers and community contributors.** They add a *platform pack* (a new server type) or
   a *version* (data) without touching the core.
3. **AI coding agents.** They maintain the framework itself from task cards, guarded by gates
   ([AGENTS.md](../AGENTS.md)).

## Principles (ordered; earlier wins on conflict)

1. **Correctness over speed.** We accept slower suites in exchange for:
   - fresh state,
   - two oracles,
   - self-tests that run before every cell,
   - repeated runs at night,
   - control runs without the plugin.
2. **No silent passes.** An unknown is treated as a failure, and a skip always states its reason.
3. **Reproducible.** Everything is pinned, every failure ships a repro bundle, and the same inputs give
   the same verdict.
4. **Extensible by data and packs.** New versions are rows in `versions.yml`, and new platforms are
   packs that implement an SPI and pass conformance.
5. **Boring, stable public API.** It follows semver, plugin authors touch only `testificate-api`, and
   internals may churn freely.
6. **AI-maintainable.** Small modules, explicit contracts, golden files, task cards and ADRs.
7. **Fast enough.** We optimise for speed only after 1–6 hold: caching, parallel cells, and PR subsets
   versus nightly full runs.

## Non-goals (for now)

- **Performance and load benchmarking.** That is a different problem, though a later "soak" mode is
  possible.
- **Client-side mods with rendering.** Fabric's client gametest already covers that, and we may integrate
  it later (see [platforms.md](platforms.md)).
- **Testing against online-mode authentication.** Tests run in offline mode, and proxy forwarding is
  covered by proxy packs.
- **Replacing unit tests.** Testificate is the top of the pyramid. Keep MockBukkit and plain unit tests for
  logic.

## Success criteria for v1.0

- Disenchantment's full e2e suite runs green on Paper and Folia across every version it supports. It
  catches a deliberately injected NMS-mapping bug and an anvil-duplication bug (mutation check).
- A brand-new Paper release enters the matrix through an automated PR, with no human code changes.
- An external author adds Testificate to an existing Maven *and* an existing Gradle plugin project in
  under 15 minutes, following only the docs.
- At least one non-Bukkit platform pack (Velocity or Fabric) passes conformance.
