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
- Class `FixtureEvent` (T-M6-111): a Bukkit event of this plugin, never fired. The agent contract test subscribes a probe to it, to check
  that probing another plugin's event logs no cross-plugin class-loading WARN. The plugin loads it on enable and soft-depends on the agent,
  so the agent is first in the plugin order.
- Command `fixture-getserver <player>` (T-M7-12): sends `GetServer` on the `BungeeCord` plugin channel for that player and
  logs the proxy's answer as `fixture BungeeCord reply: GetServer <server>`. The topology conformance P04 reads that line from
  the container log. It sends one message and records the reply; it does nothing else.
- Command `fixture-resourcepack <player> <url> <sha1> <required>` (T-M8-10): pushes that resource pack to the player in play
  (`Player#addResourcePack`, by reflection, Bukkit 1.20.3+), as a plugin under test would. The conformance scenarios C-R01
  and C-R02 use it because Paper fires `PlayerResourcePackStatusEvent` only for a play-phase answer.

## How to test
- `./gradlew :platforms:bukkit:agent:integrationTest`: `AgentContractIT` copies this jar into the Paper container with
  `PaperHost` and replays the config and reload cases.
