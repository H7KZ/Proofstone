# Releasing Proofstone to Maven Central

The release runs from the `release` workflow on a `v<version>` tag ([ADR-0019](adr/0019-public-repo-workflows.md),
[contributing flow](contributing-flow.md)). Every step also works by hand with the scripts below, which the workflow calls.
Nothing publishes itself: Central is `USER_MANAGED` and the GitHub Release is a draft. First-time setup is in
"Release secrets and one-time setup" at the end of this page.
Naming and the namespace are in [ADR-0012](adr/0012-name.md). The licence is in [ADR-0011](adr/0011-licensing.md).

## What is published

| Artefact | Module |
|---|---|
| `io.github.h7kz.proofstone:proofstone-api` | `proofstone-api` |
| `io.github.h7kz.proofstone:proofstone-core` | `proofstone-core` |
| `io.github.h7kz.proofstone:proofstone-protocol` | `proofstone-protocol` |
| `io.github.h7kz.proofstone:proofstone-drivers-mcpl` | `drivers:mcpl` |

Each artefact has a jar, a sources jar, a javadoc jar, a POM and a signature (`.asc`) when signed.
The version is `proofstone` in `gradle/libs.versions.toml`. A version ending in `-local` is never released.

## Dry run (safe, the default)

```bash
bash scripts/release-maven.sh --dry-run
```

This runs `publishToMavenLocal` and `centralStaging` (stages into `build/central-staging`), then checks:
- every module has pom, jar, sources and javadoc, and the jars are readable;
- the POM has name, description, url, licenses, developers and scm;
- signatures, when `ORG_GRADLE_PROJECT_signingKey` is in the environment.

It uploads nothing. Without a signing key it prints `signatures: NOT CHECKED` and still succeeds.

## Publish (refuses unless every condition holds)

```bash
PROOFSTONE_RELEASE_CONFIRM=yes-publish bash scripts/release-maven.sh --publish
```

`--publish` refuses, with the reason, unless all of these hold:
1. `PROOFSTONE_RELEASE_CONFIRM=yes-publish` is set in the command environment.
2. A mutation check passed for HEAD (see below): `build/release/mutation-check.env` has `status=PASS`, `head` is HEAD,
   `dirty=false`, and it is less than 24 hours old.
3. ADR-0011 is **Accepted**, and its `Licence:` line names the licence. `proofstone.publish.gradle.kts` reads both and sets
   the POM licence only while the status is Accepted. Otherwise the POM licence is a TODO and the script refuses.
4. The version is a release version (not `*-local`).
5. The git tree is clean.
6. An annotated tag `v<version>` points at HEAD. The maintainer creates it after the release PR merges.
7. The signing and portal credentials below are set.
8. Every artefact check in the dry run passes, with no TODO left in the POM.

## Mutation check (T-M6-07, local, before every publish)

```bash
bash scripts/release-mutation-check.sh                         # needs Docker; one real-server run at a time
bash scripts/release-mutation-check.sh --allow-missing=maven-paper-plugin   # only if Maven is not installed; a publish gate refuses it
```

- Reads each example's injected bugs from the `bugs:` block of its `src/main/resources/config.yml`.
- Runs a baseline (no bug, must PASS), then one run per bug (must FAIL, by assertion failures only). Each run goes through
  `scripts/dockerlock.sh` with `-Dproofstone.cells=paper@26.3`. The verdicts come from the JUnit XML.
- An example whose project is missing, or that has no runner, is **NOT RUN** and makes the status `INCOMPLETE`, which
  exits non-zero. Only `--allow-missing=<example>` turns that into a pass, and it is never a default.
- Writes `build/release/mutation-check.env`. `--publish` refuses unless it says `status=PASS` for the current HEAD.
  Any new commit needs a new run.

Then the script builds `build/central-bundle-<version>.zip` and uploads it to the Central Portal as a
`USER_MANAGED` deployment. The script never releases the deployment. Check it at
<https://central.sonatype.com/publishing/deployments> and publish it there by hand. The script never tags or pushes.

## Environment (names only, never commit values)

| Name | Used for |
|---|---|
| `ORG_GRADLE_PROJECT_signingKey` | ASCII-armoured PGP private key (in-memory signing) |
| `ORG_GRADLE_PROJECT_signingPassword` | Passphrase of that key (empty if none) |
| `CENTRAL_PORTAL_USERNAME` | Central Portal user token name |
| `CENTRAL_PORTAL_PASSWORD` | Central Portal user token password |
| `PROOFSTONE_RELEASE_CONFIRM` | Must be `yes-publish` for `--publish` |

Keep the values in a password manager. Set them per command, for example
`export ORG_GRADLE_PROJECT_signingKey="$(gpg --armor --export-secret-keys <KEYID>)"`, and never in a file in the repo.
`EULA` settings (`PROOFSTONE_ACCEPT_EULA`) are unrelated to releases.

## Before the first release (manual, maintainer)

1. **Namespace.** Verify `io.github.h7kz` on the Central Portal, as described in ADR-0012 ("Maven Central namespace").
2. **Licence.** Done (T-M6-06): Apache-2.0, ADR-0011 Accepted, `LICENSE` and `NOTICE` at the root. Changing it means a new
   ADR-0011 status and a new `Licence:` line, not an edit in the publish plugin.
3. **Signing key.** Create a PGP key and publish its public part to a key server. Keep the private key in a password manager.
4. **Portal token.** Generate a user token on the Central Portal and keep it in a password manager.
5. **Release PR.** Set the release version, run `bash scripts/gates.sh --max-workers=1`, then the dry run, merge, and tag
   `v<version>` (annotated) on the merged commit.

## Container images (GHCR, T-M6-02)

Script: `scripts/release-images.sh`. Same rules as the Maven script: local only, no workflow, no tag.

```bash
bash scripts/release-images.sh --dry-run          # default: builds both images locally, prints image ids, pushes nothing
PROOFSTONE_RELEASE_CONFIRM=yes-push GHCR_USERNAME=... GHCR_TOKEN=... bash scripts/release-images.sh --push
bash scripts/release-images.sh --write-digests    # writes the pushed digest into versions.yml, prints the diff
```

- **Dry run** builds `images/mineflayer-ref` and `images/spigot-buildtools` (one image per Java tag in
  `platforms.spigot.builder.jdk`) under `scripts/dockerlock.sh`. The local image ids are not registry digests and differ between
  rebuilds (timestamps), so they cannot pin anything.
- **Push** refuses unless `PROOFSTONE_RELEASE_CONFIRM=yes-push`, `GHCR_USERNAME` and `GHCR_TOKEN` are set, the version is not
  `*-local` and the git tree is clean. It pushes `ghcr.io/h7kz/proofstone-mineflayer-ref:<version>-ctx<hash>`, where the hash is
  the git blob hash of the build inputs. It never pushes `latest` or any other mutable tag. The login uses a throwaway
  `DOCKER_CONFIG` that is deleted on exit, so the maintainer's docker credentials are not read or written.
- **spigot-buildtools is not pushed.** Its Dockerfile says it is never pushed (it bundles BuildTools.jar). Changing that is a
  maintainer decision.
- **Write digests** refuses unless `build/release-images/pushed.env` came from a real `--push` for this version. It edits only
  the `mineflayer-ref` row in `versions.yml` (`image`, `digest`), so the PR diff is small.

Environment (names only, never commit values):

| Name | Used for |
|---|---|
| `GHCR_USERNAME` | GitHub user for the GHCR login (`docker login ghcr.io`) |
| `GHCR_TOKEN` | Token with `write:packages` (classic) or packages write (fine-grained) |
| `PROOFSTONE_RELEASE_CONFIRM` | Must be `yes-push` for `--push` |

Unverified: pulling by digest (acceptance of T-M6-02). Nothing has been pushed yet.

## Releasing 0.2.0 (maintainer runbook, T-M11-12)

`0.2.0` is prepared on `develop` and **not released**. An agent prepared it and never tags, pushes to `main`, uploads, approves the
`release` environment or presses Publish. Those steps are the maintainer's: they are marked **[maintainer]** below. The flow is the
one that cut `0.1.0` ([contributing flow](contributing-flow.md)); the differences are listed at the end.

What changed since `0.1.0` is in [CHANGELOG.md](../CHANGELOG.md). In short: additive API (`proofstone-api/api-baseline.txt` gained 33
lines, lost none), the agent protocol stays `1.3` (the Bukkit agent reports one new capability token, `pdc`), two Java-driver fixes.
`0.x` allows a break in a minor, so a reviewer should still read the baseline diff
(`git diff v0.1.0..develop -- proofstone-api/api-baseline.txt`): only `+` lines.

### What the preparation card set

- `proofstone` is `0.2.0` in `gradle/libs.versions.toml`, and the same value in `examples/maven-paper-plugin/pom.xml`
  (`scripts/gates.sh` fails when they differ), the example README, both quickstarts, the bug template placeholder and the agent
  descriptors (`plugin.yml`, `bungee.yml`, `fabric.mod.json`, `velocity-plugin.json`, `neoforge.mods.toml`, two `VERSION` constants).
- The protocol version is unchanged on purpose. `docs/platforms/*` notes that quote a `0.1.0` log line are observations and stay.
- `scripts/consumer-cleanroom.sh` reads the version from `gradle/libs.versions.toml` (it had `0.1.0` as a literal).
- The CHANGELOG `[0.2.0]` section is dated 2026-11-21. Change the date if the tag is pushed on another day.

### Pre-flight you can repeat (no secrets, nothing is uploaded)

Run each Gradle, Maven or Docker command under `scripts/dockerlock.sh`, one at a time. `PROOFSTONE_ACCEPT_EULA=true` goes in the
command environment only.

```bash
bash scripts/dockerlock.sh bash scripts/gates.sh --no-parallel --max-workers=1       # check, docsCheck, release-gate tests, version match
bash scripts/dockerlock.sh bash scripts/ci-prefetch-deps.sh --no-parallel --max-workers=1   # what hosted gates and release.yml (verify, maven) run first
bash scripts/dockerlock.sh bash scripts/release-maven.sh --dry-run                    # stages 4 artefacts + mavenLocal 0.2.0
# consumers of the local 0.2.0 (mavenLocal is where both examples read it):
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :examples:gradle-paper-plugin:e2eTest -Dproofstone.cells=paper@26.3 --no-parallel --max-workers=1
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh "$MAVEN_HOME/bin/mvn" -B -f examples/maven-paper-plugin verify -Pe2e -Dproofstone.cells=paper@26.3   # JDK 26
```

Read `build/test-results/**` and `target/failsafe-reports/*.xml`: every suite `skipped="0" failures="0" errors="0"`. The dry run prints
`signatures: NOT CHECKED` without a signing key: that is expected, and the real signature check runs in the `maven` job.
The Central-only clean-room check (`scripts/consumer-cleanroom.sh`) cannot run before the publish: it forbids mavenLocal and needs
`0.2.0` on Central. Run it after step 7.

### Steps

1. **Merge the preparation PR into `develop`.** Do not release from a `develop` that has other unreviewed changes: the release PR
   carries everything on `develop`.
2. **Release PR [maintainer].** `gh pr create --base main --head develop --title "Release v0.2.0"`. Body: the CHANGELOG `[0.2.0]`
   section. Merge with a merge commit (as for `0.1.0`) once `gates` is green. `main` is protected, so nobody pushes to it directly.
3. **Check `main`.**
   ```bash
   git fetch origin && git checkout main && git pull --ff-only
   sed -n 's/^proofstone = "\(.*\)"$/\1/p' gradle/libs.versions.toml     # must print 0.2.0
   git status --short                                                    # must be empty
   ```
4. **Tag [maintainer].** The tag ruleset `v*` allows only the maintainer, and the workflow refuses a tag that is not on `main` or
   does not match the version.
   ```bash
   git tag -a v0.2.0 -m "v0.2.0"
   git push origin v0.2.0
   ```
   The push starts the `release` workflow. Nothing else starts it.
5. **Watch `verify`.** It runs `scripts/gates.sh`, `:conformance:smoke` and the mutation check on the hosted runner. A red job stops the release: fix on `develop`
   and release the next patch (a pushed tag is never moved).
6. **Approve the `release` environment [maintainer].** After `verify`, the `maven` and `images` jobs wait for the required reviewer
   (Actions run page, "Review deployments"). `maven` re-runs the mutation check, then `release-maven.sh --publish` uploads the bundle
   to the Central Portal as a `USER_MANAGED` deployment. `images` pushes
   `ghcr.io/h7kz/proofstone-mineflayer-ref:0.2.0-ctx<hash>` (never `latest`) and uploads the digests as an artefact. `versions.yml`
   pins the image by digest, so the `0.1.0` pin keeps working: no `versions.yml` change is needed for this release.
7. **Central Publish [maintainer].** Open <https://central.sonatype.com/publishing/deployments>. Check the deployment is `VALIDATED` and
   lists the four artefacts at `0.2.0` (`proofstone-api`, `-core`, `-protocol`, `-drivers-mcpl`). Press **Publish**, or **Drop** a
   deployment you do not trust: a published version cannot be taken back. After it reaches `PUBLISHED`, the files must answer HTTP 200
   on `https://repo1.maven.org/maven2/io/github/h7kz/proofstone/<artefact>/0.2.0/` (pom, jar, sources, javadoc, `.asc`).
8. **GitHub Release [maintainer].** The workflow creates the draft `v0.2.0` with generated notes. Replace the body with the CHANGELOG
   section (including "Known limits") and publish it.
9. **Verify the published artefacts.** `PROOFSTONE_ACCEPT_EULA=true bash scripts/consumer-cleanroom.sh all 1` (it runs under the Docker
   lock itself, needs the agent jar built first, and uses a fresh Maven repository so every `0.2.0` artefact must come from Central).
   Three green runs per example is what `0.1.0` got.
10. **Close out.** Merge `main` back into `develop` by PR. Change the README status line to say `0.2.0` is on Maven Central, add a ledger
    entry to `docs/plan/ROADMAP.md`, and flip the GitHub release draft note in the ledger.

### What differs from 0.1.0

- No licence, namespace, PGP or Portal setup: all of it is done and recorded ([ADR-0011](adr/0011-licensing.md),
  [ADR-0012](adr/0012-name.md)). Before step 4, confirm the four `release` environment secrets still exist and the token is not
  expired (only the Portal and GitHub show that).
- The first real upload of `0.1.0` was rejected for a manifest in the Central bundle (T-M6-104); the staging check now guards that, and
  the dry run above exercises it.
- The tag is pushed once. If a hosted job fails after the tag exists, do not move the tag: fix on `develop` and release `0.2.1`.

### Pre-flight evidence for this preparation

The results of the commands above, with the commit they ran on, are in the PR body of T-M11-12. They are evidence for that commit
only; re-run them on the release commit if `develop` moved.

## Gotchas

- Gradle writes `.asc` files with CRLF on Windows. The script strips the carriage return before checking the header.
- Javadoc needs `UTF-8` set explicitly, or it fails on Windows. The publish plugin sets it.
- `proofstone-protocol` exposes test fixtures as Gradle variants. They are not in the POM by design.

## Release secrets and one-time setup

Do these once, by hand. None of them is done by an agent.

1. **Central namespace.** Sign up at the Central Portal with the GitHub account that owns the repository and verify the namespace
   `io.github.h7kz` ([ADR-0012](adr/0012-name.md)). Only the signup username is auto-verified.
2. **PGP key.** Create a signing key, publish the public key to a key server, and keep the private key out of the repository.
3. **GitHub environment `release`.** Settings → Environments → New environment `release`. Add the maintainer as a required reviewer and
   set deployment branches and tags to the selected pattern `v*`. Add a tag ruleset for `v*` (Settings → Rules) so only the maintainer can
   create or move a release tag.
   Add the secrets `SIGNING_KEY` (ASCII-armoured private key), `SIGNING_PASSWORD`, `CENTRAL_PORTAL_USERNAME` and
   `CENTRAL_PORTAL_PASSWORD` (Portal user token). The workflow maps them to the variable names the scripts read.
4. **Create `develop`** from `main` (`git push origin main:refs/heads/develop`), then add **branch protection** on `main` and `develop`: PR required, the `gates` check required, code-owner review, no force push, no deletion.
5. **GHCR packages.** After the first push, set each package public and link it to the repository, so `versions.yml` digests can be
   pulled without a login. Do not push the `spigot-buildtools` image (it bundles BuildTools.jar).
6. **Private vulnerability reporting.** Settings → Code security → enable it ([SECURITY.md](../SECURITY.md)).

The first `release` run is the proof that the workflow works on a hosted runner. Do a dry run first: `bash scripts/release-maven.sh --dry-run`
and `bash scripts/release-images.sh --dry-run`, and expect to fix the first hosted run.
