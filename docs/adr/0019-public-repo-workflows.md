# ADR-0019: Public-repo workflows and a develop → main flow

- Status: Accepted (2026-11-18; amends [ADR-0014](0014-haiku-first-and-minimal-ci.md) point 5 and its 2026-11-08 update, and AGENTS.md section 4 "CI cost")
- Date: 2026-11-18
- Deciders: maintainer

## Context
- ADR-0014 removed every workflow on 2026-11-08 because Actions minutes were billed to the maintainer.
- The repository is now **public**. GitHub-hosted standard runners are free for public repositories
  ([GitHub billing docs](https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-actions)).
  The cost reason no longer holds.
- The project is about to be used by many plugin projects. They need reproducible, attributable releases, a PR gate that
  contributors cannot skip, and a branch flow that keeps `main` releasable.
- Local scripts stay the source of truth (`scripts/gates.sh`, `scripts/release-maven.sh`, `scripts/release-images.sh`,
  `scripts/release-mutation-check.sh`). Workflows call them, so there is one release path.

## Decision
1. **Three workflows, nothing scheduled.** (Amended 2026-11-19: `docs.yml` added, maintainer decision, T-M10-16.)
   - `gates.yml`: on pull requests to `develop` and `main`, and pushes to `develop`. Runs `bash scripts/gates.sh` (no Docker).
     Amendment (T-M6-113, maintainer-approved): a prior step `scripts/ci-prefetch-deps.sh` downloads dependencies, retried up to 3 times
     only on a dependency resolution error, and pushes to `develop` write the Gradle cache. The gates themselves are never retried.
   - `release.yml`: on a pushed tag `v*` only. Runs gates, the real-server smoke test and the mutation check, then publishes.
   - `docs.yml`: on pushes to `main` that touch `docs/**` or `scripts/docs-site.sh` only. Builds the static docs site with `scripts/docs-site.sh`
     and deploys it to GitHub Pages (permissions: contents read, pages write, id-token write). No schedule, no manual trigger, no secrets.
2. **Nothing publishes itself.** Central gets a `USER_MANAGED` deployment (the maintainer presses Publish in the Portal). The GitHub
   Release is a **draft**. `versions.yml` digests change only through a reviewed PR.
3. **Secrets sit in a protected environment** named `release` (required reviewer: the maintainer). Signing key, Portal token and
   the GHCR push use that environment. No secret is used on `pull_request`.
4. **Hardening rules for every workflow:** top-level `permissions: contents: read` with job-level grants only; actions pinned to a
   full commit SHA (Renovate keeps them current, `helpers:pinGitHubActionDigests`); no `pull_request_target`; no self-hosted
   or larger (billed) runners; no `workflow_dispatch` that publishes.
5. **Real-server matrix, nightly and version-watch stay local** (`./gradlew updateVersions`, `metaTest`, per-row runs). Hosted-runner
   viability of long Docker suites is not proven, so a red hosted run is investigated, never retried into green (AGENTS.md section 3).
6. **Branch flow.** Work happens on `card/*` branches that open PRs into **`develop`**. A release is a PR `develop → main`, then a
   tag on `main`. `main` always equals the last release plus hotfixes. Details: [contributing flow](../contributing-flow.md).
7. **Branch protection** on `develop` and `main`: PR required, the `gates` check required, code-owner review, no force push, no
   deletion, linear or merge commits as the maintainer prefers. Documented, applied by the maintainer in repository settings.

## Consequences
- Contributors see a red or green gate without trusting a local claim. Maintainers still run real-server suites locally and paste
  the evidence ([AGENTS.md](../../AGENTS.md) section 4).
- A release is one tag push plus two clicks (approve the `release` environment, press Publish in the Portal and publish the draft).
- The first hosted runs are the proof the workflows work. Until `gates` has run green once on a PR, treat the workflow files as
  untested.
- Compromise surface grows: a maintainer account with tag rights can start a release. The `release` environment reviewer is the
  second factor.

## Alternatives considered
- **Stay local-only.** Rejected: no provenance, no visible gate for outside contributors, the signing key stays on one machine.
- **`main` only, no `develop`.** Rejected: the maintainer asked for a staging branch, and an integration branch lets several
  card PRs soak before a release PR.
- **JReleaser or a community Gradle publish plugin.** Not adopted now. The repo's own bundle script works with the Portal API. Revisit
  if the script becomes a burden (Sonatype lists these tools but does not support the community plugins).
