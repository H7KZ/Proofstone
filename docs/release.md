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
2. ADR-0011 is **Accepted**, and the licence is set in `build-logic/src/main/kotlin/proofstone.publish.gradle.kts`.
   Set both together. While the POM licence is a TODO, the script refuses.
3. The version is a release version (not `*-local`).
4. The git tree is clean.
5. An annotated tag `v<version>` points at HEAD. The maintainer creates it after the release PR merges.
6. The signing and portal credentials below are set.
7. Every artefact check in the dry run passes, with no TODO left in the POM.

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

## Gotchas

- Gradle writes `.asc` files with CRLF on Windows. The script strips the carriage return before checking the header.
- Javadoc needs `UTF-8` set explicitly, or it fails on Windows. The publish plugin sets it.
- `proofstone-protocol` exposes test fixtures as Gradle variants. They are not in the POM by design.
