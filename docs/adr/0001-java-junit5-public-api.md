# ADR-0001: Public test API is Java + JUnit 5

- Status: Accepted
- Date: 2026-11-03

## Context
- Most plugin and mod authors write Java or Kotlin and already use JUnit 5, often with MockBukkit.
- The Maven and Gradle split is roughly even, so the API must work from both.
- The existing JS/TS framework (Plugwright) is Gradle-only and asks authors to write tests in a second
  language.

## Decision
- The only public API is `testificate-api`: a JUnit 5 extension (`@TestTemplate`-based cell matrix)
  plus a fluent Java DSL, written so it is also pleasant from Kotlin.
- It ships to Maven Central with Java 17 bytecode. Using it needs only a test dependency and Docker.

## Consequences
- Authors stay in their language and test runner, and IDE debugging works.
- Non-JVM authors (Bedrock add-on developers) need a separate front end later. The wire protocol is
  language-neutral, so a TS front end is possible (M10).
- The API follows semver and every public type is documented. Breaking changes require a major version.

## Alternatives considered
- **TS/JS tests (Plugwright style):** a second language and toolchain for JVM authors. Rejected as the
  primary API.
- **YAML scenario files:** too weak for real assertions and loops. They may come later as a thin layer
  on top of the Java API.
