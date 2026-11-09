# Proofstone 0.1.0: release notes (DRAFT, not released)

> **DRAFT.** Not a release. Nothing is tagged, published to Maven Central or GHCR, or announced. The release is
> **NO-GO** for a public 0.1.0 claim (see [ROADMAP, M6 review addendum](plan/ROADMAP.md), 2026-11-09). Every claim below is
> either verified locally (with the evidence named) or marked as not verified. Remove this banner only when the maintainer says GO.

## What Proofstone is

A framework for real-server end-to-end tests of Minecraft plugins. Tests run against a real Paper server through a
Minecraft client, and each result is checked by two oracles (the server agent and the client), so a test cannot pass on
a wrong view of the world alone. See [vision](vision.md).

## Verified locally (T-M6-13, 2026-11-09, at commit 4df12c8, one Docker run at a time)

- Gates: `bash scripts/gates.sh --max-workers=1` passes (compile, unit tests, lint, architecture rules, docs checks).
- Self-tests: `:examples:bad-tests:metaTest` with no selector. Each bad test gives its expected verdict: `VACUOUS`, `FLAKY`,
  `DISAGREE`, `INVALID` (two cases), `LEAK`, `FAIL` (per-cell expectation) and `SKIPPED` (missing capability).
- Conformance: `:conformance:smoke` on paper@26.3 passes (2 tests, no skips). `paper@26.1.2` run: no failures, 3 skips,
  all for missing capabilities.
- Gradle example: `e2eTest` green on paper@26.3, and both injected bugs are killed by their assertions.
- Maven example: `verify -Pe2e` green on paper@26.3, and both injected bugs are killed by their assertions.
- Mutation check: `scripts/release-mutation-check.sh` (no flag) passes completely on the clean tree at 4df12c8, 4 bugs killed.
- Release scripts: `release-maven.sh --dry-run` OK (unsigned; signatures not checked). `--publish`, `release-images.sh --push`
  and `--write-digests` each refuse without their confirmation and credentials.

## Known problems (not fixed in 0.1.0 unless the maintainer decides otherwise)

- The example projects are flaky on paper@26.3. The Gradle example's plain path is red in 3 of 61 runs, all join timeouts
  (`timed out joining ... after PT30S`). The cause is unexplained; the rate is bounded. The GitHub Action path was red in 2 of 4
  earlier runs. Follow-up card T-M6-14 logs the handshake stage at join.
- Prefixed-Bedrock login through ViaProxy stalls on bridged cells. Native cells only for Bedrock (C-B01).
- Folia 1.20.6 is not in the supported matrix (two different reds, see `docs/platforms/folia.md`).

## Not in 0.1.0

- Maven Central and GHCR artefacts: not published. Requires namespace verification on Central (ADR-0012). The licence is
  decided (ADR-0011, Apache-2.0). The namespace is `io.github.h7kz.proofstone` (T-M6-12).
- GitHub Action: local only (`integrations/github-action`), not published, hosted runner not verified.
- Disenchantment's own suite on Proofstone: runs in another repository (T-M6-05), not verified here.
- Proxies (Velocity, BungeeCord), mods (Fabric, NeoForge) and Bedrock beyond the prefixed simulation: later milestones.

## Install

Not yet available from a public repository. For local use, build and publish to `mavenLocal` (see
[quickstart-gradle.md](user/quickstart-gradle.md) and [quickstart-maven.md](user/quickstart-maven.md)). The quickstart
run-through took 108 s on its first green run, with caveats recorded in the M6 review. Not timed again.

## Acknowledgements and licence

Proofstone is licensed under the Apache License 2.0 (see `LICENSE` and `NOTICE`; decision in ADR-0011). Third-party components: MCProtocolLib (MIT), Mineflayer (MIT),
Testcontainers (MIT), JUnit 5 (EPL-2.0), Jackson (Apache-2.0, shaded into the agent), ViaProxy (GPL-3.0, separate container,
not redistributed).
