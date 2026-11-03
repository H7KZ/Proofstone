# Glossary (ubiquitous language)

Use these terms exactly, in code, docs, issues and PRs. `docsCheck` warns on known synonyms.

| Term | Meaning | Avoid saying |
|---|---|---|
| **Agent** | The in-server component (a plugin, mod or script pack) that the harness installs next to the jar under test. It performs setup and reads server truth through the wire protocol. It never interferes with *act*. | helper plugin, test plugin |
| **Act phase** | The part of a test where only the **client** interacts (opens, clicks, chats). The agent may only observe. | — |
| **Bridged cell** | A cell where the driver's protocol differs from the server's, so ViaProxy translates between them. | proxied cell |
| **Capability** | A feature a cell does or doesn't offer, such as `FOLIA_REGIONS`, `BEDROCK_CLIENT`, `NMS`, `PROXY_TOPOLOGY` or `DATAPACKS`. Tests declare the capabilities they require. | feature flag |
| **Cell** | One concrete environment that a test runs in: platform × version × Java × driver × topology. | env, config |
| **Client** | A connected fake player, as tests see it. It is backed by a *driver*. | bot (fine in prose, but the API name is `Client`) |
| **Conformance suite** | The harness self-tests for one platform pack. They check known vanilla behaviour, such as the vanilla anvil or a chest move, on every cell **before** plugin tests run. A failing conformance cell marks plugin results on that cell as *invalid*, not as failed. | smoke tests |
| **Control run** | A run of the plugin's suite **without** the plugin jar. Any test that still passes is *vacuous* and gets flagged. | negative run |
| **Differential run** | The same test run through two independent drivers (primary and reference) on the same cell. If their observations disagree, that is a harness bug. | — |
| **Driver** | An implementation of the client SPI, for example `mcpl` (MCProtocolLib, primary), `mineflayer` (reference) or `bedrock`. | bot library |
| **Flake** | A test whose verdict changes across identical runs. It is always a bug to investigate and never retried until green. | — |
| **Invalid (verdict)** | The cell's conformance failed, so no plugin verdict exists for that cell. | skipped |
| **Oracle** | A source of truth used in an assertion. There are two: **server oracle** (the agent) and **client oracle** (the driver's view). A *dual-oracle assertion* requires both to agree. | — |
| **Platform pack** | Everything needed to support one server type: the host image recipe, the agent build, the capability list, the conformance profile and the gotchas doc. | adapter, integration |
| **Repro bundle** | The artefacts attached to every failure: server log, agent transcript, driver packet log, `cell.json` with exact versions, and the command to re-run. | — |
| **Topology** | The arrangement of containers for a cell, for example a single server, a proxy with backends, or Geyser with a server. | — |
| **Vacuous test** | A test that passes even when the behaviour under test is absent. The control run detects these. | — |
| **versions.yml** | The single source of truth for which versions exist, how to run them and their status (`required`, `experimental` or `retired`). | — |
