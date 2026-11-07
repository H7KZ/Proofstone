# Mineflayer reference driver

The reference driver (ADR-0004, [client-driver spec](../specs/client-driver.md) section 4): Mineflayer 4.39.0 in a
sidecar image, exposed as JSON-RPC 2.0 over a WebSocket. Code: `images/mineflayer-ref` (sidecar) and
`drivers/mineflayer-ref` (Java side).

## Protocol finding (T-M4-05, checked 2026-11-07)

Checked before choosing the acceptance cells, from the pinned npm install of `mineflayer@4.39.0`:

- `mineflayer@4.39.0` depends on `minecraft-protocol` 1.68.0 and `minecraft-data` 3.119.0.
- The highest Minecraft version both list is **26.1**, which is protocol **775**. 26.2 and 26.3 are not listed. This
  matches ADR-0004 (a 26.3 server refused it).
- So the native row is **26.1.2** (protocol 775). Every row with an older protocol, for example **1.21.11** (774), is
  bridged through ViaProxy, and the sidecar talks to ViaProxy rather than to the server.
- `versions.yml` `drivers.mineflayer-ref.max-native: "26.1"` is confirmed.

The sidecar forces the protocol with `version: "26.1"` in `createBot`. Without it, Mineflayer takes the version from the
server's ping, speaks the server's own protocol, and the bridged cell never goes through ViaProxy.

## Protocol (JSON-RPC 2.0 over WebSocket, port 8765)

| Method | Params | Result |
|---|---|---|
| `connect` | `host`, `port`, `username` | `null` once the bot has spawned, or an error |
| `chat` | `message` | `null` |
| `command` | `command` (no slash) | `null` |
| `state` | none | `position` (`[x,y,z]` or `null`), `experienceLevel` (or `null` before the first sync) |
| `close` | none | `null` |

Notifications from the sidecar: `chat` (`text`, `system`), `disconnect` (`reason`), `error` (`message`).

## Gotchas

- **Forced version.** See the protocol finding above. A bot that reports the server's version has skipped ViaProxy.
- **Player chat has no separate event.** Mineflayer's `message` event carries player chat too. The sidecar sets
  `system: false` for text that starts with `<name> `, and `system: true` for everything else.
- **Join and version replies.** Both arrive as plain text: `ProofBot joined the game`, and `This server is running Paper
  version ...`. The bridged 1.21.11 row answers `version` with `1.21.11`, so the reply is the server's own, not
  ViaProxy's.
- **A stopped server ends the session.** The bot reports `socketClosed` as the reason, and the Java side fails pending
  waits with `disconnected while waiting`.
- **Not done in this slice.** Windows, blocks, inventory and item parsing are not implemented (T-M4-05b). They fail
  loudly. The C03 to C06 acceptance is therefore not met by this PR.
