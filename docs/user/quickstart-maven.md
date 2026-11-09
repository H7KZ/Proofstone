# Quickstart: Maven

**Status: not available. The Maven example does not exist yet.**

Nothing on this page has been run or verified. Do not treat any command here as working.

## What is missing

- The Maven example project is not in the repository. The card for it is
  [T-M6-04](../plan/tasks/M6-dogfood-release.md). The Gradle twin is in `examples/gradle-paper-plugin/`.
- Maven is not installed on the machine where the Gradle example was written, so no Maven build was run.
- The intended setup (from T-M6-04): failsafe runs the `*E2E.java` tests under the `e2e` profile, with a plugin and two
  toggleable bugs, the same as the Gradle example.
- Proofstone's artefacts are not published. Until they are, a Maven project would have to read them from `mavenLocal`
  after `./gradlew publishToMavenLocal` (see [quickstart-gradle.md](quickstart-gradle.md#2-publish-proofstones-libraries-to-mavenlocal)).
  That path is not verified for Maven either.

## What to use now

Use the [Gradle quickstart](quickstart-gradle.md). It is the verified path, inside this repository.
