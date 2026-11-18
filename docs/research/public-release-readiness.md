# Public release readiness (research, 2026-11-18)

Status: research note, read-only. Not a decision. Nothing here changes a rule in AGENTS.md or an ADR.
Primary sources only. Anything not confirmed at a primary source is marked **UNVERIFIED**.
Fetched 2026-11-18. Doc pages change; re-check before acting.

## Question

What must be true before Proofstone can be released publicly (Maven Central, GHCR, GitHub public repo with community files), what does
the repo already have, and what conflicts with the "no CI, no workflows" decision (AGENTS.md section 4, ADR-0014 update 2026-11-08)?

## Findings

### 1. Maven Central (Central Portal)

- Requirements for every deployment: `groupId`, `artifactId`, `version`; version must not end in `-SNAPSHOT`; packaging `jar` unless stated.
  POM must have `name`, `description`, `url`, at least one `license`, at least one `developer`, and `scm` with `connection`,
  `developerConnection`, `url`. `<repositories>` / `<pluginRepositories>` in the POM are discouraged. Non-`pom` projects need
  `-sources.jar` and `-javadoc.jar` (placeholder jars are accepted). Every file needs a GPG `.asc`. `.md5` and `.sha1` are required for
  every file; `.sha256`/`.sha512` optional.
  Source: <https://central.sonatype.org/publish/requirements/>
- Namespace: for `io.github.<user>` the page says signing up with GitHub usually verifies the namespace automatically, but only for the
  GitHub **username used to sign up**; organisation names are not auto-registered. Otherwise the verification key goes in a temporary public repo
  (code hosting) or a DNS TXT record (own domain). If no namespace was created, email Central Support.
  Source: <https://central.sonatype.org/register/namespace/>
- Portal Publisher API: user token from the Account page, sent as `Authorization: Bearer base64(user:pass)`. `POST /api/v1/publisher/upload`
  (multipart, part `bundle`), optional `name` and `publishingType`. `AUTOMATIC` publishes after validation; `USER_MANAGED` (default) waits for a
  manual publish in the Portal or `POST /api/v1/publisher/deployment/<id>`. Status via `POST /api/v1/publisher/status?id=`; states PENDING,
  VALIDATING, VALIDATED, PUBLISHING, PUBLISHED, FAILED. The bundle layout is on a separate page not fetched here (**UNVERIFIED** here; the repo's
  script builds one, and a real upload has never been run).
  Source: <https://central.sonatype.org/publish/publish-portal-api/>
- Gradle options: Sonatype lists JReleaser as the main option, plus unsupported community plugins (vanniktech, GradleUp/nmcp, and others).
  It also says existing OSSRH-plugin users can use the Portal OSSRH Staging API. Source: <https://central.sonatype.org/publish/publish-portal-gradle/>
- Gradle `maven-publish`: `java { withSourcesJar(); withJavadocJar() }`, `pom {}` block with name, description, url, licenses, developers, scm.
  Checksums are generated for all artefacts except signatures. Source: <https://docs.gradle.org/current/userguide/publishing_maven.html>
- Gradle `signing`: `useInMemoryPgpKeys(key, password)` from `ORG_GRADLE_PROJECT_signingKey/Password`; `sign(publishing.publications[...])`.
  Source: <https://docs.gradle.org/current/userguide/signing_plugin.html>
- Not found in the Gradle docs: handling of test-fixtures variants in the POM, and multi-module publishing guidance (the page says it does not
  cover them). The repo's claim that protocol test fixtures stay out of the POM is therefore a local decision, not a documented guarantee.
- Reproducible archives (Gradle `isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`, fixed permissions; locale/encoding
  properties). Source: <https://reproducible-builds.org/docs/jvm/>

### 2. GHCR from GitHub Actions

- Standard GitHub-hosted runners in public repositories are free and do not reduce included minutes; larger runners are billed even in public
  repos. Source: <https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-actions>
- Container registry storage and bandwidth is "currently free" with at least one month's notice of change; GitHub Packages is free for public
  packages. Source: <https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-packages>
- GHCR: new packages are private by default (visibility must be flipped to public for unauthenticated pulls). Publishing from a workflow with
  `GITHUB_TOKEN` links the repo automatically; from the CLI add the `org.opencontainers.image.source` label. Pull by digest with `@sha256:`.
  PAT (classic) with `write:packages` is the documented non-workflow credential. If a package was pushed earlier unlinked,
  `GITHUB_TOKEN` may lack push permission to it. Source:
  <https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry>
- Attestations: `id-token: write`, `attestations: write`, `contents: read`, plus `packages: write` for images; `push-to-registry: true`,
  `subject-name` (fully qualified, no tag) and `subject-digest` (from `docker/build-push-action` output). Verify with
  `gh attestation verify oci://...`. The page uses `actions/attest`; whether `actions/attest-build-provenance` is still the preferred name is
  **UNVERIFIED**. Plan availability (public repo vs private) is **UNVERIFIED** on that page.
  Source: <https://docs.github.com/en/actions/security-for-github-actions/using-artifact-attestations/using-artifact-attestations-to-establish-provenance-for-builds>
- Multi-arch: QEMU + buildx on one runner (simple, slow) or Docker's distributed native-runner builder. Source:
  <https://docs.docker.com/build/ci/github-actions/multi-platform/>
- Hardening: pin actions to full SHA, read-only default `GITHUB_TOKEN`, no `pull_request_target` with untrusted checkout, no self-hosted runners on
  public repos, environment secrets gated by required reviewers. Source:
  <https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions>
- Environments with required reviewers are available on GitHub Free for public repos only. Source:
  <https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/managing-environments-for-deployment>
- Fork PRs get no secrets and a read-only `GITHUB_TOKEN`; first-time contributors may need a maintainer to approve runs; tag-push events are not
  created when more than three tags are pushed at once. Source:
  <https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows>

### 3. Release automation, branching, versioning

- Release notes: GitHub generates notes from merged PRs; `.github/release.yml` sets categories by label, exclusions by label/author, and a `"*"`
  catch-all. Source: <https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes>
- Branch protection: required reviews, required status checks (strict/loose), signed commits, linear history, force-push control; admins are exempt
  unless "do not allow bypassing" is set; bypass lists only for org-owned repos. Which options exist on a free plan public repo is
  **UNVERIFIED** from that page (only push restriction is stated for free orgs). Source:
  <https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches>
- SemVer 2.0: major zero means anything may change; a public API must be declared; deprecation requires a minor bump; 1.0.0 defines the public API.
  Source: <https://semver.org/>
- Changelog: Keep a Changelog 1.1.0: Added/Changed/Deprecated/Removed/Fixed/Security, `Unreleased` on top, `[YANKED]` marker, state SemVer use.
  Source: <https://keepachangelog.com/en/1.1.0/>
- Dependabot supports gradle, github-actions and docker ecosystems (cooldown table). Config file path not confirmed on the fetched page
  (**UNVERIFIED**; Renovate is already configured in the repo, so this is optional). Source:
  <https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference>
- Gitflow-lite (`develop` -> `main`) is a project convention, not a documented GitHub requirement. No primary source; treat as a decision.

### 4. Community files

- Community profile checklist checks README, CODE_OF_CONDUCT, LICENSE, CONTRIBUTING (the page says "such as", so the list may not be exhaustive),
  and issue templates in `.github/ISSUE_TEMPLATE` (`.yml` forms need `name:` and `description:`). Source:
  <https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/about-community-profiles-for-public-repositories>
- SECURITY.md should say supported versions and how to report. Where it can live is not stated on the fetched page (**UNVERIFIED**; OpenSSF
  Scorecard looks "in well-known directories"). Source: <https://docs.github.com/en/code-security/getting-started/adding-a-security-policy-to-your-repository>
- Private vulnerability reporting: enable under Settings > Advanced Security; owners/admins of public repos. Source:
  <https://docs.github.com/en/code-security/security-advisories/working-with-repository-security-advisories/configuring-private-vulnerability-reporting-for-a-repository>
- CODEOWNERS in `.github/`, root or `docs/`; must be on the base branch; "Require review from Code Owners" in branch protection or rulesets.
  Source: <https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners>
- Contributor Covenant 2.1: the enforcement contact placeholder must be filled in; text is adapted with attribution. Text licence **UNVERIFIED**
  at the fetched page. Source: <https://www.contributor-covenant.org/version/2/1/code_of_conduct/>
- GitHub Pages: project sites at `<owner>.github.io/<repo>`, one project site per repo. Free-plan availability and publishing sources
  **UNVERIFIED** on the fetched page. Source: <https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages>
  A docs site is optional: `docs/` already renders on GitHub.
- OpenSSF Best Practices (passing): needs FLOSS licence, human-readable release notes, published vulnerability reporting with a first response
  within 14 days, automated tests with a documented run command, static analysis, and a test-for-new-features policy. Source:
  <https://www.bestpractices.dev/en/criteria/0>
- OpenSSF Scorecard checks include Pinned-Dependencies, Token-Permissions, Branch-Protection, Signed-Releases (`.asc`/`.sig` on release assets;
  SLSA provenance scores highest), Security-Policy, Dangerous-Workflow, Dependency-Update-Tool, SBOM, CI-Tests. It scores workflows and
  branch settings, so a no-workflow repo scores low on several by construction. Source:
  <https://github.com/ossf/scorecard/blob/main/docs/checks.md>

### 5. Repo state (read 2026-11-18, `main` at 64ae8c1)

- Root: AGENTS.md, CLAUDE.md, CONTRIBUTING.md (DCO), LICENSE, NOTICE, README.md, renovate.json, versions.yml. No SECURITY.md, CODE_OF_CONDUCT.md,
  SUPPORT.md, CHANGELOG.md, CODEOWNERS.
- `.github/`: `pull_request_template.md` and `ISSUE_TEMPLATE/task.yml` only. No workflows, no `release.yml`, no `config.yml`, no `dependabot.yml`.
- README says "Status: design phase ... There is no code yet". This is stale (ROADMAP shows M0 to M10 work, 490 commits).
- ADR-0011 Accepted: Apache-2.0, DCO. ADR-0012 Accepted: name, group `io.github.h7kz.proofstone`, namespace verification **pending**.
  ADR-0014 update: all workflows removed 2026-11-08; no required checks.
- `docs/release.md` and `scripts/release-maven.sh`, `release-images.sh`, `release-mutation-check.sh`: local release flow with dry run, publish
  guards, `USER_MANAGED` upload, no tag or push by script. Version in catalog is `0.1.0-local`. No git tags exist. `docs/release-notes-0.1.0.md`
  says NO-GO and lists known flakiness.
- `proofstone-api` has an API baseline test (`api-baseline.txt`). No binary-compat tool (japicmp or similar) found. No deprecation policy found in
  docs (grep for deprecat/semver in specs, versioning, architecture returned nothing relevant). Wire protocol is versioned (health reports
  protocol version, e.g. 1.1/1.3 notes in agent-protocol.md); versions.yml has `schema: 5`. No written compatibility promise for either.
- Dependency risk: `drivers/mcpl` uses MCProtocolLib resolved via `repo.opencollab.dev` (settings.gradle.kts). Whether `org.geysermc.mcprotocollib:protocol`
  is on Maven Central is **UNVERIFIED**. If not, Central's "dependencies must be resolvable" expectation is at risk. (The requirements page says
  dependencies are strongly recommended, not required, and discourages repositories in the POM; it does not state resolvability rules.) The Bedrock
  driver pins a timestamped snapshot, but `drivers:bedrock` is not in the published list.
- Reproducible-build settings were not found in `build-logic`.

### Conflicts with AGENTS.md / ADR-0014

- AGENTS.md section 4: "Never add one [a workflow] ... without maintainer approval"; ADR-0014 update: all workflows deleted to stop billed minutes.
  Public repo hosted-runner minutes are free (source above), which removes the original reason, but an ADR is needed to change the rule.
- Docs say the release is manual and local (docs/release.md, ADR-0012 "Release mechanics", ADR-0014). A tag-triggered publish workflow contradicts these.
- Signing key and Portal token in Actions secrets contradicts "keep values in a password manager, per command" (docs/release.md). Environment secrets with
  required reviewers are the documented mitigation.
- Real-server tests need Docker and a Minecraft EULA opt-in; hosted-runner Docker/EULA/time-budget viability is **UNVERIFIED** (release notes also say the
  hosted runner path was not verified).

## Proposed ADR: reintroducing workflows (draft for the maintainer, not accepted)

Title: ADR-0019 Minimal public-repo workflows. Amends ADR-0014 point 5 and AGENTS.md section 4 "CI cost".
- Scope: (a) `release.yml` on tag `v*`, `workflow_dispatch` off; builds, runs `bash scripts/gates.sh` equivalent, publishes with a protected
  `release` environment (required reviewer = maintainer), uploads a USER_MANAGED bundle, GitHub Release with generated notes; (b) optional `gates.yml`
  on `pull_request` running `check docsCheck` only (no Docker). Nothing scheduled. Smoke/matrix stay local.
- Rules: top-level `permissions: contents: read`; job-level grants only; actions pinned to full SHA (Renovate `helpers:pinGitHubActionDigests`
  already set); no `pull_request_target`; no self-hosted runners; standard runners only (no larger runners, which bill on public repos).
- Keep the local scripts as the source of truth; the workflow calls them, so there is one release path.
- Precondition: repo is public (free hosted minutes). Re-check the billing page at decision time.
- Alternative: stay local-only; sign and upload by hand per docs/release.md. Cost: no provenance attestations, lower Scorecard score, key on a
  maintainer machine.

## Checklist

| # | Item | State | Note |
|---|---|---|---|
| 1 | Licence file (Apache-2.0) + NOTICE | DONE | ADR-0011 |
| 2 | DCO policy | DONE | CONTRIBUTING.md |
| 3 | README current | MISSING | says "design phase, no code yet" |
| 4 | CONTRIBUTING | DONE | references agent workflow; add human-contributor quick path (DECISION-NEEDED: are outside PRs welcome) |
| 5 | SECURITY.md + private vuln reporting enabled | MISSING | needs a contact and supported-versions text |
| 6 | CODE_OF_CONDUCT | MISSING | needs enforcement contact (DECISION-NEEDED: who) |
| 7 | SUPPORT.md | MISSING | optional for profile |
| 8 | Issue templates | DONE (partial) | only `task.yml`; add bug report and `config.yml` (DECISION-NEEDED: card-only intake vs open bugs) |
| 9 | PR template | DONE | card-centric |
| 10 | CODEOWNERS | MISSING | needs branch rule to take effect |
| 11 | CHANGELOG (Keep a Changelog) | MISSING | `release-notes-0.1.0.md` exists |
| 12 | `.github/release.yml` note categories | MISSING | needs labels on PRs |
| 13 | Branch protection on `main` | DECISION-NEEDED | removed 2026-11-08; re-adding without CI limits to review/force-push rules |
| 14 | develop -> main flow | DECISION-NEEDED | currently PR to `main` from `card/*` |
| 15 | Central namespace `io.github.h7kz` verified | MISSING | ADR-0012: not verified; must be the signup username |
| 16 | PGP key created and published | MISSING | docs/release.md |
| 17 | Portal user token | MISSING | manual |
| 18 | POM fields, sources, javadoc jars, signing, checksums | DONE (dry run) | signatures "NOT CHECKED" without key; real upload never run; bundle layout UNVERIFIED |
| 19 | Dependency resolvability from Central (MCProtocolLib) | DECISION-NEEDED | UNVERIFIED; repo.opencollab.dev used |
| 20 | Release version set, tag `v0.1.0` | MISSING | version `0.1.0-local`, no tags |
| 21 | Release verdict | DECISION-NEEDED | draft says NO-GO (flaky join timeouts, unverified Disenchantment suite) |
| 22 | GHCR images pushed, public, digest pinned | MISSING | script exists; nothing pushed; packages private by default; link label needed |
| 23 | spigot-buildtools image | DECISION-NEEDED | not pushed on purpose (bundles BuildTools.jar) |
| 24 | Provenance/attestations for images and jars | MISSING | needs a workflow (OIDC); local alternative not researched |
| 25 | Workflows (release, gates) | DECISION-NEEDED | conflicts with AGENTS.md; see proposed ADR |
| 26 | Dependency update tool | DONE | Renovate config (is the Renovate app installed on a public repo: UNVERIFIED) |
| 27 | SemVer + deprecation policy for `proofstone-api` | MISSING | baseline test exists; no written policy; no binary-compat check |
| 28 | Wire protocol and versions.yml compatibility policy | MISSING | versions exist; no promise written |
| 29 | Reproducible build settings | MISSING | not found in build-logic |
| 30 | Docs site (Pages) | DECISION-NEEDED | optional; `docs/` readable on GitHub |
| 31 | Mutation check, gates, self-tests | DONE | local evidence in release notes draft |
| 32 | Trademark/name check | DONE | ADR-0012; lawyer review in ADR-0011 still open |
| 33 | Secrets/binaries audit before going public | MISSING | not run; history has 490 commits and agent worktrees (`.claude/worktrees` seen on disk; confirm ignored) |

## Risks

1. Going public with a README that says "no code yet" and a draft NO-GO release.
2. Central deployment is not reversible once published; coordinates and API cannot be taken back. Use `USER_MANAGED` and inspect first (already the script default).
3. Namespace verification failure if the GitHub account used to sign up differs from `H7KZ`.
4. Signing key and tokens in Actions secrets would widen the attack surface; unprotected workflow edits by a PR could exfiltrate them. Use environments and no `pull_request_target`.
5. Unpinned or private-by-default GHCR packages break `versions.yml` digest pulls for outside users.
6. Flaky join timeouts (documented) become public credibility issues; hosted-runner viability of real-server tests is unproven.
7. No written compatibility policy means 0.x changes will surprise users; SemVer says 0.y.z allows anything, so state that explicitly.
8. History not yet audited for secrets or large/binary files before the repo is made public.

## Open decisions (maintainer)

1. Go/no-go for 0.1.0 and the first public date.
2. Reintroduce workflows (ADR-0019) or stay local-only.
3. Branching: keep `main` + `card/*`, or add `develop`.
4. Branch protection and CODEOWNERS settings once public.
5. Contact address for SECURITY.md and CODE_OF_CONDUCT; enable private vulnerability reporting.
6. Accept outside contributions and bug reports, or card-only intake.
7. Publish tool: keep the custom bundle script or move to JReleaser / a community plugin (Sonatype lists, does not support, community plugins).
8. MCProtocolLib resolvability from Central; fix or vendor-note before publishing `drivers:mcpl`.
9. Publish `spigot-buildtools` image or not.
10. API deprecation window (for example: deprecate in minor N, remove no earlier than the next major, 0.x exempt but announced) and wire protocol
    compatibility promise (for example: agent and core must match on major; minor additive).
11. Lawyer review (ADR-0011 open item).
12. Docs site yes/no.
