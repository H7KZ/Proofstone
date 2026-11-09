# Golden files: wire protocol v1

Each case is a pair `<endpoint>/<case>.request.json` and `<endpoint>/<case>.response.json`. Every agent's contract
test replays the request and compares the response with `GoldenMatcher` (`proofstone-protocol` testFixtures).
Spec: [agent protocol](../../docs/specs/agent-protocol.md) §4.

## Format

Request:

- `method`, `path`: the HTTP method and the path, with no host.
- `body`: optional JSON body.
- `auth`: optional, default `true`. `false` means the harness sends no `Authorization` header.

Response:

- `status`: the HTTP status code.
- `body`: the JSON envelope (`{"ok": …, "data": …}` or `{"ok": false, "error": …}`).

## Placeholders

A placeholder is a JSON string whose whole value is one of the tokens below. The matcher checks the shape of the
actual value. It does not ignore it. Any other value in angle brackets is an error.

| Token | Matches |
|---|---|
| `"<uuid>"` | a lower-case UUID (`uuid` fields) |
| `"<time>"` | an ISO-8601 instant with `Z` (`time` fields) |
| `"<requestId>"` | a non-blank string (`X-Proofstone-Request-Id`) |
| `"<string>"` | any string. Used for `raw` and for free text such as `message` |
| `"<integer>"` | a non-negative JSON integer. Used for volatile counters: ticks, log and event cursors, event `tick` |

`raw` is server-native output. Never write a version-specific value there; it is always `"<string>"`.

## Fixture state

Cases under `players/` share one player, `Alex`, in the harness's fixture world. Replay runs them in this order, after
a `POST /v1/players/Alex/reset`:

1. `players/empty-inventory`
2. `players/give-enchanted-sword`
3. `players/read-after-give`

The fixture spawn is `world` at `(0.5, 64.0, 0.5)`: block `(0, 64, 0)`, centred. The harness sets the world spawn to `(0, 63, 0)` and puts a stone block there, so the reset lands the player on it; the contract test does this in setup (T-M1-05).

## Cases

| Case | Shows |
|---|---|
| `health/ok` | readiness and version fields |
| `health/unauthorised` | 401 without a token |
| `players/not-found` | 404 `PLAYER_NOT_FOUND` for an unknown player |
| `players/empty-inventory` | reset state: empty inventory, no cursor, no open window |
| `players/give-enchanted-sword` | give with enchant level 255. Paper 26.3 stores at most 255 and reads larger levels back as 255 (docs/platforms/paper.md); the agent never clamps, so the server's value is the truth |
| `players/read-after-give` | the same item read back, enchant intact |
| `command/reply` | console command output |
| `world/block-get` | `GET /v1/world/block` reply: namespaced type and state object (v1.1) |
| `world/block-put` | `PUT /v1/world/block`: request body `block.json#/$defs/write`, reply is the block (v1.1) |
| `plugins/config-patch` | `PATCH /v1/plugins/{id}/config` sets a path, reply `{}` (v1.1) |
| `plugins/config-read` | `GET /v1/plugins/{id}/config?path=` reads the patched value (v1.1) |
| `plugins/config-restore` | `POST /v1/plugins/{id}/config/restore` restores the snapshot, reply `{}` (v1.1) |
| `plugins/config-read-after-restore` | the same path reads its original value `hi` after restore (v1.1) |
| `plugins/reload` | `POST /v1/plugins/{id}/reload` with no body: the plugin is disabled and enabled, reply `{}` (v1.1, T-M2-03) |
| `plugins/reload-hook` | the same route with `proofstone-reload-command` (`reload.json`): the named console command is the hook, reply `{}` (v1.1, T-M2-03) |
| `ticks/read` | `GET /v1/ticks`: global counter, `<integer>` because it varies (v1.1) |
| `ticks/await` | `POST /v1/ticks/await` with `{n: 1}` replies with the counter (v1.1) |
| `events/subscribe` | `POST /v1/events/subscribe` for `PlayerJoinEvent` replies `{probeId}` (v1.1) |
| `events/poll` | `GET /v1/events/probe-1?since=0`: one captured event with getters serialised (v1.1) |
| `log/read` | `GET /v1/log?since=0&minLevel=WARN`: one record with a stack trace (v1.1) |
| `players/open-anvil` | `players/read-after-give` with an open anvil window: `openWindow` in `player-state.json` (v1.1) |
| `snapshot/read` | `GET /v1/snapshot` after the give: Alex with the sword, and the two fixture plugins with their config (`snapshot.json`, additive in v1.1, T-M4-12) |
| `health/proxy-ok` | a proxy agent's health: platform `velocity`, capabilities `players` and `proxy` (v1.2, T-M7-03) |
| `proxy/servers` | `GET /v1/proxy/servers`: the two default backends (v1.2) |
| `proxy/server-get` | `GET /v1/proxy/servers/survival`: one backend (v1.2) |
| `proxy/server-not-found` | 404 `SERVER_NOT_FOUND` with `detail {name}` (v1.2) |
| `players/proxy-first-join` | proxy `GET /v1/players/Alex` on the first server: `previousServer` is an explicit `null` (v1.2) |
| `players/proxy-switched` | the same after a switch lobby to survival (v1.2) |

**Proxy cases (T-M7-03).** The six v1.2 cases are checked against their schemas by this module's `GoldenContractTest`
only. The Bukkit agent does not replay them (a backend never serves them), and it is not in its case list. The proxy
agents (T-M7-05, T-M7-06) replay them.

**Replay order and setup (T-M2-02).** Cases that need setup:

- `events/subscribe` must replay before the first `players/Alex` case, because that case joins Alex. `events/poll`
  then reads the join: the probe is `probe-1`, the first probe a fresh agent creates.
- `players/open-anvil` needs the test to open an anvil before the read. It is still pending (openWindow, not in
  T-M2-02), and replays after `players/read-after-give`.

**Pending cases.** Only `players/open-anvil` is in `GoldenContractTest` (this module) only: it is not in the agent's
replay lists yet (not in `AgentContractIT.CASES`, and not in the agent's `GoldenContractTest` case list). The openWindow
card adds it when the endpoint is served. Every other case is served and replayed.

The `health/ok` golden moved from protocol `1.1` to `1.2` for T-M7-03 (the only edit to an existing golden), and from `1.0` to `1.1` in T-M2-01. The agent's `PROTOCOL` constant matches it. T-M2-03
added the `config` and `reload` capabilities to it and T-M2-02 added `world`, `events`, `log` and `ticks`. `reload.json`
is additive, so the protocol stays at `1.1`.

The config cases use the conformance fixture plugin (`platforms/bukkit/fixture`, plugin `proofstone-fixture`,
`greeting: hi`). The contract test copies its jar into the container. On enable the fixture logs one WARNING with an
exception, which is the record `log/read` expects.

## Adding a case

1. Add both files under the endpoint folder.
2. Add the case to the list in `GoldenContractTest`, to `CASES` in the agent's `AgentContractIT` (T-M1-05, in order),
   and to its schema table.
3. Keep the new case valid against `schemas/`. Bump the protocol version if the schema changes (AGENTS.md §3).
