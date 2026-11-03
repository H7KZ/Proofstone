# Conformance fixture plugin (`platforms:bukkit:fixture`)

## Purpose
A test-only Bukkit plugin, `proofstone-fixture`, that the agent's contract tests load on a real server. It has a
`config.yml` (`greeting: hi`) and a reload hook command, `fixture-reload`. The golden config cases
(`proofstone-protocol/golden/plugins/`) and the reload cases read and change it.

## Boundaries
- Test-only. Never shipped, never published, never a product.
- No logic beyond what the contract needs. It must not change other plugins or the server.
- Java 8 bytecode (`options.release = 8`), compiled against Spigot API 1.8.8 with no transitive dependencies.

## Public surface
- Jar: `build/libs/proofstone-fixture.jar`, built by `:platforms:bukkit:fixture:jar`.
- Plugin name `proofstone-fixture` (the `{id}` in `/v1/plugins/{id}/...`).
- Config key `greeting`, default `hi`.
- Command `fixture-reload`, the declared reload hook.

## How to test
- `./gradlew :platforms:bukkit:agent:integrationTest`: `AgentContractIT` copies this jar into the Paper container with
  `PaperHost` and replays the config and reload cases.
