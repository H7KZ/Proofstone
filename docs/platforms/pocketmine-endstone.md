# PocketMine-MP and Endstone (scope only, unverified)

Scope spike T-M10-14, 2026-11-20. **Nothing was downloaded, run or built.** No server, image, phar or wheel was pulled; the
card forbids it. Every claim is marked **read** (with the URL it was read from, on 2026-11-20) or **guess**. Nothing here is
verified by a run, so no feasibility is claimed. `versions.yml`, the matrix and the wire protocol did not change. See
[platforms.md](../platforms.md), [bds.md](bds.md) (the T-M10-01 answer Endstone depends on), [ADR-0002](../adr/0002-server-truth-via-agent.md)
and [ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md).

## Verdict

- **PocketMine-MP: not now.** The project is archived and its newest release speaks a Bedrock protocol far behind the driver's.
  The software itself looks like a fit on paper (an offline login path exists, a MONITOR event priority exists), so it is worth a
  spike only if a maintained fork appears.
- **Endstone: not now, blocked by T-M10-01.** Endstone is not a separate server: it runs the unmodified official BDS with a
  native runtime preloaded. T-M10-01 showed BDS refuses the M9 driver's login. Endstone inherits that result unless a run proves
  a plugin can change it (a guess, and it would break the observe-only rule).

## Licences and redistribution (AGENTS.md section 3: no Mojang binaries committed, server jars downloaded at runtime)

| Project | Licence | Source | Fits our no-redistribution rule? |
|---|---|---|---|
| PocketMine-MP | LGPL-3.0 (read) | `https://api.github.com/repos/pmmp/PocketMine-MP` (`license.spdx_id`), `composer.json` | Yes, as a runtime download (release asset `PocketMine-MP.phar`, 3,354,057 bytes, read from the 5.44.3 release API). We never ship it. A Proofstone agent plugin would be a separate work that only calls the plugin API; whether LGPL-3.0 reaches a plugin loaded into the server is a legal question not answered here (guess: no, but not checked). |
| Endstone | Apache-2.0 (read) | `https://raw.githubusercontent.com/EndstoneMC/endstone/main/LICENSE`, `https://api.github.com/repos/EndstoneMC/endstone` | Yes for Endstone. Endstone does **not** bundle BDS: its bootstrap downloads BDS from "the official mirror" and checks a sha256 (read: `endstone/cli/base.py`, `_download`; docs "Start your server"). The BDS licence and EULA are Mojang's (see [bds.md](bds.md)); we would still download at run time and never commit. |

## What each is

| Fact | PocketMine-MP | Endstone |
|---|---|---|
| Kind | A from-scratch Bedrock server in PHP, C and C++ (read: repository description) | A plugin API for the official BDS: Python 3.11+ or C++ plugins (read: README) |
| Newest release | `5.44.3`, published 2026-07-06, "For Minecraft: Bedrock Edition 1.26.30" (read: `https://api.github.com/repos/pmmp/PocketMine-MP/releases`) | `v0.11.16`, published 2026-11-16, "support for BDS version 1.26.65" (read: `CHANGELOG.md`, releases API) |
| Project state | **Archived 2026-07-09**, read-only; the README says "no further updates will be provided by the PMMP team" and links a how-to for forks (read: repository API `archived: true`, `README.md`) | Active: last push 2026-11-19, 775 stars (read: repository API) |
| Platforms | PHP `^8.1` with many extensions (`pmmpthread`, `leveldb`, `crypto` and others); a Docker build exists in the repository (`docker/Dockerfile`, read) | Windows 10+ and Linux (Ubuntu 22.04+, Debian 12+); `pip install endstone`; an image `endstone/endstone` is named (read: README, installation page) |
| Transport | RakNet (`pocketmine/raklib`, read: `composer.json`) | BDS's own. The 0.11.16 changelog mentions NetherNet servers (read), so BDS's NetherNet default applies as in bds.md |
| Entry into the server | n/a (it is the server) | Linux: `LD_PRELOAD` of `libendstone_runtime.so` plus mimalloc; Windows: a loader DLL (read: `endstone/cli/linux.py`, `src/endstone/runtime/CMakeLists.txt`; the hook mechanism itself was not studied) |

## Plugin API model and an agent that serves the protocol read-only

The wire protocol is HTTP, inverted transport allowed ([agent-protocol.md](../specs/agent-protocol.md)). What each platform
would need, all **guess** unless marked read:

- **PocketMine (PHP phar plugin).** Events with a `MONITOR` priority that "should not change the event outcome" exist (read:
  `src/event/EventPriority.php`, `PlayerJoinEvent.php`). The server requires `ext-curl` and `ext-sockets` (read: `composer.json`),
  so a plugin can call out over HTTP (an inverted long-poll as in bds.md) or run a listener on a thread. PHP's threading model
  (`pmmpthread`) probably makes a server socket inside a plugin harder than in Java (guess). Agent estimate: 3 to 5 days, with a
  PHP image carrying the right extensions (the official image is `ghcr.io/pmmp/pocketmine-mp`: named by the README, not pulled).
- **Endstone (Python or C++ plugin).** 60+ events, many cancellable, an event-handler decorator and a scheduler (read: README,
  docs "Python API"). A Python plugin can probably use `urllib` or a thread for HTTP (guess). "Observe, never interfere" means
  MONITOR-style handlers only; whether Endstone has a MONITOR priority was not read (guess: it follows the Bukkit shape). Guess
  2 to 4 days for the agent *after* the BDS login problem is solved. Endstone sends metrics to bStats (read:
  `endstone/metrics/base.py`, `REPORT_URL`); a cell would need that off or blocked (guess).

## Protocol coverage against the Bedrock driver (2225, game 1.26.60, `versions.yml`)

| Server | Its protocol | Driver 2225 joins? |
|---|---|---|
| PocketMine-MP 5.44.3 | `CURRENT_PROTOCOL = 1001`, `MINECRAFT_VERSION = 'v26.30'` (read: `https://raw.githubusercontent.com/pmmp/BedrockProtocol/58.0.0%2Bbedrock-1.26.30/src/ProtocolInfo.php`; PocketMine's `composer.json` pins `pocketmine/bedrock-protocol ~58.0.0+bedrock-1.26.30`) | **No, by source:** `isCompatibleProtocol` in `SessionStartPacketHandler.php` is `$protocolVersion === ProtocolInfo::CURRENT_PROTOCOL` (read), one value only, so the driver would be refused as incompatible. The jump from 1001 (v26.30) to 2225 (1.26.60) is not explained by anything read; it looks like a numbering change, not a count of releases (guess). |
| Endstone 0.11.16 | the protocol of BDS 1.26.65 (number not read; BDS 1.26.66.1 was the newest in bds.md) | Same as BDS: refused in 5 of 5 runs, T-M10-01 ([bds.md](bds.md)). Not re-run. |

**Login, offline.** PocketMine reads both a full Xbox chain and a self-signed one (`AuthenticationType` branch,
`mapSelfSignedTokenBody`, `ProcessSelfSignedLoginTask`: read in `LoginPacketHandler.php`). `xbox-auth` is a `server.properties`
key defaulting to `true` (read: `Server.php`), and `PlayerPreLoginEvent` carries `authRequired` (read). With the key off an unsigned
login looks acceptable. That is **promising, not shown**: the first attempt would be the first run. Whether PocketMine also calls
a Microsoft name at start or login was not read (guess: a full login does, a self-signed one does not; the ADR-0018 stand-in would
have to be checked).

## Recommended next step and effort

1. **Record "not now" for both.** No card now.
2. **PocketMine:** reopen only if a maintained fork or a new official release reaches protocol 2225 (check the PMMP fork list and
   `ProtocolInfo.php`). Then one spike card (tier sonnet, 2 days): boot the phar on PHP 8 (a new image, which needs the maintainer),
   join with the driver under `xbox-auth=false`, record protocol, names asked and MONITOR events; agent afterwards 3 to 5 days.
   Alternative today: a driver variant at protocol 1001, which needs a Cloudburst codec at that version (not looked up; guess it is
   not in the pinned snapshot).
3. **Endstone:** wait for T-M10-02/03 to reopen (a BDS that accepts an offline login, or a NetherNet or signed-login client). Then a
   2-day spike (the `endstone/endstone` image or a Python image, digest pinned) reusing the `scripts/spike/bds/` scripts. A plugin
   that accepts an unsigned login would interfere with the server, so it could only be a harness-side switch, not an agent feature.
4. Total if both are ever taken: about 8 to 12 days over three or four cards (guess).

## Open questions (for the maintainer)

- Is a PHP image acceptable for PocketMine if a fork keeps it alive, or is an archived upstream a hard "no"?
- Does LGPL-3.0 matter for a plugin we would publish? (No legal check made.)
- Is a driver pinned to an older protocol (1001) of interest, given ADR-0004 (latest protocol plus ViaProxy)?
