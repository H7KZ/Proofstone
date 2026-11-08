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
| `inventory` | none | the player inventory (window 0) as last synced, even while a container is open |
| `window` | none | the open container (`id`, `type`, `title`, `stateId`, `slots`, `cursor`), or the inventory when none is open |
| `click` | `slot`, `button`, `mode` | `null`; the server answers with a sync (a newer `stateId`) |
| `closeWindow` | none | `null` |
| `rename` | `name` | `null`; sets the anvil rename box (`name_item`) |
| `useBlock` | `x`, `y`, `z` | `null` once Mineflayer has activated the block |

A stack is `{itemId, type, count, enchants, storedEnchants, name, lore, damage, repairCost}`. Enchantments are
`{name: level}` with minecraft-data names; `name` is plain text, or `null`.

Notifications from the sidecar: `chat` (`text`, `system`), `disconnect` (`reason`), `error` (`message`),
`windowOpen` (`id`, `type`), `windowClose` (`id`).

## Gotchas

- **Forced version.** See the protocol finding above. A bot that reports the server's version has skipped ViaProxy.
- **Player chat has no separate event.** Mineflayer's `message` event carries player chat too. The sidecar sets
  `system: false` for text that starts with `<name> `, and `system: true` for everything else.
- **Join and version replies.** Both arrive as plain text: `ProofBot joined the game`, and `This server is running Paper
  version ...`. The bridged 1.21.11 row answers `version` with `1.21.11`, so the reply is the server's own, not
  ViaProxy's.
- **A stopped server ends the session.** The bot reports `socketClosed` as the reason, and the Java side fails pending
  waits with `disconnected while waiting`.
- **Mineflayer's `clickWindow` predicts the result locally.** It runs `window.acceptClick` on its own copy, so the
  sidecar sends `window_click` itself with no changed slots and an empty cursor. The server answers with a resync, and
  the driver awaits the newer `stateId`. _26.1.2 and 1.21.11 (bridged) · T-M4-05b_
- **`windowClose` can carry `null`.** Mineflayer emits it for a window it never tracked; reading `.id` crashed the
  sidecar, which the Java side saw only as a dropped socket (close code 1006). The sidecar's output goes to the test log
  now. _26.1.2 · T-M4-05b_
- **`stored_enchantments` is not read by prismarine-item.** Its `enchants` getter reads only the `enchantments`
  component. The sidecar reads both, as `{enchantments: [{id, level}]}`, and maps each registry id to its name with
  minecraft-data. _26.1.2 · T-M4-05b_
- **Custom names and lore are NBT-typed components.** The sidecar unwraps `{type, value}` before it reads the text
  children, or the name comes out empty and the rename scenario never sees it. _26.1.2 and 1.21.11 · T-M4-05b_
- **Mineflayer's window state id is private.** The sidecar tracks it from `window_items` and `set_slot`, the same two
  packets Mineflayer uses. _26.1.2 · T-M4-05b_
