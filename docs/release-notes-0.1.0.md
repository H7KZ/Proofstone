# Proofstone 0.1.0: release notes

First public release. **Pre-1.0:** a minor release may break the public API ([compatibility](compatibility.md)). Read the
[limits](limits.md) and the known problems below before you depend on it.

**Release decision (2026-11-18, maintainer authorised, executed by Claude):** GO for a `0.1.0` pre-1.0 release of the harness
(`proofstone-api`, `-core`, `-protocol`, `-drivers-mcpl`). Basis: every local gate, real-server suite, example and the release mutation
check passed at `e77149b` ([ROADMAP, M6 release status refresh](plan/ROADMAP.md)). The decision does **not** claim the open items under "Not in 0.1.0".

## What Proofstone is

A framework for real-server end-to-end tests of Minecraft plugins. Tests run against a real Paper server through a
Minecraft client, and each result is checked by two oracles (the server agent and the client), so a test cannot pass on
a wrong view of the world alone. See [vision](vision.md).

## Verified locally (T-M6-13 at 4df12c8, re-run in full at e77149b on 2026-11-18, one Docker run at a time)

- Gates: `bash scripts/gates.sh --max-workers=1` passes (compile, unit tests, lint, architecture rules, docs checks).
- Self-tests: `:examples:bad-tests:metaTest` with no selector. Each bad test gives its expected verdict: `VACUOUS`, `FLAKY`,
  `DISAGREE`, `INVALID` (two cases), `LEAK`, `FAIL` (per-cell expectation) and `SKIPPED` (missing capability).
- Conformance: `:conformance:smoke` on paper@26.3 passes (2 tests, no skips). `paper@26.1.2` run: no failures, 3 skips,
  all for missing capabilities.
- Gradle example: `e2eTest` green on paper@26.3, and both injected bugs are killed by their assertions.
- Maven example: `verify -Pe2e` green on paper@26.3, and both injected bugs are killed by their assertions.
- Mutation check: `scripts/release-mutation-check.sh` (no flag) passes completely on the clean tree at 4df12c8 and again at e77149b (2026-11-18, Gradle and Maven examples), 4 bugs killed.
- Release scripts: `release-maven.sh --dry-run` OK (unsigned; signatures not checked). `--publish`, `release-images.sh --push`
  and `--write-digests` each refuse without their confirmation and credentials.

## Known problems (not fixed in 0.1.0 unless the maintainer decides otherwise)

- The example projects are flaky on paper@26.3. The Gradle example's runs on paper@26.3 were red in 3 of 61 (2 of 46 unstressed, 1 of 15 stressed), all join timeouts
  (`timed out joining ... after PT30S`). The cause is unexplained; the rate is bounded. The GitHub Action path was red in 2 of 4
  earlier runs. Follow-up card T-M6-14 logs the handshake stage at join.
- Prefixed-Bedrock login through ViaProxy stalls on bridged cells. Native cells only for Bedrock (C-B01).
- Folia 1.20.6 is not in the supported matrix (two different reds, see `docs/platforms/folia.md`).

## Not in 0.1.0

- Nothing about Central or GHCR is verified before the first tagged release run: the `release` workflow has not run on a hosted runner
  ([release.md](release.md)).
- GitHub Action: local only (`integrations/github-action`), not published, hosted runner not verified.
- Disenchantment's own suite on Proofstone: runs in another repository (T-M6-05), not verified here.
- Velocity and BungeeCord are `required` rows. Fabric, NeoForge, data packs, resource packs and Bedrock through Geyser are `experimental` ([limits](limits.md)).

## Install

When the release is published, use `io.github.h7kz.proofstone:proofstone-api`, `-core` and `-drivers-mcpl` at `0.1.0` from Maven Central.
Until then:

For local use, build and publish to `mavenLocal` (see
[quickstart-gradle.md](user/quickstart-gradle.md) and [quickstart-maven.md](user/quickstart-maven.md)). The quickstart
run-through took 108 s on its first green run, with caveats recorded in the M6 review. Not timed again.

## Acknowledgements and licence

Proofstone is licensed under the Apache License 2.0 (see `LICENSE` and `NOTICE`; decision in ADR-0011). Third-party components: MCProtocolLib (MIT), Mineflayer (MIT),
Testcontainers (MIT), JUnit 5 (EPL-2.0), Jackson (Apache-2.0, shaded into the agent), ViaProxy (GPL-3.0, separate container,
not redistributed).
