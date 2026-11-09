#!/usr/bin/env bash
# Maven Central release for Proofstone (T-M6-01, ADR-0012, docs/release.md). Local only: no CI, no workflow.
#   --dry-run (default): publishToMavenLocal + stage every artefact in build/central-staging + check the artefacts,
#                        signatures (when a signing key is in the environment) and POM completeness. Uploads nothing.
#   --publish:           refuses unless every precondition holds (first: a PASS from scripts/release-mutation-check.sh for
#                        this HEAD, T-M6-07), then uploads the staged bundle to the Central Portal
#                        as a USER_MANAGED deployment. The maintainer releases it in the portal UI. Never tags, never pushes.
# Secrets come from the environment only. This script prints their names, never their values.
# Usage: bash scripts/release-maven.sh [--dry-run|--publish]
set -euo pipefail
cd "$(dirname "$0")/.."

MODE=dry-run
case "${1:---dry-run}" in
  --dry-run) MODE=dry-run ;;
  --publish) MODE=publish ;;
  -h | --help) sed -n '2,8p' "$0"; exit 0 ;;
  *) echo "usage: bash scripts/release-maven.sh [--dry-run|--publish]" >&2; exit 2 ;;
esac

die() { echo "release-maven: $MODE: $*" >&2; exit 1; }
err() { echo "release-maven: check failed: $*" >&2; ERRORS=$((ERRORS + 1)); }

# Keep in step with the published modules (build-logic/.../proofstone.publish.gradle.kts). The check below fails
# on any extra or missing artefact directory in the staging area.
GROUP_PATH=io/github/h7kz/proofstone
MODULES=(proofstone-api proofstone-core proofstone-protocol proofstone-drivers-mcpl)
STAGING=build/central-staging
VERSION=$(sed -n 's/^proofstone = "\(.*\)"$/\1/p' gradle/libs.versions.toml)
[[ -n "$VERSION" ]] || die "cannot read the proofstone version from gradle/libs.versions.toml"

# T-M6-07: publish needs a complete PASS for this exact commit. The check writes the result file; the gate
# (scripts/lib/mutation-gate.sh, T-M6-09) decides. A run with --allow-missing is never publishable.
MUTATION_RESULT=build/release/mutation-check.env
# shellcheck source=lib/mutation-gate.sh
source scripts/lib/mutation-gate.sh
mutation_gate() {
  mutation_gate_check "$MUTATION_RESULT" "$(git rev-parse HEAD)" "$(date +%s)" \
    || die "refusing. The mutation-check result is not a complete PASS for HEAD (reason above). Re-run: bash scripts/release-mutation-check.sh, without --allow-missing (T-M6-07)."
  echo "mutation-check: complete PASS for $(git rev-parse --short=12 HEAD) ($MUTATION_RESULT)"
}

if [[ "$MODE" == publish ]]; then
  # Every refusal names its reason. None of these checks can be bypassed by a flag.
  [[ "${PROOFSTONE_RELEASE_CONFIRM:-}" == "yes-publish" ]] \
    || die "refusing. Set PROOFSTONE_RELEASE_CONFIRM=yes-publish in the command environment (never in a file)."
  mutation_gate
  grep -q '^- Status: \*\*Accepted\*\*' docs/adr/0011-licensing.md \
    || die "refusing. ADR-0011 (licence) is not Accepted. The maintainer decides the licence first (T-M6-06)."
  case "$VERSION" in
    *-local) die "refusing. version $VERSION is the local-only version. Set the release version in gradle/libs.versions.toml." ;;
  esac
  [[ -z "$(git status --porcelain)" ]] || die "refusing. The git tree is not clean. Commit or discard changes first."
  TAG="v$VERSION"
  [[ "$(git cat-file -t "refs/tags/$TAG" 2>/dev/null || true)" == "tag" ]] \
    || die "refusing. No annotated tag $TAG. The maintainer creates it after the release PR merges."
  [[ "$(git rev-parse "$TAG^{commit}")" == "$(git rev-parse HEAD)" ]] \
    || die "refusing. $TAG does not point at HEAD."
  for name in ORG_GRADLE_PROJECT_signingKey ORG_GRADLE_PROJECT_signingPassword CENTRAL_PORTAL_USERNAME CENTRAL_PORTAL_PASSWORD; do
    [[ -n "${!name:-}" ]] || die "refusing. Environment variable $name is not set (names only, values never printed)."
  done
fi

echo "== gradle: publishToMavenLocal centralStaging ($MODE, version $VERSION)"
rm -rf "$STAGING"
GRADLE_EXTRA=()
[[ "$MODE" == publish ]] && GRADLE_EXTRA+=(-PproofstoneRequireSigning=true)
./gradlew publishToMavenLocal centralStaging --max-workers=1 "${GRADLE_EXTRA[@]}"

echo "== verify staged artefacts in $STAGING"
command -v jar > /dev/null || die "no jar tool on PATH (needs a JDK)"
SIGNED=false
[[ -n "${ORG_GRADLE_PROJECT_signingKey:-}" ]] && SIGNED=true
ERRORS=0
LICENCE_TODO=false
ARTEFACT_ROOT="$STAGING/$GROUP_PATH"
[[ -d "$ARTEFACT_ROOT" ]] || die "no artefacts staged under $ARTEFACT_ROOT"

for dir in "$ARTEFACT_ROOT"/*/; do
  name=$(basename "$dir")
  [[ " ${MODULES[*]} " == *" $name "* ]] || err "unexpected artefact $name in staging (add it to MODULES or fix the build)"
done

for m in "${MODULES[@]}"; do
  base="$ARTEFACT_ROOT/$m/$VERSION/$m-$VERSION"
  for f in "$base.pom" "$base.jar" "$base-sources.jar" "$base-javadoc.jar"; do
    [[ -s "$f" ]] || err "missing or empty: $f"
  done
  [[ -s "$base.pom" ]] || continue

  if [[ -s "$base-sources.jar" ]]; then
    listing=$(jar tf "$base-sources.jar")
    grep -q '\.java$' <<< "$listing" || err "$m sources jar has no .java files"
  fi
  if [[ -s "$base-javadoc.jar" ]]; then
    listing=$(jar tf "$base-javadoc.jar")
    grep -q 'index.html' <<< "$listing" || err "$m javadoc jar has no index.html"
  fi

  pom="$base.pom"
  for tag in name description url licenses developers scm; do
    grep -q "<$tag>" "$pom" || err "$m POM lacks <$tag>"
  done
  grep -q '<description>[^<]\+</description>' "$pom" || err "$m POM has an empty description"
  if grep -q 'TODO' "$pom"; then
    LICENCE_TODO=true
  fi

  if [[ "$SIGNED" == true ]]; then
    for f in "$base.jar" "$base.pom" "$base-sources.jar" "$base-javadoc.jar"; do
      first=""
      IFS= read -r first < "$f.asc" 2> /dev/null || true
      first=${first%$'\r'} # Gradle writes CRLF on Windows
      [[ "$first" == "-----BEGIN PGP SIGNATURE-----" ]] || err "no valid PGP signature for $(basename "$f")"
    done
  fi
done

if [[ "$SIGNED" == true ]]; then
  echo "signatures: checked (header of each .asc)"
else
  echo "signatures: NOT CHECKED (ORG_GRADLE_PROJECT_signingKey not set; dry run only)"
fi

if [[ "$LICENCE_TODO" == true ]]; then
  if [[ "$MODE" == publish ]]; then
    err "POM licence is still TODO. Decide ADR-0011 and set the licence in proofstone.publish.gradle.kts."
  else
    echo "WARNING: POM licence is TODO (ADR-0011 is Proposed). --publish will refuse until it is decided."
  fi
fi

if ((ERRORS > 0)); then
  die "$ERRORS check(s) failed. See above."
fi
echo "artefacts: ${#MODULES[@]} modules x (pom, jar, sources, javadoc) present, jars readable, POM complete"

if [[ "$MODE" == dry-run ]]; then
  echo "DRY RUN OK. Nothing uploaded. Staging: $STAGING"
  exit 0
fi

echo "== bundle and upload (USER_MANAGED: release it in the portal UI, the script does not release)"
BUNDLE="build/central-bundle-$VERSION.zip"
rm -f "$BUNDLE"
jar --create --file "$BUNDLE" -C "$STAGING" .
auth=$(printf '%s:%s' "$CENTRAL_PORTAL_USERNAME" "$CENTRAL_PORTAL_PASSWORD" | base64 | tr -d '\n')
# The bearer header goes through curl's config on stdin, so it never appears in the process list.
response=$(printf 'header = "Authorization: Bearer %s"\n' "$auth" \
  | curl -sS --fail-with-body -K - -X POST -F "bundle=@$BUNDLE" \
    "https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED")
echo "upload accepted. Deployment id: $response"
echo "Next: check the deployment at https://central.sonatype.com/publishing/deployments and publish it there by hand."
