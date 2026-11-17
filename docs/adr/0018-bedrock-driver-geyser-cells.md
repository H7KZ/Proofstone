# ADR-0018: Bedrock driver, Geyser cells and an offline login stand-in

- Status: Accepted
- Date: 2026-11-17
- Deciders: maintainer delegated the M9 decisions ("decide", 2026-11-17) with two fixed limits: no Bedrock Dedicated Server
  (BDS) in M9, and no new image and no workflow in M9. Decided autonomously by the T-M9-03 agent from the T-M9-01 spike
  ([geyser.md](../platforms/geyser.md)). Amends nothing; adds to [ADR-0004](0004-client-driver-latest-protocol-plus-viaproxy.md),
  [ADR-0008](0008-versions-yml-single-source-of-truth.md) and [ADR-0016](0016-topology-cells.md); rows follow
  [ADR-0015](0015-local-evidence-row-promotion.md).

## Context
- Real Bedrock clients log in with a chain signed through Xbox Live. Proofstone runs are offline, with no secret and no
  account ([ADR-0006](0006-pin-everything.md), AGENTS.md section 3). M5 shipped only a simulation: a Java client under a `.`
  name (`SIMULATED_BEDROCK`, [test-api.md](../specs/test-api.md) section 11).
- The T-M9-01 spike ran 59 Bedrock joins on `paper@26.3` with Geyser 2.12.1 build 1303 and Floodgate 2.2.5 build 141; 48
  spawned, and all 42 with the three conditions in place were green, for two drivers and two routes. The conditions, each of
  which failed when removed:
  1. `advanced.bedrock.validate-bedrock-login: false` (default `true` refuses an unsigned chain with `Please log into Xbox to
     join this server.`);
  2. `bedrock.transport: both` (Geyser 2.12.1 defaults to NetherNet; RakNet, UDP 19132, needs `both`);
  3. a stand-in for two Microsoft discovery names (`client.discovery.minecraft-services.net`,
     `authorization.franchise.minecraft-services.net`). CloudburstMC Protocol's `EncryptionUtils` static initialiser fetches
     them, in **Geyser at the first login of each server start and in every Cloudburst client JVM**. Without a route the join
     fails; with one, a Microsoft host is contacted, which the card calls "not offline".
- Other spike facts this ADR relies on: the Java Cloudburst client joined from a container on the cell network 6 of 6 with
  its DNS and trust store pointing at the stand-in; a host-placed client with internet did reach Microsoft; the Node client
  (`bedrock-protocol`, `raknet-native`) made no JS-level lookup but cannot set an XUID (every Floodgate player gets the nil
  UUID) and needs a Node image on the cell network, which does not exist; Testcontainers 2.0.5 publishes UDP only through a
  create-command modifier and `getMappedPort` then throws, so a host-side UDP binding needs its own inspect code; Floodgate's
  `key.pem` can be copied in before the first start (3 of 3 joins); Geyser's mappings come from its jar, and its Mojang manifest
  and locale fetches are optional.
- The spike's driver used a **snapshot** (`3.0.0.Beta15-SNAPSHOT`, Gradle resolved `20261116.161745-6`). Whether a release
  carries the 2225 codec was not checked.
- Not run, and not claimed: Geyser through a Testcontainers cell, any chat, command or inventory round trip through a Bedrock
  driver, Geyser's refusal text for an unsupported protocol, two Cloudburst clients at once.

## Decision

### 1. Scope: Geyser and Floodgate on Paper only; no BDS in M9
- M9 builds one real Bedrock path: a Bedrock client joining `paper@26.3` through the **Geyser-Spigot** and **Floodgate-Spigot**
  plugins, read by the existing Bukkit agent. Only the `paper` platform is paired; Folia, Purpur and Spigot were not run.
- **BDS stays out of M9** (maintainer decision, 2026-11-17). A BDS image or zip is a Mojang binary with its own EULA and no
  redistribution, and a new image needs an explicit maintainer yes (AGENTS.md section 3). T-M9-02 (BDS spike), T-M9-09 and
  T-M9-10 move to M10 unchanged in intent. Nothing in this ADR depends on BDS, and nothing in it decides a script agent, an
  inverted transport or a protocol bump. What M10 inherits: `Edition.BEDROCK` and a Bedrock driver that already speaks RakNet.
  Whether BDS accepts an unsigned login is **unverified**.

### 2. Driver: CloudburstMC Protocol (Java), run as a driver host on the cell network
- **Library.** `org.cloudburstmc.protocol` (`bedrock-connection`, `bedrock-codec`, codec `Bedrock_v2225`: Bedrock protocol
  2225, game version 1.26.60, RakNet 11), in a new module `drivers/bedrock`. It is Java, like `mcpl`, so no Node and no image
  of ours. Protocol-level facts (login, resource packs, start game, item registry) are written in
  [client-driver.md](../specs/client-driver.md) section 6.
- **Placement: not in the harness JVM.** The spike shows why. The Cloudburst client fetches Microsoft's discovery documents in
  its static initialiser, in whichever JVM loads it, and a host-placed client with internet reached Microsoft. Redirecting
  that in the user's test JVM means JVM-global changes (default `SSLContext`, DNS), which a plugin author's own tests can see,
  and the resolution is not verified. The only closed placement that ran is the client **inside a container on the cell
  network**, 6 of 6 (r13, r25). So the driver is a **driver host**: one container of the existing pinned `itzg/minecraft-server`
  image (its tag is the row's `java`), started with `--entrypoint java`, running the shaded driver jar copied in (no bind
  mount, [ADR-0005](0005-containers-testcontainers-no-bind-mounts.md)). Its DNS is the stand-in (section 4) and its JVM trusts
  the cell's trust store. The harness holds `BedrockDriver implements ClientDriver` (`Edition.BEDROCK`), a thin client of the
  driver host over HTTP and JSON (client-driver.md section 6), reached the way the harness reaches an agent.
- **Consequences that came free.** The driver speaks to Geyser over the cell network, so **no UDP port is published and
  Testcontainers' UDP gap does not matter**. Geyser sees the driver's own address. The driver library and its snapshot are
  **shaded and relocated** into one jar (like the agents); users' Gradle and Maven graphs never see a snapshot or the
  OpenCollab repository.
- **Pin.** In `gradle/libs.versions.toml`: the **exact timestamped snapshot version**, `3.0.0.Beta15-20261116.161745-6` (the
  Spigot API 1.8.8 snapshot is pinned the same way: a frozen timestamped build, T-M1-03), never `-SNAPSHOT`, never a range.
  A repository entry `https://repo.opencollab.dev/maven-snapshots/` is added with `exclusiveContent` for the Cloudburst groups
  only (`org.cloudburstmc.*`, the card lists the groups the resolved graph needs). The build records the **sha256 of every
  resolved Cloudburst artefact** and fails when one differs (as the NeoForge artefacts are checked, T-M8-06), and fails when
  the resolved graph of `drivers/bedrock` contains a `-SNAPSHOT` or a dynamic version anywhere. T-M9-05 first checks whether a
  release carries the 2225 codec; if one does, the pin moves to it in the same PR and the snapshot rules below are not needed.
- **If the snapshot repository drops the pinned build:** resolution fails loudly on a clean checkout (a machine with the
  artefact in its Gradle cache keeps working, which is why the checksum list is the authority, not the cache). The fix is a
  gated PR that moves the pin to a newer timestamped build or a release, with the real-server runs of T-M9-05 behind it. There
  is no vendored copy of the jars in the repository and no mirror in M9; a maintainer-owned mirror is a separate decision if a
  prune is ever seen (the same stance as ADR-0016 for BungeeCord on Jenkins).
- **Packet surface (M9, v1).** Join, chat and command, text and system messages in, inventory read, position read,
  disconnect. Every unknown field in a packet the driver reads is an error, never a default (AGENTS.md section 3). A window
  click, `useBlock`, resource packs and forms are not in v1: they throw `UnsupportedOperationException` naming the edition and
  this ADR. The item registry needs its own limit and definitions (the spike's two decode errors), which T-M9-05 solves.

### 3. `Edition` in the driver SPI
- `ClientDriver.edition()` returns `Edition` (`JAVA`, `BEDROCK`), an enum in `proofstone-core` (internal, **not** public API).
  `mcpl` and `mineflayer-ref` report `JAVA`. `ProtocolInfo` for a Bedrock driver carries the Bedrock protocol number (2225) and
  the game version; it is never compared with a Java protocol number.
- A cell offers one driver per edition. A Geyser cell has `JAVA` (`mcpl`, for the injected `Client`) and `BEDROCK`. Core
  refuses a driver whose edition does not match the endpoint it is asked to join (a Java driver on Geyser's UDP port) with an
  error naming both. A Bedrock driver on a non-Geyser cell is an error, never a skip, unless the test declared
  `@Requires(BEDROCK_CLIENT)` (section 8).
- Bridging stays Java-only: ViaProxy is never put in front of a Geyser cell, and the planner refuses it with an error naming
  this ADR (a cell is native or it is not planned, as ADR-0016 section 3).

### 4. The offline login route and the Microsoft-names stand-in
- **Route.** Floodgate. Geyser `java.auth-type: floodgate`, `validate-bedrock-login: false`, Floodgate installed with a
  per-cell key. The **offline route without Floodgate is not used**: the player then has no prefix and no Floodgate UUID, so a
  plugin's Bedrock branch cannot run, and with Floodgate installed Geyser treats `offline` as Floodgate anyway (spike). The
  agent sees the player as `.<name>` (Floodgate's default prefix `.`, left default), with the Floodgate UUID built from the
  client's claimed XUID (the driver claims a numeric XUID, unique per join: the spike's duplicate-XUID and empty-XUID reds).
- **This is a labelled test-only deviation from production auth.** A Geyser cell has Xbox authentication disabled:
  `validate-bedrock-login: false` is "NOT SUPPORTED" by Geyser, allows any claimed name and XUID, and Geyser's own text says
  Floodgate "will not work" with it off (it worked in the spike). Proofstone therefore says so everywhere the cell appears:
  `cell.json` carries `"bedrockAuth": "offline-stub"`, the report and display name say `offline-stub`, the platform doc and
  user guide carry the warning, and a plugin whose behaviour depends on Xbox-verified identities **cannot be tested here**
  and this is stated in the docs. The Bedrock port is never published to the host (section 6).
- **The stand-in (owned by the harness, no new image).** A small Java program in `proofstone-core`, built at Proofstone's build
  time (Java 17 bytecode, JDK classes only: `DatagramSocket` for DNS, `com.sun.net.httpserver.HttpsServer` for TLS) and copied
  into **a third container of the existing pinned image** (`--entrypoint java`), started by the Geyser host before the server.
  It is the prototype's `HostLog` as production code. It does three things:
  1. **DNS** for the cell: it answers the two Microsoft discovery names with its own address and forwards every other name to
     Docker's resolver, writing every name asked to its log. The server and the driver host use it as their only DNS server
     (`--dns`).
  2. **TLS on 443** with a certificate generated per run (SAN: the two names), serving invented discovery, OpenID and JWKS
     documents. They carry no Microsoft key; that is fine only because login validation is off.
  3. **A name guard:** the host fails the cell with the log tail if a name on a deny list (Xbox and Microsoft account hosts,
     for example `login.live.com`, `*.xboxlive.com`, `*.xboxlive.cn`) was ever asked. The two stand-in names are expected.
     The guard is a check on the names the cell looked up; it cannot see traffic to an address that was never looked up.
- **Trust.** The harness copies the pinned image's `cacerts` out of the container, adds the stand-in certificate with the
  JDK `KeyStore` API, and copies the result into the server and the driver host (never mounted). Both JVMs get
  `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=<path> -Djavax.net.ssl.trustStorePassword=changeit`. The certificate is
  produced with the `keytool` of the JDK that runs the harness (as the spike did; a JRE-only harness fails loudly), generated
  per run in a temp directory, held in memory and deleted after. Neither key nor certificate is committed.
- **Egress is not closed.** The spike removed the server's default route (`NET_ADMIN`) to prove the join. The cell does not:
  the first start downloads Paper as every Paper cell does. What the cell guarantees is the list above (names answered, names
  asked, no Xbox configuration anywhere, an unsigned chain), and the conformance run asserts the stand-in's log (section 9).
  The claim in the docs is "no Microsoft or Xbox name is looked up at join", never "no packet leaves".
- **Judged and rejected: a stand-in owned by the harness JVM** (DNS through container extra hosts to the host gateway, a TLS
  endpoint in the test JVM). Port 443 (and 53 for a DNS answer) on the host needs privileges and may be taken (IIS, Hyper-V,
  another run), `host-gateway` routing and firewalls differ between Docker Desktop and Linux, and a test JVM that listens on
  443 is a surprise for a user's CI. The container route ran in the spike on the pinned image and holds both ports without
  privileges on the host. Ownership stays with the harness code and lifecycle (generated documents, start, stop, guard).
- **Geyser's other start-up fetches** (update check, Mojang manifest and locale jar, two STUN hosts) are optional.
  `stun-discovery: false` is set; the rest warn at WARN and are allowlisted with that reason (section 6).

### 5. Geyser config, Floodgate key, agent tokens
- **Rendering.** The Geyser host renders `plugins/Geyser-Spigot/config.yml` from a template resource of the pinned build's own
  default file, replacing these keys only: `bedrock.transport: both`, `bedrock.webrtc-port: 19133` (NetherNet's UDP port must
  differ from RakNet's), `bedrock.signaling.stun-discovery: false`, `java.auth-type: floodgate`,
  `advanced.bedrock.validate-bedrock-login: false`. Paper gets `connection-throttle: -1` (the Java default client joins; the
  proxy hosts do the same). The spike generated the file at a first start and edited it before a restart (about 15 s more);
  that proven path stays the fallback if Geyser rejects a pre-rendered file, which T-M9-06 settles by a run.
- **Floodgate key.** 16 random bytes from `SecureRandom` per cell, held in memory, copied to `plugins/floodgate/key.pem` before
  the first start (pre-seeding survived 3 of 3 joins). Geyser on the same server uses Floodgate's key, so no second key file is
  written. The key is redacted as the forwarding secret is (ADR-0016 section 8): the bundle writer drops `plugins/floodgate`
  and replaces the key in every other file and log (raw, hex and base64 forms), and a unit test greps every bundle file for it.
- **Capabilities and tokens.** A new capability `BEDROCK_CLIENT` (section 8), held when the Geyser pack declares it **and** the
  agent reports a new token `floodgate`, reported by the Bukkit agent when the `floodgate` plugin is enabled (a plugin lookup
  by name; no Floodgate API dependency; read-only). So a cell whose Floodgate failed to load does not hold it. `SIMULATED_BEDROCK`
  keeps its meaning (a Java client under a `.` name) and is not renamed. The agent token and the agent's wire protocol are
  otherwise unchanged; T-M9-06 checks whether the health schema enumerates tokens and, if so, bumps the minor version with
  the golden files, else adds no bump (AGENTS.md section 3).
- The agent shows no Bedrock-specific field and none is added. A test reads the player under the Floodgate name.

### 6. Binding, readiness and the log guard
- **No published UDP.** The driver is on the cell network (section 2), so nothing is published for Bedrock. The Java port is
  bound to `127.0.0.1` on a free host port because the cell has a network (ADR-0016 amendment). A host-side binding for a
  developer who wants to join with a real Bedrock client is **not built**; if a card adds it, it is `-p 127.0.0.1::19132/udp`
  through a create-command modifier with the host port read from the container's inspect data (`getMappedPort` throws, spike),
  bound to loopback only, and a debug option. Geyser's NetherNet and signaling ports are never published.
- **Readiness**, in order: the stand-in answers DNS and TLS; the server's log has `Started Geyser on UDP port 19132` and
  Paper's `Done (`; the agent's `health` answers with the `floodgate` token; the driver host answers its own health. A failed
  start reports every container's log tail and stops every container (ADR-0016 section 9 style). The spike measured 25 to 27 s cold and 14 to 17 s warm for the server alone.
- **Log guard.** The Geyser cell has its own list, per container. The three `XBOX AUTHENTICATION IS DISABLED ON THIS GEYSER
  INSTANCE!` lines are the harness's own doing (section 4) and are allowlisted with that reason, the exact lines taken from a
  run. The locale-cache and update-check WARNs are allowlisted with "optional fetch, no Mojang route needed". Every later line
  is added with a reason from a reviewed run (empty-then-grow, as ADR-0016). `JAVA_TOOL_OPTIONS` prints `Picked up ...` on
  stderr, which is not a log line. A new ERROR line fails the test.

### 7. Join accounting
- A Bedrock join is stage-named like a Java join (ADR-0016 section 9): `RAKNET_CONNECT`, `NETWORK_SETTINGS`, `LOGIN_SENT`,
  `ENCRYPTION_HANDSHAKE`, `RESOURCE_PACKS`, `START_GAME`, `SPAWN_STATUS`. A timeout is a `JoinTimeoutException` whose message
  starts `timed out joining` and carries the stage, plus the Geyser and stand-in log tails. A server disconnect with a message is
  its own error naming the message (for example the Xbox refusal text), never reported as a timeout. Bedrock join timeouts are
  counted **apart** from Paper, topology and loader numbers (M9 Flake rule). No retry, no sleep ([ADR-0007](0007-no-sleeps-no-retries.md)).
- The driver waits for the agent to list nobody before the next login of the same name (the server notices a client's
  disconnect only after about 10 s, spike); this is an await on the agent, never a sleep.

### 8. Versions, identity and rows
- **Identity:** `geyser@<geyser-version>+<backend>@<mc>`, for example **`geyser@2.12.1+paper@26.3`**. It is used unchanged
  for `-Dproofstone.cells=`, `printMatrix`, the report, `cell.json` and the bundle directory, with ADR-0016's `+` form. Floodgate
  is not in the identity: its pin is part of the Geyser row. `geyser` is a reserved id (not a platform, proxy or loader id) and
  appears only left of the `+`; a Geyser id on the right or a proxy on the left of a Geyser pairing is a selector error.
- **A Geyser cell is not a topology cell.** There is no proxy, no backends and no `Topology` object: one server, the plain
  `Agent`, the injected Java `Client`. It is a *Geyser cell*, started by a new `GeyserHost` that owns a `PaperHost`, the
  stand-in and the driver host (as `TopologyHost` owns several `PaperHost`s). The `+` form is shared; capabilities `PROXY` and
  `TOPOLOGY` are **not** held.
- **`versions.yml` schema 5 (additive).** A new optional top-level key `geyser`, a sibling of `platforms`, `proxies` and
  `loaders`, and a `drivers.bedrock` entry. A schema 4 file is a valid schema 5 file, every other key is read as before, and
  every existing cell's `printMatrix` line stays identical. The spec is [versions-yml.md](../specs/versions-yml.md). Rows carry
  the Geyser pin (`version`, `build`, `sha256`), the Floodgate pin (`version`, `build`, `sha256`), the Bedrock protocol the
  build is run with, `java` (required, as proxies: the aux containers use that tag's pinned digest), `status`, `backends`
  (paired `paper` rows with their own `status`), `observed` and `notes`. A Geyser cell's status is the weakest of the Geyser
  row, the pairing and the backend row. A pairing is native only: the Bedrock protocol equals `drivers.bedrock.protocol` and the
  backend row's protocol equals `drivers.mcpl.protocol`.
- **Downloads.** The jars come from `download.geysermc.org/v2` by version and build, are downloaded **by the harness JVM**,
  verified against the row's sha256 (a mismatch fails the cell) and copied into `/plugins` (no bind mount). Geyser never fetches
  a jar; its mappings are inside its jar, so the sha256 pins them (spike).
- **Required rows only through ADR-0015.** The first rows are `experimental`. Promotion needs the ADR-0015 table with C-G01 to
  C-G04 in place of C01 to C08 and three green invocations, **and** a driver library that is a release, not a snapshot (so a
  pruned repository cannot turn a `required` row red). A `required` Geyser pairing needs a `required` Geyser row and a
  `required` backend row. The pinned build number is stable (Geyser's API publishes a sha256 per build); a removed build is a loud
  failure at download and a row update, as for BungeeCord.

### 9. Test API (additive, `api-change`)
Detail in [test-api.md](../specs/test-api.md) section 14. Additions only: `Platform.GEYSER`, `Capability.BEDROCK_CLIENT`,
`Clients.bedrock(String name)` (a **default** method, so existing implementers compile), no change to any frozen v0 signature.
- `@Matrix(platform = Platform.GEYSER, versions = {"latest"})` plans Geyser cells from the `geyser` rows in scope, as
  `Platform.VELOCITY` plans topologies. Geyser rows are `experimental`, so a class plans one only when `-Dproofstone.cells`
  names it (the loader-cell rule); otherwise `NOT SELECTED (not run, not a pass)`.
- `clients.bedrock("Name")` joins through the driver host and returns a `Client`, awaited to the spawn status. The name is 1 to 15
  characters from `A-Za-z0-9_` (Floodgate prepends one `.`); the `Client` reports the server-side name `.Name`, so
  `agent.player(client)` works. Methods a Bedrock session does not support in v1 throw `UnsupportedOperationException`.
- On a cell without `BEDROCK_CLIENT`, a test with `@Requires(BEDROCK_CLIENT)` is `SKIPPED` with `CAPABILITY_MISSING`
  (`missing capability BEDROCK_CLIENT on cell paper@26.3`); calling `clients.bedrock` there without the declaration is an
  `IllegalStateException` naming the cell and the capability. `SIMULATED_BEDROCK` never claims real Bedrock support.
- **Dual oracle.** The server oracle is the Bukkit agent under the Floodgate name. The client oracle is the Bedrock driver's
  inventory view, built from the server-synced packets and the item definitions the driver reads (never predicted). `DualAssert.item`
  compares id and count; Bedrock has no data components, so a Bedrock client's view is compared on what Geyser translates, and a
  difference is asserted as it is and written to `geyser.md` (T-M9-08), never weakened. Where the two disagree it is `DISAGREE`.
- A differential or flake-detector run on a Geyser cell is refused with an error in M9 (no second Bedrock driver).
- **Conformance:** C-G01 to C-G04 (join with the Floodgate name, command reply, inventory read, a plugin's Bedrock-only branch)
  plus a first check that the stand-in was asked for exactly the two expected names and the name guard is clean. A red makes
  every test on that cell `INVALID`.

## Consequences
- M9 delivers a real Bedrock client against Geyser and Floodgate on one native row, with no new image, no workflow, no account
  and no secret. The price is a **Microsoft-names stand-in and `validate-bedrock-login: false`**, so the cell is "offline-stub",
  not production auth, and says so; plugins that need verified identities are out of reach.
- A Geyser cell runs three containers (server, stand-in, driver host). It is slower than a
  Paper cell and has its own join-stage numbers.
- The driver lives in a container, not in the user's JVM: more machinery (an HTTP client, a shaded jar, a jar copy) than
  `mcpl`, but no UDP code, no Microsoft route in the user's JVM and no snapshot in users' graphs.
- The pinned snapshot can disappear; the answer is a loud failure and a PR, not a fallback.
- New work is carded: T-M9-04 (schema 5, planner, `Edition`), T-M9-05 (driver and driver host), T-M9-06 (Geyser host, stand-in,
  key, agent token), T-M9-07 (API), T-M9-08 (C-G01 to C-G04), T-M9-11 (negative controls), T-M9-12 (docs).
- Risks written down, not hidden: Geyser accepting a pre-rendered config is unproven; the Docker DNS and trust-store route is
  proven for the server and a Java client in the spike but not through Testcontainers; two Bedrock clients at once and any
  window or inventory round trip are unrun; the stand-in's documents are invented and rely on login validation being off.

## Alternatives considered
- **An Xbox or Microsoft test account.** Rejected. It needs a secret and a live Microsoft call at run time (AGENTS.md: no
  secrets, ADR-0006: nothing unpinned or external at run time), is rate limited and subject to the service's terms, and the
  results would depend on Microsoft's availability.
- **Real Microsoft hosts with login validation on.** Rejected. Not offline, and an unsigned chain is refused (3 of 3 reds in
  the spike), so it cannot work without an account.
- **Node `bedrock-protocol` as the driver (a sidecar like `mineflayer-ref`).** Rejected for M9. It made no Microsoft call, but a
  Node image on the cell network does not exist and a new image is out of M9; run on the host it needs Node installed by every
  user and a UDP binding the harness does not have. It cannot set an XUID (all its Floodgate players share the nil UUID, so two
  at once collide), it works only with the `raknet-native` prebuilt binary (the pure JS RakNet cannot join Geyser), and it
  cannot be pinned by the Gradle catalog. It may return as a reference driver for differential runs if a Node image is ever
  approved.
- **CloudburstMC in the harness JVM (in-process, like `mcpl`).** Rejected. The user's JVM fetches Microsoft documents at class
  initialisation (shown), the redirect needs JVM-global changes that are unverified, and it needs a host UDP binding that
  Testcontainers does not give. Revisit only if a release of the library removes the fetch.
- **A stand-in in the harness JVM**, or a **new stand-in image.** Rejected: privileged ports and portability for the first,
  the no-new-image limit for the second (section 4).
- **No stand-in, with a route to Microsoft.** Rejected: a Microsoft host is contacted at the first login (5 runs), which is "not
  offline". **No stand-in and no route:** the join fails with `An internal error occurred!` (1 red) and is kept as a negative
  control.
- **The offline route without Floodgate.** Rejected: no prefix, no Floodgate UUID, no Bedrock branch to test.
- **Closing the server's default route (`NET_ADMIN`) as the spike did.** Rejected as a rule: the first start needs Paper's
  download, the capability is a new privilege, and the name guard states what is guaranteed instead.
- **A Geyser Standalone container in front of the server**, as ADR-0016's proxies. Rejected for M9: it was not run, needs its own
  jar, config and key sharing, and the plugin form is what ran.
- **`Edition` in the public API** (`Client.edition()`). Rejected: a test needs no more than `clients.bedrock(...)`, and a public
  enum is hard to remove.
- **Vendoring the snapshot jars or committing a Maven mirror.** Rejected for M9 (size, licence bookkeeping, "pin, do not copy").
- **Bedrock Dedicated Server in M9, or a BDS script agent now.** Rejected by the maintainer: a Mojang binary with its own EULA
  and no redistribution, a new image or zip needing explicit approval, and a beta Script API. Deferred to M10.
- **Promoting a Geyser row on a snapshot-pinned driver.** Rejected: a pruned snapshot would turn a `required` row red for a
  reason unrelated to the plugin under test.
