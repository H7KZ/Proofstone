# Spec: agent wire protocol v1

Status: draft. The schemas live in `proofstone-protocol/schemas/*.json`. They are the source of truth,
and this page explains them.

## 1. Transport
- HTTP/1.1 over the Docker network. The agent listens on `0.0.0.0:${PROOFSTONE_AGENT_PORT:-25580}`
  inside the container.
- Every request requires the header `Authorization: Bearer ${PROOFSTONE_AGENT_TOKEN}`, a random value
  generated per cell. A missing or wrong token returns 401.
- All bodies are `application/json; charset=utf-8`.
- Responses use the envelope `{ "ok": true, "data": … }` or
  `{ "ok": false, "error": { "code", "message", "detail" } }`.
- Every request carries `X-Proofstone-Request-Id`. The agent logs it, so transcripts can be correlated.
- Timeouts are enforced server-side. The request body may set `timeoutMs`, which defaults to 10 000. When
  it runs out, the agent returns `{code:"TIMEOUT"}`, never a partial result.
- Threading: the agent runs each operation on the correct thread or region and replies only once it has
  completed (Folia: entity, region or global scheduler).

## 2. Endpoints (v1)

| Method and path | Purpose | Notes |
|---|---|---|
| `GET /v1/health` | readiness, agent version, protocol version, platform, MC version, capabilities | used by core readiness |
| `POST /v1/command` | run a console command, returning `{raw}` | setup only; output is best-effort |
| `GET /v1/players` | online players | — |
| `GET /v1/players/{name}` | `PlayerState` | inventory, cursor, open window, xp, gamemode, location, health |
| `POST /v1/players/{name}/reset` | reset to the fixture state | idempotent |
| `POST /v1/players/{name}/give` | body `Item` | built through the platform API |
| `PATCH /v1/players/{name}` | level, exp, gamemode, location, op | — |
| `GET/PUT /v1/world/block` | `{world, x, y, z}`, and `{type, state}` for PUT | — |
| `POST /v1/world/reset` | restore the fixture region | per-pack definition |
| `GET/PATCH /v1/plugins/{id}/config` | read and patch config by path | the agent snapshots on the first patch |
| `POST /v1/plugins/{id}/config/restore` | restore the snapshot bytes and reload the config (not the plugin; use `reload`) | core calls this after every test |
| `POST /v1/plugins/{id}/reload` | reload | disable/enable, or a declared hook |
| `POST /v1/events/subscribe` | `{class}`, returns `{probeId}` | reflective listener, MONITOR, read-only |
| `GET /v1/events/{probeId}?since=` | captured events, with getters serialised | — |
| `DELETE /v1/events/{probeId}` | unsubscribe | — |
| `GET /v1/ticks` | the current tick counter (per region on Folia: `{global, regions}`) | — |
| `POST /v1/ticks/await` | `{n}` | — |
| `GET /v1/log?since=&minLevel=` | captured log records with stack traces | root JUL/Log4j handler |
| `GET /v1/snapshot` | full-state dump for repro bundles and the leak detector | reply `{players, plugins}` (`snapshot.json`): every online `PlayerState` and each loaded plugin's `{name, version, enabled, config}`, sorted by name; `config` maps each dotted path to its leaf value, jar defaults included. Read-only. No timestamps, never the agent token. Added within v1 (§4), so the protocol stays `1.1` |

Error codes used so far (the code is an `UPPER_SNAKE` token; adding codes is allowed within v1):

| Code | HTTP | When |
|---|---|---|
| `UNAUTHORIZED` | 401 | missing or wrong token; the reply is the same for every endpoint |
| `PLAYER_NOT_FOUND` | 404 | the player is not online; `detail` is `{name}` |
| `UNKNOWN_KEY` | 400 | unknown material or enchantment on `give`; no partial item is created |

A successful `give`, `reset` or `PATCH` with nothing to return replies `{"ok": true, "data": {}}`.

## 3. Core schemas

- **`item.json`:** see the [test API §6](test-api.md). A `raw` field (string) is always present on reads.
- **`player-state.json`:**
  - `name`, `uuid`, `level`, `exp`, `gamemode`, `health`;
  - `location {world, x, y, z, yaw, pitch}`;
  - `inventory [slotIndex → item]`, where slot indexes are the Bukkit raw player-inventory indexes,
    documented per pack;
  - `cursor`;
  - `openWindow {type, title, slots[]}` or `null`.
- **`event.json`:** `{class, tick, thread, fields: {getterName → value}}`. Values are serialised as JSON
  primitives, `item.json` for `ItemStack`, `{name}` for `Player`, or `toString()` with a `"$type"` marker.
- **`log-record.json`:** `{time, level, logger, message, thrown}`.

## 4. Versioning rules
- The path prefix `/v1` is the major version. Adding endpoints or optional fields is allowed within v1.
  Removing anything or changing a meaning requires `/v2`.
- `GET /v1/health` reports `protocol: "1.<minor>"`. Core refuses to run if the major differs, or if the
  agent minor is below core's minimum.
- **Golden files:** `proofstone-protocol/golden/<endpoint>/*.json` hold request and response pairs.
  Every agent's contract test replays them.

## 5. Security
- The agent never ships in user plugin builds. It is installed only by the harness into the container.
- It binds only inside the container network and is never published to the host by default. Debug mode
  can publish it on a random host port.
