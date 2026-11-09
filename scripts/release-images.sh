#!/usr/bin/env bash
# GHCR image release for Proofstone (T-M6-02, docs/release.md). Local only: no CI, no workflow.
#   --dry-run (default): builds mineflayer-ref and spigot-buildtools locally (under scripts/dockerlock.sh), checks the
#                        pins, prints the image ids and writes build/release-images/dry-run.env. Pushes nothing.
#   --push:              refuses unless PROOFSTONE_RELEASE_CONFIRM=yes-push, GHCR_USERNAME and GHCR_TOKEN are set, the
#                        release version is not *-local and the git tree is clean. Pushes mineflayer-ref under an immutable
#                        content tag (<version>-ctx<hash>), never latest. Records the digest that `docker push` reports in
#                        build/release-images/pushed.env. spigot-buildtools is never pushed (its Dockerfile forbids it).
#                        The login uses a throwaway DOCKER_CONFIG, so the maintainer's docker credentials are not touched.
#   --write-digests:     writes the digest from build/release-images/pushed.env into versions.yml and prints the diff for the
#                        PR. Refuses without a pushed digest (a dry run never writes one). Commits nothing.
# Secrets come from the environment only. This script prints their names, never their values.
# Usage: bash scripts/release-images.sh [--dry-run|--push|--write-digests]
set -euo pipefail
cd "$(dirname "$0")/.."

MODE=dry-run
case "${1:---dry-run}" in
  --dry-run) MODE=dry-run ;;
  --push) MODE=push ;;
  --write-digests) MODE=write-digests ;;
  -h | --help) sed -n '2,15p' "$0"; exit 0 ;;
  *) echo "usage: bash scripts/release-images.sh [--dry-run|--push|--write-digests]" >&2; exit 2 ;;
esac

die() { echo "release-images: $MODE: $*" >&2; exit 1; }

GHCR_OWNER=h7kz
MF_IMAGE="ghcr.io/$GHCR_OWNER/proofstone-mineflayer-ref"
OUT_DIR=build/release-images
VERSION=$(sed -n 's/^proofstone = "\(.*\)"$/\1/p' gradle/libs.versions.toml)
[[ -n "$VERSION" ]] || die "cannot read the proofstone version from gradle/libs.versions.toml"
[[ -f versions.yml ]] || die "versions.yml missing"

# Runs one docker command under the machine-wide Docker lock (only one Docker run at a time, see docs/release.md).
docker_locked() { bash scripts/dockerlock.sh docker "$@"; }

# Content hash of the image's build inputs: git blob ids of the tracked files. Same inputs, same tag.
ctx_hash() { git ls-files -s -- "$1" | sha256sum | cut -c1-12; }

# Reads the pins from versions.yml. Each lookup must find exactly one value, or the script fails loudly.
one() { # one <label> <value>
  local n
  n=$(printf '%s\n' "$2" | grep -c . || true)
  [[ "$n" == 1 ]] || die "versions.yml: expected exactly one $1, found $n"
  printf '%s' "$2"
}
MF_LIB_VERSION=$(one "mineflayer-ref version" "$(sed -n 's/.*mineflayer-ref:.*version: "\([^"]*\)".*/\1/p' versions.yml)")
BT_BUILD=$(one "buildtools build" "$(sed -n 's/.*buildtools: { build: \([0-9]*\),.*/\1/p' versions.yml)")
BT_SHA=$(one "buildtools sha256" "$(sed -n 's/.*buildtools: .*sha256: "\([0-9a-f]\{64\}\)".*/\1/p' versions.yml)")
JAVA_TAGS=$(sed -n 's/^ *\(java[0-9]*\): *"eclipse-temurin@sha256:[0-9a-f]\{64\}".*/\1/p' versions.yml)
[[ -n "$JAVA_TAGS" ]] || die "versions.yml: no spigot builder JDK digests found"
jdk_for() { one "builder JDK for $1" "$(sed -n "s/^ *$1: *\"\(eclipse-temurin@sha256:[0-9a-f]\{64\}\)\".*/\1/p" versions.yml)"; }

GIT_SHA=$(git rev-parse HEAD)
GIT_CREATED=$(git log -1 --format=%cI)
if git diff --quiet HEAD -- . && [[ -z "$(git ls-files --others --exclude-standard)" ]]; then DIRTY=false; else DIRTY=true; fi

# Pin checks on the inputs. A failure stops the run before any build.
pin_check() {
  grep -E '^FROM ' images/mineflayer-ref/Dockerfile | grep -qE '@sha256:[0-9a-f]{64}$' \
    || die "images/mineflayer-ref/Dockerfile: a FROM line is not pinned by digest"
  grep -E '^FROM ' images/spigot-buildtools/Dockerfile | grep -qE '^FROM \$\{JDK_IMAGE\}$' \
    || die "images/spigot-buildtools/Dockerfile: FROM must take the digest-pinned JDK_IMAGE build arg"
  [[ "$MF_LIB_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "mineflayer-ref version is not an exact version: $MF_LIB_VERSION"
}

if [[ "$MODE" == write-digests ]]; then
  ENV_FILE="$OUT_DIR/pushed.env"
  [[ -f "$ENV_FILE" ]] || die "refusing. No pushed digest at $ENV_FILE. Run --push first (a dry run never writes digests)."
  env_get() { sed -n "s/^$1=//p" "$ENV_FILE"; }
  [[ "$(env_get mode)" == push ]] || die "refusing. $ENV_FILE was not written by --push."
  [[ "$(env_get version)" == "$VERSION" ]] || die "refusing. pushed version $(env_get version) differs from $VERSION."
  [[ "$(env_get image)" == "$MF_IMAGE" ]] || die "refusing. pushed image $(env_get image) differs from $MF_IMAGE."
  DIGEST=$(env_get digest)
  [[ "$DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || die "refusing. pushed digest is not a sha256 digest: '$DIGEST'"

  one "mineflayer-ref row" "$(grep '^  mineflayer-ref:' versions.yml || true)" > /dev/null
  if grep -q '^  mineflayer-ref:.*digest:' versions.yml; then
    sed -i "/^  mineflayer-ref:/s|digest: \"sha256:[0-9a-f]*\"|digest: \"$DIGEST\"|" versions.yml
  else
    sed -i "/^  mineflayer-ref:/s|max-native: \"\([^\"]*\)\" }|max-native: \"\1\", image: \"$MF_IMAGE\", digest: \"$DIGEST\" }|" versions.yml
  fi
  grep -q "digest: \"$DIGEST\"" versions.yml || die "versions.yml was not updated (the mineflayer-ref row has an unexpected shape)"
  echo "== versions.yml diff for the PR (not committed):"
  git diff -- versions.yml || true
  echo "write-digests: OK. Open the PR yourself. Pull by digest is still UNVERIFIED."
  exit 0
fi

pin_check
echo "== inputs: version $VERSION, mineflayer-ref lib $MF_LIB_VERSION, buildtools $BT_BUILD, JDK tags: ${JAVA_TAGS//$'\n'/ }, tree dirty: $DIRTY"

if [[ "$MODE" == push ]]; then
  [[ "${PROOFSTONE_RELEASE_CONFIRM:-}" == "yes-push" ]] \
    || die "refusing. Set PROOFSTONE_RELEASE_CONFIRM=yes-push in the command environment (never in a file)."
  [[ -n "${GHCR_USERNAME:-}" ]] || die "refusing. Environment variable GHCR_USERNAME is not set (name only, value never printed)."
  [[ -n "${GHCR_TOKEN:-}" ]] || die "refusing. Environment variable GHCR_TOKEN is not set (name only, value never printed)."
  case "$VERSION" in
    *-local) die "refusing. version $VERSION is the local-only version. Set the release version in gradle/libs.versions.toml." ;;
  esac
  [[ "$DIRTY" == false ]] || die "refusing. The git tree is not clean. Commit or discard changes first."
fi

mkdir -p "$OUT_DIR"
LABELS=(
  --label "org.opencontainers.image.source=https://github.com/H7KZ/Proofstone"
  --label "org.opencontainers.image.revision=$GIT_SHA"
  --label "org.opencontainers.image.version=$VERSION"
  --label "org.opencontainers.image.created=$GIT_CREATED"
  --label "dev.proofstone.dirty=$DIRTY"
)

# Local image names for a dry run. The push retags the same build to the GHCR name.
MF_CTX=$(ctx_hash images/mineflayer-ref)
MF_LOCAL="proofstone-release/mineflayer-ref:$VERSION-ctx$MF_CTX"
SB_CTX=$(ctx_hash images/spigot-buildtools)

echo "== build mineflayer-ref -> $MF_LOCAL"
docker_locked build -f images/mineflayer-ref/Dockerfile -t "$MF_LOCAL" \
  "${LABELS[@]}" --label "dev.proofstone.context=$MF_CTX" --label "dev.proofstone.mineflayer=$MF_LIB_VERSION" images/mineflayer-ref
MF_ID=$(docker image inspect -f '{{.Id}}' "$MF_LOCAL")

SB_IDS=""
for tag in $JAVA_TAGS; do
  jdk=$(jdk_for "$tag")
  local_tag="proofstone-release/spigot-buildtools:$tag-ctx$SB_CTX"
  echo "== build spigot-buildtools ($tag, BuildTools $BT_BUILD) -> $local_tag"
  docker_locked build -f images/spigot-buildtools/Dockerfile -t "$local_tag" \
    --build-arg "JDK_IMAGE=$jdk" --build-arg "BUILDTOOLS_BUILD=$BT_BUILD" --build-arg "BUILDTOOLS_SHA256=$BT_SHA" \
    "${LABELS[@]}" --label "dev.proofstone.context=$SB_CTX" --label "dev.proofstone.buildtools=$BT_BUILD" \
    images/spigot-buildtools
  SB_IDS="$SB_IDS spigot-buildtools:$tag=$(docker image inspect -f '{{.Id}}' "$local_tag")"
done

if [[ "$MODE" == dry-run ]]; then
  {
    echo "mode=dry-run"
    echo "version=$VERSION"
    echo "mineflayer-ref.image=$MF_LOCAL"
    echo "mineflayer-ref.image_id=$MF_ID"
    for kv in $SB_IDS; do echo "$kv"; done
  } > "$OUT_DIR/dry-run.env"
  echo "== dry run digests (local image ids, not registry digests; nothing pushed)"
  echo "mineflayer-ref  $MF_LOCAL  $MF_ID"
  for kv in $SB_IDS; do echo "$kv"; done
  echo "DRY RUN OK. Written: $OUT_DIR/dry-run.env. Pins checked. Pull by digest: UNVERIFIED (nothing pushed)."
  exit 0
fi

# --push
export DOCKER_CONFIG
DOCKER_CONFIG=$(mktemp -d)
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username "$GHCR_USERNAME" --password-stdin > /dev/null

REMOTE="$MF_IMAGE:$VERSION-ctx$MF_CTX"
docker tag "$MF_LOCAL" "$REMOTE"
PUSH_LOG="$OUT_DIR/push-mineflayer-ref.log"
docker_locked push "$REMOTE" 2>&1 | tee "$PUSH_LOG"
DIGEST=$(sed -n 's/.*digest: \(sha256:[0-9a-f]\{64\}\) size:.*/\1/p' "$PUSH_LOG" | tail -1)
[[ "$DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || die "push succeeded but no digest was reported in $PUSH_LOG"
docker logout ghcr.io > /dev/null 2>&1 || true

cat > "$OUT_DIR/pushed.env" <<EOF
mode=push
version=$VERSION
image=$MF_IMAGE
tag=$VERSION-ctx$MF_CTX
digest=$DIGEST
git_sha=$GIT_SHA
pushed_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF
echo "push: OK. $REMOTE $DIGEST. Recorded in $OUT_DIR/pushed.env."
echo "spigot-buildtools: NOT pushed (its Dockerfile says it is never pushed; maintainer decision)."
echo "Next: bash scripts/release-images.sh --write-digests, then open the PR. Pull by digest: UNVERIFIED."
