# Proofstone 0.1.0: release notes (DRAFT, not released)

> **DRAFT.** Not a release. Nothing is tagged, published to Maven Central or GHCR, or announced. The release is
> **NO-GO** as of T-M6-99 (see [ROADMAP, M6 review](plan/ROADMAP.md)). Every claim below is either verified locally
> (with the evidence named) or marked as not verified. Remove this banner only when the maintainer says GO.

## What Proofstone is

A framework for real-server end-to-end tests of Minecraft plugins. Tests run against a real Paper server through a
Minecraft client, and each result is checked by two oracles (the server agent and the client), so a test cannot pass on
a wrong view of the world alone. See [vision](vision.md).

## Verified locally (T-M6-99, 2026-11-09, one Docker run at a time)

- Gates: `bash scripts/gates.sh --max-workers=1` passes (compile, unit tests, lint, architecture rules, docs checks).
- Self-tests: `:examples:bad-tests:metaTest` on paper@26.3. Each bad test gives its expected verdict: `VACUOUS`, `FLAKY`,
  `DISAGREE`, `INVALID` (two cases), `LEAK`, `FAIL` (per-cell expectation) and `SKIPPED` (missing capability).
- Conformance: `:conformance:smoke` on paper@26.3 passes (2 tests, no skips).
- Mutation check: `scripts/release-mutation-check.sh --allow-missing=maven-paper-plugin` passes. Both injected Gradle
  example bugs are killed by their tests. The Maven example is not run (it does not exist yet).
- Gradle example: 10 of 10 plain runs green on paper@26.3, and 1 red in 15 plain runs across this and an earlier review.

## Known problems (not fixed in 0.1.0 unless the maintainer decides otherwise)

- The Gradle example is flaky on paper@26.3: 1 red in 15 plain runs, 2 red in 4 through the GitHub Action path. The cause is
  unexplained, and the verdict of the earlier reds was not recorded.
- The Maven example does not exist (Maven is not installed on the release machine).
- The bad-test runner does not pass the `-Dproofstone.cells` selector to its tasks, so `metaTest` runs its default matrix.
- Prefixed-Bedrock login through ViaProxy stalls on bridged cells. Native cells only for Bedrock (C-B01).
- Folia 1.20.6 is not in the supported matrix (two different reds, see `docs/platforms/folia.md`).

## Not in 0.1.0

- Maven Central and GHCR artefacts: not published. Requires namespace verification (ADR-0012). The licence is decided
  (ADR-0011, Apache-2.0).
- GitHub Action: local only (`integrations/github-action`), not published.
- Disenchantment's own suite on Proofstone: runs in another repository (T-M6-05), not verified here.
- Licence: Apache-2.0 (ADR-0011). `LICENSE` and `NOTICE` at the repository root.
- Proxies (Velocity, BungeeCord), mods (Fabric, NeoForge) and Bedrock beyond the prefixed simulation: later milestones.

## Install

Not yet available from a public repository. For local use, build and publish to `mavenLocal` (see
[quickstart-gradle.md](user/quickstart-gradle.md)). The quickstart run-through took 108 s on its first green run, with caveats
recorded in the M6 review.

## Acknowledgements and licence

Proofstone is licensed under the Apache License 2.0 (see `LICENSE` and `NOTICE`; decision in ADR-0011). Third-party components: MCProtocolLib (MIT), Mineflayer (MIT),
Testcontainers (MIT), JUnit 5 (EPL-2.0), Jackson (Apache-2.0, shaded into the agent), ViaProxy (GPL-3.0, separate container,
not redistributed).
