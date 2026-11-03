# Architecture Decision Records

An ADR records one decision: its context, the decision itself, the consequences, and the alternatives
that were rejected. **Accepted ADRs are not re-decided inside a task.** To challenge one, open an
"ADR challenge" issue. A successful challenge produces a new ADR that supersedes the old one.

| # | Title | Status |
|---|---|---|
| [0001](0001-java-junit5-public-api.md) | Public test API is Java + JUnit 5 | Accepted |
| [0002](0002-server-truth-via-agent.md) | Server truth comes from an in-server agent, never RCON or console | Accepted |
| [0003](0003-dual-oracle.md) | Every assertion uses two oracles | Accepted |
| [0004](0004-client-driver-latest-protocol-plus-viaproxy.md) | Primary driver: MCProtocolLib, newest protocol only, with ViaProxy for older servers | Accepted |
| [0005](0005-containers-testcontainers-no-bind-mounts.md) | Testcontainers, pinned images, no bind mounts | Accepted |
| [0006](0006-pin-everything.md) | Pin everything; upgrades only through gated PRs | Accepted |
| [0007](0007-no-sleeps-no-retries.md) | No sleeps, no auto-retries; flakes are bugs | Accepted |
| [0008](0008-versions-yml-single-source-of-truth.md) | `versions.yml` is the single source of truth for the matrix | Accepted |
| [0009](0009-gradle-monorepo.md) | Gradle monorepo; artefacts usable from Maven and Gradle | Accepted |
| [0010](0010-ai-first-maintenance-model-tiering.md) | AI-first maintenance with model tiering | Accepted |
| [0011](0011-licensing.md) | Licensing | **Proposed** (owner decides) |
| [0012](0012-name.md) | Project name: Testificate | Accepted |

Template: [0000-template.md](0000-template.md).
