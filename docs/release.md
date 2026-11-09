# Releasing Proofstone to Maven Central

Local only. There is no CI and no workflow (maintainer decision 2026-11-08). The maintainer runs every step by hand.
Naming and the namespace are in [ADR-0012](adr/0012-name.md). The licence is in [ADR-0011](adr/0011-licensing.md).

## What is published

| Artefact | Module |
|---|---|
| `io.github.h7kz.proofstone:proofstone-api` | `proofstone-api` |
| `io.github.h7kz.proofstone:proofstone-core` | `proofstone-core` |
| `io.github.h7kz.proofstone:proofstone-protocol` | `proofstone-protocol` |
| `io.github.h7kz.proofstone:proofstone-drivers-mcpl` | `drivers:mcpl` |

Each artefact has a jar, a sources jar, a javadoc jar, a POM and a signature (`.asc`) when signed.
The version is `proofstone` in `gradle/libs.versions.toml`. The local version `0.1.0-local` is never released.

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
3. ADR-0011 is **Accepted**, and the licence is set in `build-logic/src/main/kotlin/proofstone.publish.gradle.kts`.
   Set both together. While the POM licence is a TODO, the script refuses.
4. The version is a release version (not `*-local`).
5. The git tree is clean.
6. An annotated tag `v<version>` points at HEAD. The maintainer creates it after the release PR merges.
7. The signing and portal credentials below are set.
8. Every artefact check in the dry run passes, with no TODO left in the POM.

## Mutation check (T-M6-07, local, before every publish)

```bash
bash scripts/release-mutation-check.sh                         # needs Docker; one real-server run at a time
bash scripts/release-mutation-check.sh --allow-missing=maven   # only while the Maven example does not exist
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
2. **Licence.** Decide ADR-0011 (T-M6-06). Then update the ADR status and `decidedLicence` in
   `proofstone.publish.gradle.kts` in the same PR.
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

## Gotchas

- Gradle writes `.asc` files with CRLF on Windows. The script strips the carriage return before checking the header.
- Javadoc needs `UTF-8` set explicitly, or it fails on Windows. The publish plugin sets it.
- `proofstone-protocol` exposes test fixtures as Gradle variants. They are not in the POM by design.
