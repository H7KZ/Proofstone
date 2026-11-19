#!/usr/bin/env bash
# Clean-room consumer check (T-M6-105, ADR-0012, ADR-0019). Copies the Gradle and Maven example sources to a scratch directory
# outside the repository and runs their real-server e2e suite there. Each run gets a fresh empty Maven repository (or
# GRADLE_USER_HOME) and no mavenLocal, so the four io.github.h7kz.proofstone artefacts must come from Maven Central.
# The script fails (non-zero) when any Proofstone artefact is not downloaded from Central, when no artefact of the four is
# seen, or when a suite XML is not `skipped="0" failures="0" errors="0"`.
#
# Usage: PROOFSTONE_ACCEPT_EULA=true bash scripts/consumer-cleanroom.sh [gradle|maven|all] [run-number]
#   One call is one run. Evidence goes to <evidence>/<suite>-run-<n> and an existing directory is never overwritten.
#   The suite runs under scripts/dockerlock.sh inside this script: do not wrap this script in dockerlock.
#   Env overrides: PROOFSTONE_CLEANROOM_WORK (scratch), PROOFSTONE_CLEANROOM_EVIDENCE, PROOFSTONE_CELLS (default paper@26.3),
#   MVN (Maven 3.9.9 binary), PROOFSTONE_JDK21 (Gradle JVM, toolchains), PROOFSTONE_JDK26 (Maven JVM, Paper plugin compile).
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
REPO_MIXED="$(cygpath -m "$REPO" 2>/dev/null || echo "$REPO")"
SUITE="${1:-all}"
RUN="${2:-1}"
PARENT="$(dirname "$REPO")"
WORK="${PROOFSTONE_CLEANROOM_WORK:-$PARENT/cleanroom-work}"
EVIDENCE="${PROOFSTONE_CLEANROOM_EVIDENCE:-$PARENT/cleanroom-evidence}"
CELL="${PROOFSTONE_CELLS:-paper@26.3}"
MVN="${MVN:-/c/Users/honzi/tools/apache-maven-3.9.9/bin/mvn}"
JDK21="${PROOFSTONE_JDK21:-/c/Program Files/Java/jdk-21}"
JDK26="${PROOFSTONE_JDK26:-/c/Program Files/Java/jdk-26}"
ARTIFACTS="proofstone-api proofstone-core proofstone-protocol proofstone-drivers-mcpl"
CENTRAL_RE='^https://(repo\.maven\.apache\.org|repo1\.maven\.org)/maven2/'

fail() { echo "consumer-cleanroom: $*" >&2; exit 1; }

[ "${PROOFSTONE_ACCEPT_EULA:-}" = "true" ] || fail "PROOFSTONE_ACCEPT_EULA=true must be set in the command environment"
case "$SUITE" in gradle|maven|all) ;; *) fail "suite must be gradle, maven or all" ;; esac
[[ "$RUN" =~ ^[1-9][0-9]*$ ]] || fail "run number must be a positive integer"
[ -f "$REPO/platforms/bukkit/agent/build/libs/agent-all.jar" ] || fail "agent jar missing: build :platforms:bukkit:agent:shadowJar first"
[ -f "$REPO/gradle/libs.versions.toml" ] || fail "gradle/libs.versions.toml missing"

# Every io.github.h7kz.proofstone download must come from Central, and each of the four artefacts must show up from Central.
# $1 = log file, $2 = gradle|maven, $3 = file to write the resolved list to.
check_central() {
  local log="$1" kind="$2" list="$3" a bad=0
  if [ "$kind" = gradle ]; then
    grep -oE 'https?://[^ ,]*/io/github/h7kz/proofstone/[^ ,]*' "$log" | sort -u > "$list" || true
  else
    # Maven prints "Downloaded from <repo-id>: <url>" (and "Downloading from ...") for every fetch.
    grep -oE 'from [A-Za-z0-9._-]+: https?://[^ ,]*/io/github/h7kz/proofstone/[^ ,]*' "$log" \
      | sed -E 's/^from ([A-Za-z0-9._-]+): /\1 /' | sort -u > "$list" || true
    if grep -vE '^central https://' "$list" | grep -q .; then
      echo "NOT CENTRAL (repo id):"; grep -vE '^central https://' "$list" || true; bad=1
    fi
  fi
  local urls
  if [ "$kind" = gradle ]; then urls=$(cat "$list"); else urls=$(awk '{print $2}' "$list"); fi
  if [ -n "$urls" ]; then
    while IFS= read -r u; do
      [ -n "$u" ] || continue
      [[ "$u" =~ $CENTRAL_RE ]] || { echo "NOT CENTRAL (host): $u"; bad=1; }
    done <<< "$urls"
  fi
  for a in $ARTIFACTS; do
    if ! grep -qE "/io/github/h7kz/proofstone/$a/0\.1\.0/$a-0\.1\.0\.jar" "$list"; then
      echo "MISSING from Central log: $a 0.1.0 jar"; bad=1
    fi
  done
  return $bad
}

# Every suite XML must show skipped="0" failures="0" errors="0" and at least one test.
check_xml() {
  local dir="$1" f tag n=0 bad=0
  for f in "$dir"/TEST-*.xml; do
    [ -e "$f" ] || { echo "no TEST-*.xml in $dir"; return 1; }
    n=$((n + 1))
    tag=$(grep -o '<testsuite [^>]*>' "$f" | head -1 || true)
    for attr in skipped failures errors; do
      grep -q " $attr=\"0\"" <<< "$tag" || { echo "NOT GREEN $(basename "$f"): $tag"; bad=1; }
    done
    grep -qE ' tests="[1-9][0-9]*"' <<< "$tag" || { echo "NO TESTS $(basename "$f")"; bad=1; }
  done
  echo "suite XML files checked: $n"
  return $bad
}

# Copies the run's evidence aside. Refuses to overwrite an earlier run's evidence.
collect() { # $1 = suite, $2 = scratch dir, $3 = xml dir, $4 = bundle dir
  local dest="$EVIDENCE/$1-run-$RUN"
  [ -e "$dest" ] && fail "evidence $dest exists: a run number is never reused"
  mkdir -p "$dest/xml" "$dest/bundles"
  cp -r "$3"/. "$dest/xml/" 2>/dev/null || true
  cp "$4"/*.jar "$dest/bundles/" 2>/dev/null || true
  cp "$2/run.log" "$dest/$1.log"
  cp "$2/central-resolved.txt" "$dest/central-resolved.txt" 2>/dev/null || true
  cp "$2/central-check.txt" "$dest/central-check.txt" 2>/dev/null || true
  cp "$2/xml-check.txt" "$dest/xml-check.txt" 2>/dev/null || true
  cp "$2"/consumer-build/* "$dest/" 2>/dev/null || true
  echo "evidence: $dest"
}

run_gradle() {
  local dir="$WORK/gradle-run-$RUN" proj="$WORK/gradle-run-$RUN/project" rc=0 repo_mixed="$REPO_MIXED"
  rm -rf "$dir"; mkdir -p "$proj/gradle" "$dir/consumer-build"
  cp -r "$REPO/examples/gradle-paper-plugin/src" "$proj/"
  cp "$REPO/gradle/libs.versions.toml" "$proj/gradle/"
  cp -r "$REPO/gradle/wrapper" "$proj/gradle/"
  cp "$REPO/gradlew" "$proj/"
  chmod +x "$proj/gradlew"

  # settings: the three repositories the README names, no mavenLocal, no project repositories.
  cat > "$proj/settings.gradle.kts" <<'EOF'
// Consumer settings for the clean-room check (T-M6-105). Only Maven Central, the Paper repo and the opencollab repo (README).
rootProject.name = "gradle-paper-plugin"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
    }
}
EOF

  # build file: the example's build.gradle.kts with three monorepo-only links replaced: the java-host convention plugin (its
  # effective settings are re-added below), the repo-root paths (pointed at this worktree), and the monorepo publish and
  # agent-build tasks (replaced by the prebuilt agent jar, which must already exist).
  sed -e 's/id("proofstone.java-host")/`java-library`/' \
      -e "s|rootProject.projectDir.absolutePath|\"$repo_mixed\"|" \
      -e "s|rootProject.layout.projectDirectory.file(\"platforms/|layout.projectDirectory.file(\"$repo_mixed/platforms/|" \
      -e 's|dependsOn("jar", ":platforms:bukkit:agent:shadowJar")|dependsOn("jar")|' \
      "$REPO/examples/gradle-paper-plugin/build.gradle.kts" \
    | awk '/^    dependsOn\($/ {skip=1; next} skip && /^    \)$/ {skip=0; next} !skip {print}' > "$proj/build.gradle.kts"
  cat >> "$proj/build.gradle.kts" <<'EOF'

// Effective settings of proofstone.java-host (the convention plugin is not on this classpath): UTF-8 and warnings as errors.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Werror")
}
EOF
  # The transform must have removed every monorepo-only link, or the copy is not a clean consumer.
  for pat in publishToMavenLocal shadowJar java-host mavenLocal 'rootProject.projectDir' 'rootProject.layout'; do
    if grep -v '^[[:space:]]*//' "$proj/build.gradle.kts" | grep -q "$pat"; then fail "gradle consumer build still contains '$pat'"; fi
  done
  cp "$proj/build.gradle.kts" "$proj/settings.gradle.kts" "$dir/consumer-build/"
  cp "$REPO/gradle/libs.versions.toml" "$dir/consumer-build/"

  cd "$proj"
  export GRADLE_USER_HOME="$dir/gradle-home" JAVA_HOME="$JDK21"
  set +e
  bash "$REPO/scripts/dockerlock.sh" ./gradlew e2eTest -Dproofstone.cells="$CELL" --no-parallel --max-workers=1 \
    --no-daemon --info --console=plain > "$dir/run.log" 2>&1
  rc=$?
  set -e
  cd "$REPO"

  local cc=0
  check_central "$dir/run.log" gradle "$dir/central-resolved.txt" > "$dir/central-check.txt" 2>&1 || cc=$?
  cat "$dir/central-check.txt"
  local xc=0
  check_xml "$proj/build/test-results/e2eTest" > "$dir/xml-check.txt" 2>&1 || xc=$?
  cat "$dir/xml-check.txt"
  collect gradle "$dir" "$proj/build/test-results/e2eTest" "$proj/build/libs"
  if [ $rc -ne 0 ] || [ $cc -ne 0 ] || [ $xc -ne 0 ]; then
    echo "VERDICT gradle-run-$RUN: RED (gradle rc=$rc central=$cc xml=$xc)"; return 1
  fi
  echo "VERDICT gradle-run-$RUN: GREEN"
}

run_maven() {
  local dir="$WORK/maven-run-$RUN" proj="$WORK/maven-run-$RUN/project" rc=0 repo_mixed="$REPO_MIXED"
  rm -rf "$dir"; mkdir -p "$proj" "$dir/consumer-build"
  cp -r "$REPO/examples/maven-paper-plugin/src" "$proj/"
  cp "$REPO/examples/maven-paper-plugin/pom.xml" "$proj/"
  # The example pom declares central before papermc (T-M6-107): Maven tries repositories in declaration order, and papermc is a
  # proxy that also serves Central. The pom is used unedited; the guard below fails if that order is lost.
  local cl pl
  cl=$(grep -n '<id>central</id>' "$proj/pom.xml" | head -1 | cut -d: -f1 || true)
  pl=$(grep -n '<id>papermc</id>' "$proj/pom.xml" | head -1 | cut -d: -f1 || true)
  [ -n "$cl" ] && [ -n "$pl" ] && [ "$cl" -lt "$pl" ] || fail "maven consumer pom must declare central before papermc (T-M6-107)"
  cp "$proj/pom.xml" "$dir/consumer-build/"
  # Empty user settings: no mirrors or profiles from ~/.m2 can redirect a download away from Central.
  printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$dir/empty-settings.xml"
  if sed '/<!--/,/-->/d' "$proj/pom.xml" | grep -q 'mavenLocal'; then fail "maven consumer pom contains mavenLocal"; fi

  cd "$proj"
  export JAVA_HOME="$JDK26"
  set +e
  bash "$REPO/scripts/dockerlock.sh" "$MVN" -B -s "$dir/empty-settings.xml" \
    -Dmaven.repo.local="$dir/m2" \
    -Dproofstone.repo.root="$repo_mixed" \
    -Dproofstone.cells="$CELL" \
    -Pe2e verify > "$dir/run.log" 2>&1
  rc=$?
  set -e
  cd "$REPO"

  local cc=0
  check_central "$dir/run.log" maven "$dir/central-resolved.txt" > "$dir/central-check.txt" 2>&1 || cc=$?
  cat "$dir/central-check.txt"
  local xc=0
  check_xml "$proj/target/failsafe-reports" > "$dir/xml-check.txt" 2>&1 || xc=$?
  cat "$dir/xml-check.txt"
  collect maven "$dir" "$proj/target/failsafe-reports" "$proj/target"
  if [ $rc -ne 0 ] || [ $cc -ne 0 ] || [ $xc -ne 0 ]; then
    echo "VERDICT maven-run-$RUN: RED (mvn rc=$rc central=$cc xml=$xc)"; return 1
  fi
  echo "VERDICT maven-run-$RUN: GREEN"
}

mkdir -p "$WORK" "$EVIDENCE"
overall=0
case "$SUITE" in
  gradle) run_gradle || overall=1 ;;
  maven) run_maven || overall=1 ;;
  all) run_gradle || overall=1; run_maven || overall=1 ;;
esac
exit $overall
