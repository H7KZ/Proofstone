# ADR-0005: Testcontainers, pinned images, no bind mounts

- Status: Accepted
- Date: 2026-11-03

## Context
- The prototype hit a silent failure when a Git Bash POSIX path was passed as a Docker bind mount on
  Windows: the plugin folder was mounted empty, and the plugin was never loaded.
- The itzg image covers Paper, Folia and more, and was reliable in the prototype (boot in 19–30 s).

## Decision
- Testcontainers (2.x, JUnit 5) manages every container.
- The server host image is `itzg/minecraft-server`, pinned by digest, for families it supports. Packs may
  own images under `images/`.
- Inputs (plugin jars, agent, configs) are copied in with `copyFileToContainer`. **No bind mounts.**
- Readiness requires both the log line **and** the agent's `/v1/health`.
- E2E CI runs on Linux runners only.

## Consequences
- Everything behaves the same on Windows, macOS and Linux hosts with Docker.
- When Docker is missing, tests are skipped with a clear reason (never a silent pass).

## Alternatives considered
- **Run the server jar directly in the host JVM process tree:** no isolation, and the host needs the
  right Java version. Rejected.
- **docker-compose files:** a second orchestration layer. Rejected; topology is code in core.
