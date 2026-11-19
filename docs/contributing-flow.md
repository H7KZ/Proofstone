# Branches, PRs and releases

How code moves from a task card to a published release. Decision: [ADR-0019](adr/0019-public-repo-workflows.md).

## Branches

| Branch | Purpose | Who pushes |
|---|---|---|
| `main` | The last release plus hotfixes. Every commit on it is releasable. Tags `v<version>` live here. | Release PRs only |
| `develop` | The integration branch. Card PRs land here first. | Merged PRs only |
| `card/T-Mx-NN-short-name` | One task card, one PR (AGENTS.md section 2). | Contributor |
| `hotfix/<short-name>` | A fix for a released version, branched from `main`. | Maintainer |

Both `main` and `develop` are protected: a PR is required, the `gates` check must pass, a code-owner reviews, and force push and
deletion are off.

## The path of a change

1. Pick a task card ([docs/plan/tasks](plan/tasks/)) or open an issue. Branch `card/T-Mx-NN-short-name` from `develop`.
2. Make the change. Keep the diff near 400 changed lines, tests excluded.
3. Run `bash scripts/gates.sh` and the card's Verify commands. Real-server runs use the Docker lock
   ([AGENTS.md](../AGENTS.md) section 4).
4. Commit with a sign-off (`git commit -s`, [DCO](../CONTRIBUTING.md#licence-and-sign-off-dco)).
5. Open a PR into **`develop`**. Fill the PR template: the acceptance checklist with evidence and the gate output.
6. The `gates` workflow runs. A maintainer reviews and merges. Delete the branch.

## Cutting a release

1. Open a PR `develop → main` titled `Release v<version>`. It sets `proofstone` in `gradle/libs.versions.toml` to the release
   version (not `*-local`), updates [CHANGELOG.md](../CHANGELOG.md), and has the release notes final.
2. Merge it after `gates` is green and the maintainer has run the local real-server evidence listed in [release.md](release.md).
3. Tag the merge commit on `main`: `git tag -a v<version> -m "v<version>"` and `git push origin v<version>`.
4. The `release` workflow verifies the tag is on `main` and matches the version, runs gates, smoke and the mutation check, then
   uploads to the Central Portal, pushes the images and drafts the GitHub Release.
5. The maintainer approves the `release` environment, presses **Publish** in the Central Portal after inspecting the deployment,
   and publishes the GitHub Release draft.
6. Merge `main` back into `develop` so the version and changelog agree.

## Docs site

A push to `main` that changes `docs/**` or `scripts/docs-site.sh` runs the `docs` workflow, which builds the site and deploys it to GitHub
Pages ([docs site](docs-site.md)). It is the only deployment besides the release.

## Hotfixes

Branch `hotfix/<name>` from `main`, fix, PR into `main`, release as a patch (`v0.1.1`), then merge `main` into `develop`.

## Rolling back

Maven Central deployments cannot be taken back once published. That is why the upload is `USER_MANAGED`: inspect it in the Portal,
and **drop** a bad deployment before pressing Publish. A published bad version is superseded by a patch release and an advisory.
GHCR tags are immutable by policy here: `versions.yml` pins digests, so a bad image is replaced by a new digest in a reviewed PR.

## Labels used for release notes

`breaking`, `feature`, `bug`, `docs`, `platform`, `version-row`, `dependencies`, `skip-changelog`
(see [.github/release.yml](../.github/release.yml)).
