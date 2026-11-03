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
| `ticks/read` | `GET /v1/ticks`: global counter, `<integer>` because it varies (v1.1) |
| `ticks/await` | `POST /v1/ticks/await` with `{n: 1}` replies with the counter (v1.1) |
| `events/subscribe` | `POST /v1/events/subscribe` for `PlayerJoinEvent` replies `{probeId}` (v1.1) |
| `events/poll` | `GET /v1/events/probe-1?since=0`: one captured event with getters serialised (v1.1) |
| `log/read` | `GET /v1/log?since=0&minLevel=WARN`: one record with a stack trace (v1.1) |
| `players/open-anvil` | `players/read-after-give` with an open anvil window: `openWindow` in `player-state.json` (v1.1) |

**Pending cases (T-M2-01).** The rows from `world/block-get` to `players/open-anvil` are in
`GoldenContractTest` (this module) only. They are **not** in the agent's replay lists yet: not in
`AgentContractIT.CASES`, and not in the agent's `GoldenContractTest` case list. T-M2-02 and T-M2-03 add each
one to those lists when the endpoint is served. Two cases need a setup the replay must provide:

- `events/poll` names the probe `probe-1`. The M2-02 replay must subscribe to `PlayerJoinEvent` first and
  make the agent's probe id `probe-1`, or change this case's path to the id it uses.
- `players/open-anvil` needs the test to open an anvil before the read. Replay it after `players/read-after-give`.

The `health/ok` golden moved from protocol `1.0` to `1.1` (T-M2-01). The agent's `PROTOCOL` constant
matches it.

## Adding a case

1. Add both files under the endpoint folder.
2. Add the case to the list in `GoldenContractTest`, to `CASES` in the agent's `AgentContractIT` (T-M1-05, in order),
   and to its schema table.
3. Keep the new case valid against `schemas/`. Bump the protocol version if the schema changes (AGENTS.md §3).
