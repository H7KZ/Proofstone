# Gate (Go proxy, `minekube/gate`)

Spike T-M10-12, 2026-11-20, Windows 11 host, Docker Desktop. Everything below was run by hand under `scripts/dockerlock.sh`
with `scripts/spike/gate-spike.sh` and `scripts/spike/GateProbe.java`. Nothing is built into the product: `versions.yml` has no
Gate row, no matrix row changed, no wire protocol changed, no Go toolchain was added. Run output lived in
`/c/pw/gate-cache/runs/` outside the repo (not committed, so no repro bundle is linked). Where a run did not show a thing, the
text says "not run" or "guessed". See [platforms.md](../platforms.md), [ADR-0016](../adr/0016-topology-cells.md).

## Verdict

**Gate fits the ADR-0016 topology shape for join, modern forwarding, direct-join refusal, server switch and the `BungeeCord`
plugin channel on `paper@26.3`, but as an agent-less proxy.** Gate is Go: there is no way to load a Java proxy agent into it, so
the proxy half of the dual oracle (ADR-0002, ADR-0003) is missing. The only oracle is the backends' agents plus the client.
A Gate cell could therefore run P04 and the backend and client halves of P01 to P03, but not P01, P02, P03 and P05 as the spec words them. It is not a matrix row, and it
should stay a spike result until a decision is made about the oracle gap (below).

## Pin (verified before the download)
Maintainer-approved spike download. Source: the official release page
`https://github.com/minekube/gate/releases/tag/v0.82.0` (published 2026-11-18, not a pre-release, not a draft).
- **Release:** `v0.82.0`, asset `gate_0.82.0_linux_amd64` (glibc build, a single static Go binary).
- **URL:** `https://github.com/minekube/gate/releases/download/v0.82.0/gate_0.82.0_linux_amd64`
- **sha256:** `591ac49c9597f9e19dd17ee1b87fdca6caad1ad58dd60a23cd1dab4ee070397a`
- **Size:** 26,448,137 bytes.
- **Cross-check:** the release's `checksums.txt` (sha256 `2fd1241d9559f7ea460629240d2ff652ec86887984f6e0c410496a28dadf7377`)
  lists the same digest, GitHub's asset `digest` field gives the same value, and `sha256sum` of the downloaded file matched.
  The spike script re-checks the digest on the host and inside the container before it starts Gate.
- **Kept in** `/c/pw/gate-cache/` (outside any repo); the binary is never committed. A container image was not pulled.
- **Not pinned in `versions.yml`.** The source for a real pin would be this release asset (digest from `checksums.txt`).

## Host
- **How it ran:** the binary was copied (`docker cp`, no bind mount) into a container created from the project's pinned
  `itzg/minecraft-server@sha256:e2fabc53...` image with the entrypoint overridden to `chmod +x` and run `./gate -c config.yml`.
  That image had to carry glibc; it avoided pulling a second image. A real cell would want a small pinned image or a scratch
  image with the binary, a separate decision.
- **Ready:** the log line `listening for connections {"addr": "0.0.0.0:25565"}`. Boot takes well under a second.
- **Protocol 777 (26.3):** Gate 0.82.0 accepted the mcpl driver (26.3) on every join. Its server-list text advertises the range
  `1.7.2-1.7.5-26.3` (from Gate's own docs).
- **Topology:** one network, backends with the aliases `lobby` and `survival`, both `paper@26.3` exactly as `versions.yml`
  pins it (build 244, jar sha256 checked every run), each with the Bukkit agent and the fixture plugin copied into `/plugins/`.

### Gate config used (`config.yml`, the keys that mattered)
```yaml
config:
  bind: 0.0.0.0:25565
  onlineMode: false
  forceKeyAuthentication: false
  servers: { lobby: "lobby:25565", survival: "survival:25565" }
  try: [lobby]
  forwarding: { mode: velocity, velocitySecret: <generated per run> }
  quota: { connections: { enabled: false }, logins: { enabled: false } }
```
- Backend: the same as Velocity modern ([velocity.md](velocity.md)): `config/paper-global.yml` with `proxies.velocity.enabled: true`,
  `online-mode: false`, `secret: <same secret>`. Gate's `velocity` forwarding mode is the Velocity modern protocol.
- `quota.*` is switched off the way Velocity's `login-ratelimit = 0` is. Its defaults (3 login burst, 0.4 per second per
  IPv4 /24) would throttle a harness that joins from one address; this was **not** shown to bite, it is from Gate's docs.
- The secret lives only in the run's staging directory, never in a committed file.

## P01 to P05, by hand
Oracle: the Bukkit agent on each backend (`/v1/players`, `/v1/players/{name}`, `/v1/command`), the backend logs, the client.
No proxy agent exists. Six runs of the script (below); the first four and the sixth were green, the fifth was my own harness
error. Player `GateSpike1`.

| ID | What the spec asks | What ran on Gate | Outcome |
|---|---|---|---|
| P01 | proxy agent lists the player and server; backend agent lists the same player; a proxy event probe saw the connect | client joined (`multiplayer.player.joined`); lobby agent listed `["GateSpike1"]`, survival agent `[]`; Gate logged `player joining initial server ... lobby` | **partial**: backend side and client shown; proxy `players` and `events` not available |
| P02 | UUID and name equal the proxy's and the offline UUID; client address equal on both sides; direct join refused | backend agent UUID `708e7b02-d5e1-3af8-9062-5340f3aa221c` equals the offline UUID of the name, and Gate logged the same id; backend logged `GateSpike1[/172.26.0.1:...]`, the Docker gateway (the client's address), not Gate's container; direct join to the backend's own port: `could not join Spike1 -> 127.0.0.1:43730: This server requires you to connect with Velocity.` (run 6) | **partial**: forwarding and refusal shown, identical text to Velocity. Gate does not log the client address at INFO, so the proxy-side address comparison cannot be made |
| P03 | switch `lobby` to `survival` and back; client, proxy agent and both backend agents agree | client typed `/server survival` then `/server lobby`; after each, the target agent listed the player and the source agent listed `[]`; the client could still chat; Gate logged `player switching the server ... previous lobby` then `... previous survival` | **shown** for client plus both backend agents; the proxy agent is the missing party. 5 of 5 runs that reached the client saw both switches (runs 1 to 4 and 6; run 5 never reached it) |
| P04 | backend fixture sends `GetServer` on `BungeeCord`, the proxy answers, the fixture records it | `fixture-getserver GateSpike1` through the lobby agent; the lobby log recorded `fixture BungeeCord reply: GetServer lobby` | **shown** (config key `bungeePluginChannelEnabled: true` is Gate's default, I did not change it) |
| P05 | a console command through the proxy agent runs; a client-typed `/server` is seen by the proxy agent's events | client-typed `/server`: reply `You are currently connected to "lobby". Available servers (2): lobby, survival`; Gate lists builtin commands `server`, `glist`, `send` at start | **partial**: the command path is shown, the console path and the events are not. Whether Gate reads a console on stdin was **not run** |

The mcpl driver stayed connected across both switches, as on Velocity.

### Runs (every one)
| Run | Invocation (under `scripts/dockerlock.sh`) | Result |
|---|---|---|
| gradle 1 | `./gradlew -q --init-script scripts/spike/classpath.init.gradle :platforms:bukkit:agent:shadowJar :platforms:bukkit:fixture:jar :drivers:mcpl:spikeClasspath --no-parallel --max-workers=1` | green, jars built |
| 1 | `bash scripts/spike/gate-spike.sh` | green: join, P02 uuid equal, P03 both switches, P04 reply |
| 2 | same | green, same observations |
| 3 | same | green, same observations |
| 4 | same | green, same observations |
| 5 | same, `SPIKE_CP` unset by my shell | **red**: `SPIKE_CP: unbound variable` before the client ran. A harness mistake, kept as a finding, not replaced |
| 6 | same, with the P02 direct join added to the script | green, direct join refused with Paper's message |

## Does Gate have an extension model an agent could use?
- **In-process (Go):** yes. Gate is a Go library; a plugin is Go code in a program that imports `go.minekube.com/gate` and appends
  to `proxy.Plugins`, with an event system and commands (from Gate's developer docs, `/developers`). It is compiled into
  the proxy binary. **Not run, and out of scope:** this card forbids a Go agent and a Go toolchain. A Java agent cannot load into Gate.
- **Out-of-process API:** Gate has an optional HTTP/ConnectRPC/gRPC API (`api: {enabled: true, bind: localhost:8080}`). Its
  proto, `minekube.gate.v1.GateService`, has these calls: `GetPlayer`, `ListPlayers`, `ListServers`, `RegisterServer`,
  `UnregisterServer`, `ConnectPlayer`, `DisconnectPlayer`, `StoreCookie`, `RequestCookie`, `GetStatus`, `GetConfig`,
  `ValidateConfig`, `ApplyConfig`. **Not run** (the API was not enabled in any run): this list is read from the
  proto at v0.82.0, so the behaviour is guessed. It has no event subscription, no command execution and no log read, so it
  would give `players` and `proxy/servers` at best, never P01's event probe or P05's console command. `ConnectPlayer`
  and `DisconnectPlayer` act on players, which an agent must never do (AGENTS.md section 3).

## Fallback and oracle gap
**Fallback:** an agent-less proxy; the backends' agents and the client are the only oracle.
**Gap, stated plainly (ADR-0002, ADR-0003):**
- no proxy `players` (who the proxy thinks is online), no `previousServer`, no `proxy/servers` `online` flag;
- no proxy `events` probe (P01, P05), no proxy `log` (the Gate log was read from `docker logs` by hand, which is scraping and
  not an oracle);
- no proxy console `command` (P05);
- the client address and the UUID the proxy saw cannot be compared with what the backend logged (P02): only the backend side exists.
A test that fails because Gate dropped or rewrote something can be seen on the backend, but a test that passes cannot say
Gate did the right thing; a plugin test about proxy behaviour would be `INVALID`, not green, under the "fail loudly on
unknowns" rule. The spec would need a cell flavour whose proxy-side scenarios are skipped with a reason (a skip is never a
pass), or a Go agent (a new toolchain, a card of its own).

## Capabilities
None declared for a cell. Not a row. No agent exists.

## Gotchas
- **Gate's `quota.logins` defaults throttle a harness** (3 burst, 0.4 per second per address block): switch them off for a cell. From docs, not seen. _T-M10-12_
- **Gate does not log the client address at INFO.** `player has connected` carries the name and UUID only. _T-M10-12_
- **The `/server` reply has a leading newline** in the text the driver receives (`\nYou are currently connected to "lobby".`). _T-M10-12_
- **A direct join to a backend with modern forwarding is refused with Paper's message**, the same as behind Velocity. _T-M10-12_
- **Config reload is on by default** (`auto config reload enabled`, from the log), so a changed `config.yml` can act on a running Gate. _T-M10-12_
- `onlineMode: false` makes Gate log `config validation warn: java: Proxy is running in offline mode!`, expected in a test cell.
