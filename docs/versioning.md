# Versions: matrix, pinning and adding new versions

Spec for the file format: [specs/versions-yml.md](specs/versions-yml.md). Decisions:
[ADR-0006](adr/0006-pin-everything.md) and [ADR-0008](adr/0008-versions-yml-single-source-of-truth.md).

## 1. Statuses

| Status | Meaning | Blocks a PR? |
|---|---|---|
| `required` | Conformance is green and the version is stable | yes |
| `experimental` | Newly added, or known gaps (for example, waiting on MCProtocolLib) | no; results are reported |
| `retired` | No longer run, kept for history | — |

## 2. Adding a new Minecraft or server version (automated)

1. The `version-watch` workflow runs every 6 hours.
   - **Sources:**
     - the Paper and Folia fill APIs;
     - the Mojang version manifest;
     - the MCProtocolLib maven-metadata;
     - ViaProxy releases;
     - later, the Fabric and NeoForge metadata.
   - **Output:** a PR that adds or updates rows in `versions.yml` with:
     - the pinned build number;
     - the jar SHA-256;
     - the image digest (one per Java tag, ADR-0013; `updateVersions` does not refresh these, a maintainer re-pins them with
       `docker buildx imagetools inspect itzg/minecraft-server:<tag>`);
     - the Java tag (from the rule table);
     - the protocol number;
     - `status: experimental`.
2. CI on that PR runs **conformance** and the **example suites** for the new cells.
3. A maintainer, or an agent with `tier: haiku`, merges it. Experimental cells don't block.
4. The `promote` workflow (or a card) flips a row to `required` after it has been green for 3 nightly runs
   in a row.
5. When MCProtocolLib publishes the newest protocol, the Renovate PR bumps the driver. Cells that were
   bridged become native.

Run it locally with `./gradlew updateVersions`. Add `-Pproofstone.versions.dryRun=true` to print the diff and write
nothing. The task re-pins a row only when its build changed, its sha256 is `TODO`, or it has no protocol. It adds stable
Paper or Folia versions newer than a platform's newest row, as `experimental`. Its unit tests use recorded API responses
(`build-logic/src/test/resources/versions/api`) and never touch the network.

**CI on version-watch PRs.** The workflow opens its PR with `GITHUB_TOKEN`, and GitHub does not start workflows from
PRs made that way. `ci.yml` therefore does not run, and the required checks (gates, smoke, renovate-validate) stay
pending and block the merge. The fix is to give `create-pull-request` a token that can trigger workflows: a
fine-grained PAT or GitHub App token stored as the repository secret `VERSION_WATCH_TOKEN`, passed as `token:` in
`.github/workflows/version-watch.yml`. A maintainer creates that secret; it is not in the repo. Until then, close and
reopen the PR (from a maintainer account) to start `ci.yml`.

**No code change is needed for a version unless conformance shows a real behaviour change.** A real
change becomes a card against the pack, with the gotcha recorded in `docs/platforms/<id>.md`.

## 3. Java rule table (observed in T-M3-02, 2026-11-07)

Each row booted with its rule-assigned tag and passed C01 (agent health, Java major checked by `PaperHost`).

| MC versions | Java image tag | Observed on |
|---|---|---|
| ≤ 1.16.5 | `java8` | Paper 1.8.8, 1.12.2, 1.16.5 (observed) |
| 1.17.x | `java16` (the tag exists on Docker Hub) | Paper 1.17.1 (observed) |
| 1.18 – 1.20.4 | `java17` | Paper 1.18.2, 1.20.4 (observed) |
| 1.20.5 – 1.21.x | `java21` | Paper 1.20.6, 1.21.11 (observed) |
| 26.x | `java25` | Paper 26.1.2, 26.2, 26.3 (observed) |

No rule needed changing. The host digest is pinned per tag (`host.digests`, ADR-0013). Folia has the same digests but
no run on them yet.

## 4. Choosing cells

- **PR default** (`all` with scope `pr`, which is the default): the `required` rows, plus the newest version of
  each family that the user project declares in its own `proofstone.yml` (test-api spec §1). Experimental rows run on
  a PR only when named. Retired rows never run unless named.
- **Nightly** (`-Dproofstone.scope=nightly`): every `required` and `experimental` row, plus every family version.
  Retired rows never run unless named.
- **Local:** `-Dproofstone.cells=paper@1.21.11,folia@latest` (or `PROOFSTONE_CELLS`). It replaces the `@Matrix`
  versions of the platforms it names. Syntax and errors: test-api spec §1.

## 5. Dependency bumps (Renovate)

These are all pinned and bumped by Renovate PRs, each gated by conformance:
- MCProtocolLib;
- ViaProxy;
- Mineflayer, in the reference image;
- Testcontainers;
- JUnit;
- base images, by digest.
