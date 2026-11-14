# ADR-0016: Topology cells: one proxy row plus a backend row set

- Status: Accepted
- Date: 2026-11-09
- Deciders: maintainer delegated the M7 decision list ("do it all, your recommendations, autonomous", 2026-11-09);
  decided autonomously by the T-M7-02 agent. Amends nothing; adds to [ADR-0008](0008-versions-yml-single-source-of-truth.md),
  [ADR-0013](0013-host-digest-per-java-tag.md) and [ADR-0015](0015-local-evidence-row-promotion.md) for proxies only.

## Context
- A cell today is one platform at one version (`Cell`, `versions.yml` schema 2). `PaperHost`, `CellInvocation` and
  `HostAgent` assume one cell is one server. A bridged cell adds one ViaProxy container in front
  ([ADR-0004](0004-client-driver-latest-protocol-plus-viaproxy.md)).
- M7 adds proxies: a client joins a proxy, the proxy forwards it to one of N backends, and a server switch moves a live
  session between backends. The glossary already lists *topology* as part of a cell ("proxy with backends").
- The T-M7-01 spike ([velocity.md](../platforms/velocity.md), [bungeecord.md](../platforms/bungeecord.md), ROADMAP
  "M7 findings") ran, on two `paper@26.3` backends:
  - **Velocity 4.3.1 build 45** takes the protocol 777 client natively (15 of 15 joins, both forwarding modes). It is
    Java 25 bytecode, so it needs the `itzg/mc-proxy` `java25` tag. Velocity 3.5.1 build 615 rejects 777.
  - **BungeeCord build 2111** takes 777 natively (10 of 10 joins) on the `java21` tag. Legacy forwarding only, no secret.
    Jenkins gives no sha256, and old builds could be pruned.
  - **Waterfall 1.21 build 615**, its newest build, rejects 777. Waterfall is end-of-life upstream.
  - `itzg/mc-proxy` (release `2026.11.1`) ran both proxies with the jar downloaded by build number and its sha256
    checked in the container, configs copied in with no bind mount.
  - Server switches: Velocity sends start-configuration then a new login, BungeeCord adds a respawn. The mcpl driver
    stayed connected through 48 switch attempts. 2 failed on Velocity because its default `command-rate-limit = 50` with
    `forward-commands-if-rate-limited = true` forwarded `/server survival` to the backend (`command.unknown.command`);
    with the limit at 0, 10 of 10 switched (one run). Every test client shares one source address, so
    `login-ratelimit` (Velocity) and `connection_throttle` (BungeeCord) were set off.
  - Join timeouts on either hop: 0 of 25 client joins and 71 backend logins.
- The join-timeout family on single-server cells is bounded but not explained (ROADMAP "M6 review addendum"). A topology
  join has two hops, so a red must say which.

## Decision

### 1. A topology cell
- A **topology cell** is one proxy row plus a **backend row set**: N named backends, all on the **same** backend row
  (one platform at one version). Mixed backend versions are out of scope for M7.
- The **identity string** is `<proxy>@<proxy-version>+<backend>@<mc>`, for example `velocity@4.3.1+paper@26.3` and
  `bungeecord@2111+paper@26.3`. It is used unchanged for `-Dproofstone.cells=`, `PROOFSTONE_CELLS`, `printMatrix`, the
  report, `cell.json` and the bundle directory `build/proofstone/<identity>/<TestClass>.<method>/`. A single-server
  identity (`paper@26.3`) never contains `+`, so the two forms cannot collide.
- BungeeCord publishes no version other than the Jenkins build, so its row version is the build number as a string
  (`"2111"`). Velocity uses its release version (`"4.3.1"`) with the build as a separate pin.
- Backends are named `[a-z][a-z0-9]{0,15}`. The default set is `lobby` (first, the proxy's default server) and
  `survival`. A test may name others (T-M7-10). `proxy` is reserved. Each container gets the network alias `proxy` or
  `server-<name>`, and its own directory in the bundle (`proxy/`, `server-lobby/`, ...).
- **Single-server code is unchanged.** A topology is planned and started by new types (`TopologyHost`, `ProxyHost`,
  T-M7-07) that own several `PaperHost`s. `PaperHost` gains nothing beyond the network options it already has.
  *(Amended 2026-11-14, see Amendments: "unchanged" holds for identities, rows and `printMatrix` lines, not for the game-port binding.)*

### 2. `versions.yml` schema 3 (additive)
- A new top-level key `proxies`, a sibling of `platforms`. A schema 2 file is a valid schema 3 file, and code that reads
  `platforms` sees no change, so the existing single-server cells and their `printMatrix` lines stay identical.
- Proxy rows carry `version`, `build`, `sha256`, `java` (**required**: `java-rules` are keyed on Minecraft versions and do
  not apply to proxies), `status`, `observed`, `notes`, and `backends`: the backend rows this proxy row is paired with,
  each with its own `status`. The host is pinned per Java tag exactly as [ADR-0013](0013-host-digest-per-java-tag.md)
  says. Each proxy platform declares one `forwarding` mode. The spec is [versions-yml.md](../specs/versions-yml.md).
- A topology cell's status is the **weakest** of the proxy row, the pairing and the backend row
  (`retired` < `experimental` < `required`).
- **Host image:** `itzg/mc-proxy`, by digest per Java tag (`java25` for Velocity 4.3.1, `java21` for BungeeCord 2111). The
  image downloads the jar by pinned build number (`VELOCITY_VERSION` and `VELOCITY_BUILD_ID`, `BUNGEE_JOB_ID`) and the host
  checks the jar's sha256 in the container before start, as Paper rows do. `ENABLE_RCON=false` and
  `SKIP_DOWNLOAD_DEFAULTS=true` are always set, so the image fetches nothing else.
- BungeeCord's sha256 is computed from the download (Jenkins has none). If Jenkins prunes a pinned build, the cell fails
  loudly at download and the fix is a `versions.yml` PR to a newer build with a run behind it. No host-side jar cache in M7.

### 3. Required rows: native only, experimental first
- The target required rows are `velocity@4.3.1+paper@26.3` (modern forwarding) and `bungeecord@2111+paper@26.3`
  (legacy forwarding). Both enter `versions.yml` as `experimental` (T-M7-08).
- A pairing is promoted to `required` under [ADR-0015](0015-local-evidence-row-promotion.md), with topology scenarios
  in place of C01 to C08: P01 to P05 all run, the XML shows `failures="0" errors="0"`, every skip reasoned, and the M7
  flake rule's three separate green invocations. The promotion PR records the table. A pairing may be `required` only
  when its proxy row and its backend row are both `required`.
- **Native only.** Required topology rows are pairings where the driver's protocol is native on the proxy and on every
  backend. A bridged topology (ViaProxy in front of a proxy, or a backend older than the driver behind one) is **not
  planned** in M7: the planner refuses it with an error naming this ADR, never a skip and never a silent bridge.
- Velocity 3.x is not a row (it rejects 777).

### 4. Waterfall is out
- Waterfall gets no row, no pack and no run in M7. It is end-of-life upstream and its newest build rejects the newest
  client. The BungeeCord agent (T-M7-06) is not tested on it, and its README says so.
- Reopening it needs a new ADR, with a run showing a Waterfall build that takes the driver's protocol, or a bridged
  topology design.

### 5. Server switch and `transferTo`
- A **server switch** is the proxy moving a connected client from one backend to another. `Client.transferTo(name)`
  means a server switch and nothing else. It is **not** the vanilla 1.20.5 transfer packet; that packet is out of scope
  and would need its own term.
- `transferTo` is **client-side**: the client sends `/server <name>`, typed as a player would. There is no agent-side
  connect endpoint, so T-M7-03 does not add `POST /v1/proxy/servers/{name}/connect`.
- An unknown backend name, or the backend the client is already on, is an `IllegalArgumentException` before anything is
  sent. `transferTo` never re-sends a command.
- **Rate limits are harness settings.** The rendered proxy config sets Velocity `command-rate-limit = 0` and
  `login-ratelimit = 0`, and BungeeCord `connection_throttle: -1`, because every client comes from one address and the
  harness types faster than a person. They are listed in the platform docs as harness defaults. If a command still
  reaches a backend instead of switching, the switch await times out and the message quotes the backend's reply: a
  loud red, never a pass.

### 6. Dual oracle for a topology
Assertions keep [ADR-0003](0003-dual-oracle.md). The server oracle splits across agents:
- **Proxy agent:** the proxy's view (players online, each player's current and previous server, proxy events, proxy
  log). **Backend agents:** the view of their own backend, exactly as on a single-server cell.
- **Client oracle:** what the client sees on the proxy, and for a switch: the switch sequence (start configuration, then
  a new login; a respawn may follow, as on BungeeCord) completing with the client in play state.
- **A switch passes** when the client saw the new join, the proxy agent says the player is on the target, the target
  backend agent lists the player, and the source backend agent does not. All are awaited (no sleeps,
  [ADR-0007](0007-no-sleeps-no-retries.md)).
- **`DISAGREE`** when the client oracle and the server oracle differ (the client saw a new join but the proxy agent says
  the old server, or the reverse), **and** when the agents disagree with each other (the proxy agent says `survival`
  but the `survival` backend does not list the player). The message names every agent and its value. `FAIL` when no
  oracle shows the expected state. `ORACLE MISSING` when an agent never answers.
- Assertions on game state (`DualAssert.item`, `level`) use the agent of the backend the proxy agent says the player is
  on. If that is not one known backend, the assertion is an error.
- In a topology test, an injected plain `Agent` is the default backend's agent (`lobby`, where the client lands). Every
  other agent is reached through `Topology` (`server(name)`, `proxy()`, T-M7-10).

### 7. Isolation and reset
- `PER_CLASS` is the default: one topology (proxy and all backends) per class per cell. `PER_TEST` starts a new topology
  for each test.
- Before each test: the fresh client joins through the proxy and the harness awaits it on the proxy agent and on the
  default backend's agent. The player reset runs on the backend agent where the player is. The leak detector's baseline
  is read on **every** backend.
- After each test: configs are restored on every backend, the client disconnects, and the harness awaits the proxy
  agent listing no harness player before the next test. The log guard runs per container with that container's pack
  allowlist (proxy pack and backend pack). The leak detector compares every backend.
- Conformance for a topology cell is P01 to P05 ([self-tests.md](../specs/self-tests.md) §1b), cached per cell per run.
  A red makes every test on that topology cell `INVALID`. The backend row's own C-scenarios are not re-run inside a
  topology.

### 8. Forwarding secret
- Velocity modern forwarding needs a secret. It is **generated per cell** (`SecureRandom`, 32 bytes, like the agent
  token), held in memory, and copied into the proxy (`forwarding.secret`) and each backend (`paper-global.yml`) from
  memory. It is never written to a file in the repo or on the host, and never reaches a log line, the report, `cell.json`
  or a bundle. This extends the agent-token redaction rule: the bundle writer redacts both, any config copied into a
  bundle has the secret replaced, and a unit test greps every bundle file for the secret (T-M7-07).
- BungeeCord legacy forwarding has no secret, so a backend trusts any forwarded identity. Backend game ports are
  therefore not published to the network (only the proxy's port is), and the platform doc says that `ip_forward` is not a
  security feature. **Test-only exception (T-M7-11):** a backend's game port is bound to `127.0.0.1` on a host port the harness
  picked free, never to all addresses, so conformance P02 can join a backend directly and show what it does without the proxy
  (the direct-join control). `PaperHost.loopbackOnly` sets it (`Ports.Binding.bindIpAndPort("127.0.0.1", <free port>)` with
  publish-all off; port 0 and Docker's own pick failed with "address already in use" on Docker Desktop), `GamePortBindingTest`
  checks it, and the agent port stays unpublished outside debug mode. Other machines cannot reach a backend; a local process can.
- One forwarding mode per proxy platform in M7: Velocity `modern`, BungeeCord `legacy`. A second mode as a separate cell
  needs an identity suffix and an amendment to this ADR.

### 9. Join-flake accounting per hop
- A topology join has two hops: **client to proxy** and **proxy to backend `<name>`**. A join timeout, on the first join
  or during a switch, is reported with the T-M6-14 stalled stage **and** the hop. The hop is decided from evidence: no
  proxy login line for the player means client to proxy; a proxy login with no backend `logged in` line for the player
  means proxy to that backend.
- The bundle holds a log per container (proxy and every backend), and the INVALID or FAIL message carries the proxy and
  backend log tails (T-M7-07).
- Topology join timeouts are counted **separately** from the single-server numbers, per hop and per proxy, and a switch
  timeout is counted apart from a first-join timeout (T-M7-99). A join timeout is never a pass and is never retried.

### 10. Packs, agents and capabilities
- `Platform` gains `VELOCITY` and `BUNGEECORD` (additive, `api-change`, T-M7-10). A `@Matrix` naming a proxy platform
  plans topology cells from that proxy's paired rows in scope. `printMatrix --scope` lists topology cells after the
  single-server cells, by identity, with the same `pr` and `nightly` rules applied to the topology status.
- Capabilities `PROXY` (held by the proxy agent's cell view) and `TOPOLOGY` (held by every topology cell) are additive.
  `@Topology` or `@Requires(TOPOLOGY)` on a single-server cell is `SKIPPED` with `CAPABILITY_MISSING`, never a pass.
- Proxy agents observe and never interfere (AGENTS.md section 3): last priority, read-only, no commands registered, no
  config edited. Velocity's agent is Java 17 bytecode (Velocity's API floor). The BungeeCord agent targets Java 8
  bytecode unless the pinned BungeeCord API needs newer, which T-M7-06 records. Both share `platforms/agent-core`
  (Java 8, T-M7-04).

## Consequences
- The single-server path, its rows and its `printMatrix` output stay byte-identical. The cost is a parallel host and
  planner path for topologies.
- A topology run boots three containers and is slower. The flake exposure doubles (two hops), which is why the hop is
  named and counted separately.
- Velocity's switch can be broken by a proxy setting the harness controls; the harness owns those settings, so a plugin
  that depends on Velocity's default rate limit cannot be tested for that in M7.
- BungeeCord rows depend on Jenkins keeping the build. A pruned build is a loud red and a row update, not a silent
  fallback.
- New work is already carded: T-M7-03 (protocol, no connect endpoint), T-M7-07 (host, secret, per-hop messages), T-M7-08
  (schema 3), T-M7-09 (switch), T-M7-10 (API), T-M7-11 to T-M7-13 (P01 to P05 and faults).

## Alternatives considered
- **One cell per server with a shared network** (each backend its own cell, the proxy another): rejected. A cell's
  verdict would depend on other cells' lifecycles, conformance could not be cached per cell, and a switch spans cells.
- **Proxy rows inside `platforms`** with a `kind: proxy` flag: rejected. Every reader of `platforms` would need a filter,
  and a mistake would plan a proxy as a server. A separate key keeps schema 2 readers untouched.
- **Mixed backend versions in one topology** (for example `paper@1.21.11` and `paper@26.3`): rejected for M7. It needs a
  bridge per backend or a proxy-side translator, and no run exists.
- **Waterfall as a required row:** rejected. End-of-life, and its newest build rejects the newest client (T-M7-01).
- **Bridged required rows** (ViaProxy in front of Velocity 3.x or Waterfall): rejected. Native rows exist for both
  proxies, and a bridge adds a third hop with no run behind it.
- **Agent-side switch** (`POST /v1/proxy/servers/{name}/connect`): rejected as the default. The agent would act during
  the act phase (ADR-0002), and the client path is what a player does.
- **Await between proxy commands** instead of turning off Velocity's command rate limit: rejected. The limiter's rule was
  not plain "50 ms since the last command" in the spike, so no await predicate is known to be right; a fixed gap would be
  a sleep (ADR-0007).
- **The vanilla transfer packet as `transferTo`:** rejected. It is a different feature (reconnect to another address);
  proxies switch servers with `/server`.
- **A host-side cache of the BungeeCord jar:** deferred. The image download plus the in-container sha256 check matches
  Paper rows and needs no new code; a cache is a separate card if a pruned build is ever seen.
- **A pinned host image of our own** (`images/`): rejected for now. `itzg/mc-proxy` ran both proxies in the spike and
  is pinned by digest like `itzg/minecraft-server`.

## Amendments

### 2026-11-14: the loopback-only game-port binding covers bridged single-server cells too (T-M7-99 finding)
- Section 1 says single-server code is unchanged, and section 8 scopes the loopback-only game-port binding to a topology backend
  (the T-M7-11 direct-join control). As built, `PaperHost` applies `loopbackOnly` to **every** server started with a network,
  and `CellInvocation` puts a bridged single-server cell (ViaProxy in front of one server) on a network. So a bridged
  single-server cell's game port is bound to `127.0.0.1` on a free host port, not published on all addresses.
- Rule from now on: a game port is bound to `127.0.0.1` for every server that shares a Docker network with a proxy (ViaProxy,
  Velocity or BungeeCord). Only a plain single server with no network publishes it the usual way. The harness, ViaProxy and the
  proxies reach the server over the network, and the M7 review's `metaTest` bridged tasks gave their verdicts.
- What stays true of section 1: identities, rows, `printMatrix` output and the verdict logic of single-server cells did not change.
  What section 1 must not be read to promise: that a bridged single-server cell's published ports are as before M7.
- No code change. This amendment corrects the ADR text only.
