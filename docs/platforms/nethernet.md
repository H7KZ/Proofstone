# NetherNet (the WebRTC transport of Bedrock) and the Bedrock driver

Spike T-M10-19, 2026-11-20, Windows 11 host, Docker Desktop. Research from primary sources (library source, the Mojang protocol
guide, the compiled PowerNukkitX 3.0.6 jar) and runs by hand under `scripts/dockerlock.sh` with `scripts/spike/nethernet/`.
Nothing here is built: no driver change, no `versions.yml` or matrix change, no protocol change, no ADR filed. Run output lived in
`build/spike/nethernet/` (not committed). Where a run did not show a thing, the text says "not run" or "from source". Context:
[bds.md](bds.md), [nukkit.md](nukkit.md), [geyser.md](geyser.md), [ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md).

## Verdict

**Go, behind an ADR: the Bedrock driver should get NetherNet.** A throwaway Java client built only from what the PowerNukkitX 3.0.6
jar already ships (CloudburstMC NetherNet transport, libdatachannel natives, the protocol fork) joined PowerNukkitX 3.0.6 offline
over NetherNet, reached `PLAYER_SPAWN` and was listed by the agent, twice in a row with two names, XUIDs and keys (r7: 3.4 s and
2.1 s per join, no route out). The transport part is small and works; what took six runs was the Bedrock side (session timing,
compression, login claims, chunk count), none of it specific to NetherNet beyond two `NullPointerException`s. Rough cost: one to
two weeks for the driver host plus a Geyser cell, most of it in the dependency move and shading the native library.

Why it matters beyond PowerNukkitX:
- **The client side moved.** Bedrock 26.60 (the driver's own 1.26.60, protocol 2225) is the release from which the retail client
  stops connecting over RakNet (GeyserMC blog "preparing for NetherNet"; OpenCollaboration "NetherNet" article; both 2026). The
  driver speaks a transport no real 2225 client uses any more.
- **The servers moved.** BDS 1.26.66.1 logs NetherNet as "the only supported transport type" ([bds.md](bds.md)); PowerNukkitX 3.0.6
  and Nukkit master removed RakNet ([nukkit.md](nukkit.md)); Geyser 2.12.1 defaults to NetherNet and keeps RakNet only with
  `bedrock.transport: both` ([geyser.md](geyser.md)). Geyser's RakNet is kept "for older clients"; when Geyser drops it, the M9
  Geyser cell stops working.
- **The cost is a snapshot-only dependency with a native library** (below), which is why it needs an ADR, not just a card.

## How a direct (non-Xbox) NetherNet join works

Shown = seen in a run here. Source = read in the code or a primary document, not run.

| Step | What happens | Evidence |
|---|---|---|
| Discovery and probe | The client sends `GET /v1/join` to the server's signalling endpoint (TCP, same port number as the game, 19132 on PowerNukkitX). 2xx means "serves NetherNet"; the JSON body is the server-list status (name, protocol, version, players, `onlineAuth`, `selfSignedAuth`, `transportLayer`). 404 means "no NetherNet" (the retail client falls back to RakNet). The retail client tries HTTPS first and falls back to plain HTTP only when the answer was not TLS. | shown (r1 to r3: `{"protocol":2225,"version":"1.26.60",...,"onlineAuth":false,"selfSignedAuth":true,"transportLayer":2}`, HTTPS refused, HTTP answered); Mojang onboarding guide |
| Offer and answer | One `POST /v1/join/<networkId>` with the whole SDP offer (`application/sdp`), the SDP answer in the response. No trickle ICE, host candidates only, no STUN or TURN needed on one network, UDP only. | shown (connect in about 200 ms); guide; `NetherNetHTTPClientSignaling` |
| Identity in the offer | The offer carries an identity assertion (`a=identity`): a JWT naming the player (`xid`, `xname`) and a key `cpk`, plus a detached signature over the SDP fingerprints made with that key. Retail clients present a token from Minecraft's auth service. The server's answer must always carry its own identity; over plain HTTP the retail client pins the server key on first use (a prompt). | source (guide; `TokenTrust`, `IdentityUtils`, `OperatorIdentity`) |
| Who accepts an offline identity | The server chooses a `TokenTrust`. `MINECRAFT_AUTH` (the library default) checks the token against Minecraft's keys; `ANY` reads it unverified (the `cpk` binding and expiry still hold). **PowerNukkitX 3.0.6 always uses `ANY`** (compiled `Network.class`: `getstatic TokenTrust.ANY`), so a self-made identity (`OperatorIdentity.generate(...).forPlayer(xuid, name)`, a fresh key per run) passes. BDS's policy is not known (not run). | shown for PNX; source |
| Media | DTLS + SCTP over UDP (libdatachannel). Two data channels: `ReliableDataChannel` (ordered, reliable, fragmented with a 1-byte "segments remaining" header, 10,000-byte SCTP messages, at most 256 segments) and `UnreliableDataChannel` (unordered, no retransmit). PowerNukkitX hands out an ephemeral UDP port per peer when Query holds udp/19132. | shown (connect, data both ways); source (`NetherNetConstants`) |
| Bedrock on top | **The same Bedrock packets.** One NetherNet message = one compressed Bedrock batch, without RakNet's `0xFE` frame id. After that it is the RakNet-era flow unchanged: `RequestNetworkSettings`, `NetworkSettings` (compression), `Login`, the game's own ECDH encryption handshake, resource packs, `StartGame`, spawn. | shown (see the run log); PowerNukkitX `NetherNetFrameCodec` (compiled) |
| Login with Xbox auth off | Unchanged from RakNet: PowerNukkitX `LoginHandler` refuses a self-signed chain only when `xboxAuth: true`. | shown (r7: `LOGIN_SUCCESS`, `StartGame`, `PLAYER_SPAWN` with `xboxAuth: false`); r8 below for `xboxAuth: true` |
| LAN discovery | A separate mechanism on UDP 7551 (encrypted broadcast discovery). Not needed for a direct join on a cell network. | source (`NetherNetConstants.DISCOVERY_PORT`), not run |

Not shown: a join with no stand-in for the two Microsoft discovery names (the server, not the client, looks them up at the first
login, as on RakNet: [geyser.md](geyser.md)), BDS over NetherNet (needs a fresh maintainer yes for the Mojang binary in this card; see open questions), Geyser over
NetherNet, TLS signalling, a server with `MINECRAFT_AUTH`.

## What a Java client needs (library facts)

| Piece | Coordinates and version (2026-11-20) | Licence | Size | Maintenance | Natives |
|---|---|---|---|---|---|
| NetherNet transport (client and server) | `org.cloudburstmc.netty:netty-transport-nethernet` `2.0.0.CR6-20261115.174122-26` (branch `nethernet` of `CloudburstMC/Network`, head `134d26db` 2026-11-15); **snapshots only** (`repo.opencollab.dev/maven-snapshots`, nothing in releases or Central) | Apache-2.0 | 269,598 bytes | very active (646 commits on the branch; Geyser and PowerNukkitX ship it) | none of its own |
| WebRTC data channels | `dev.opencollab:libdatachannel-java` `0.24.6.0-20261105.085043-11` (fork of `pschichtel/libdatachannel-java`, binding of `paullouisageneau/libdatachannel`); **snapshots only** | MPL-2.0 (binding and libdatachannel) | 98,543 bytes (Java) + 1,457,899 (`linux-glibc-x86_64`) | binding: 1 star, 6 forks, OpenCollab-run; libdatachannel itself is a mature C++ project | yes: 8 classifiers (Linux glibc and musl x86_64 and aarch64, Windows x86_64 and aarch64, macOS x86_64 and arm64); Linux needs OpenSSL 3 on the system |
| Transitive | Netty 4.1.130 (the driver has 4.1.101), `bcpkix`/`bcutil`/`bcprov-jdk18on` 1.85, gson 2.13.2, jose4j 0.9.6 (the driver has it), expiringmap 0.5.10 (has it) | Apache-2.0 / MIT-style (Bouncy Castle) | Bouncy Castle about 12.4 MB, gson 0.3 MB | releases | none |
| Bedrock framing | ~40 lines in the driver: a frame codec (batch without `0xFE`), the initial compression, `BedrockPeer.getRakVersion()` pinned to 11 | ours | | | |

- CloudburstMC Protocol upstream (branch `3.0`, which the driver pins) has **no** NetherNet initializer; Geyser and PowerNukkitX each
  wrote their own few lines, as the spike did.
- **Placement fits ADR-0018 unchanged.** The driver host already runs in a Linux container of the pinned image, so only
  `linux-glibc-x86_64` (and `aarch64` for ARM hosts) is needed. The pinned `java25` image had the OpenSSL 3 the native needs (the
  native loaded in every run). Windows natives exist but no Windows placement is needed.
- **The library retries a handshake by default** (`NETHER_CLIENT_MAX_HANDSHAKE_ATTEMPTS`, and the HTTP signalling sends one offer per
  attempt). ADR-0007 needs it set to 1; the spike did.

## Effort (guess unless marked shown)

| Part | Estimate | Basis |
|---|---|---|
| Transport switch in the driver host (`transport: raknet \| nethernet` per session, HTTP signalling, identity, frame codec, stages `SIGNALLING`, `NETHERNET_CONNECT`) | 2 to 3 days | shown: the spike client is about 60 lines of transport code on top of the T-M9-01 join |
| Dependency move: Cloudburst netty 1.1.0.CR4 to 2.0.0.CR6 (RakNet and NetherNet from one line), Netty 4.1.101 to 4.1.130, new pins and sha256 list, shading relocation of the native loader and natives | 2 to 4 days | guess; the native in a relocated shaded jar is the unknown (libdatachannel-java loads by resource path) |
| PowerNukkitX cell (host from the T-M10-13 spike, a real agent) | 1 to 2 weeks | guess; outside this card, a platform milestone |
| Geyser cell on NetherNet (`transport: nethernet`, so the M9 cell survives a RakNet removal) | 2 to 3 days | guess; Geyser 2.12.1 serves built-in signalling on TCP 19132 (geyser.md) |
| BDS | unknown | depends on BDS accepting a self-made offer identity and an unsigned chain over NetherNet; not run |

## Dependency and licence impact

- New runtime dependencies of the driver host (shaded, never in a user's graph): `netty-transport-nethernet` (Apache-2.0),
  `libdatachannel-java` with one or two native classifiers (MPL-2.0), Bouncy Castle (MIT-style), gson (Apache-2.0). The shaded
  jar grows from 11.4 MB to about 26 MB (guess from the sizes above).
- **MPL-2.0** is file-level copyleft: shipping the unmodified binary inside our Apache-2.0 jar is allowed; the obligations are to keep
  the licence text and say where the source is (NOTICE and the shaded jar's `META-INF`). Modifying MPL files would oblige us to
  publish those files. ADR-0011 does not list MPL today; the ADR below adds it.
- **Snapshots again**, from the same repository and the same rules as ADR-0018 section 2 (timestamped pin, sha256 list, no
  `-SNAPSHOT` in the graph, no promotion of a row while it is a snapshot). Two more groups (`org.cloudburstmc.netty` moves line,
  `dev.opencollab`).
- **A native library in the driver host** is new for Proofstone. It runs only in the container (ADR-0018 placement), never in the
  harness JVM.

## Risks

- **Snapshot churn.** Both libraries exist only as timestamped snapshots on one repository; a pinned snapshot can be deleted. Mitigation
  as ADR-0018: sha256 list, a local cache, no promotion while snapshots.
- **Native library.** libdatachannel needs OpenSSL 3 in the image and must survive shading and relocation (unproven); a crash in
  native code takes the driver host JVM down, which ADR-0007 must report as a driver failure, not a test failure.
- **BDS unknown.** Whether BDS accepts a self-made offer identity (token trust) is not known; if it does not, NetherNet buys
  PowerNukkitX and Geyser only, and BDS stays out of reach without an account (forbidden).
- **Protocol drift.** PowerNukkitX checks client JWT claims strictly (r3 to r5) and the driver pins upstream Protocol `3.0` while
  PowerNukkitX ships a fork with renamed accessors; each Bedrock release can add a required claim.
- **Moving spec.** NetherNet signalling over HTTP is new (2026); Mojang may change the endpoint, identity rules or require TLS.

## Draft ADR (not filed)

> **ADR-00NN: NetherNet transport in the Bedrock driver host**
>
> - Status: Proposed. Date: 2026-11-NN. Deciders: maintainer.
>
> **Context.** Bedrock 26.60 clients connect only over NetherNet; BDS 1.26.66.1, PowerNukkitX 3.0.6 and Nukkit master accept only
> NetherNet; Geyser 2.12.1 defaults to it. The driver (ADR-0018) speaks RakNet only. Spike T-M10-19 ([nethernet.md](nethernet.md))
> joined PowerNukkitX 3.0.6 over NetherNet offline with CloudburstMC's transport, an unverified self-made offer identity and the
> unchanged self-signed login chain.
>
> **Decision.** The driver host gets a second transport, NetherNet, chosen per session (`transport` in the session request; the
> default stays RakNet until a cell asks for NetherNet). Library: `org.cloudburstmc.netty:netty-transport-nethernet` and
> `dev.opencollab:libdatachannel-java` (MPL-2.0) with the `linux-glibc-x86_64` and `linux-glibc-aarch64` natives, pinned as exact
> timestamped snapshots with sha256 like ADR-0018 section 2, shaded and relocated into the driver-host jar, never in the harness
> JVM. HTTP signalling only (plain HTTP on the cell network; no STUN, TURN, external signalling or Xbox signalling). The offer
> identity is a key made per session that names the claimed XUID and name; it is accepted only by a server whose token trust is
> `ANY` and is labelled `offline-stub` like the login chain. One handshake attempt, no retry (ADR-0007). New join stages
> `SIGNALLING` and `NETHERNET_CONNECT` replace `RAKNET_CONNECT` on NetherNet sessions. ADR-0011's licence list gains MPL-2.0 for
> unmodified binaries, with the licence text and source pointer in NOTICE.
>
> **Consequences.** PowerNukkitX (and, if a run shows it, BDS) can have a driver; the Geyser cell can drop `transport: both`. The
> driver host grows by about 15 MB and carries a native library and two more snapshot groups; a row on a NetherNet cell cannot be
> promoted while they are snapshots.
>
> **Alternatives.** Stay on RakNet (servers and clients are leaving it); a Node or Go NetherNet client (no image, no catalog pin,
> ADR-0018's Node objections); writing WebRTC in Java (DTLS, SCTP and ICE from scratch: months); `webrtc-java` (media-oriented,
> native, no data-channel-only build, not used by any Bedrock project).

## First implementation cards (proposed, not filed)

1. **T-M11-xx · ADR for NetherNet in the driver host** (tier opus): file the draft above after the maintainer's yes, amend
   ADR-0011 (MPL-2.0) and ADR-0018 (stages, transport).
2. **T-M11-xx · Driver host: NetherNet transport** (tier sonnet; depends on 1): pins and sha256 list for the two groups, Netty
   4.1.130, the transport switch, frame codec, identity, stages, one handshake attempt; unit tests for the frame codec and the stage
   names; real-server test: the spike's PowerNukkitX join through the driver host RPC, three green invocations.
3. **T-M11-xx · Geyser cell over NetherNet** (tier sonnet; depends on 2): `bedrock.transport: nethernet`, the driver on NetherNet,
   C-G01 to C-G04 green three times; `both` stays as the fallback until it is green.
4. **T-M11-xx · Spike: BDS over NetherNet** (tier opus; needs the maintainer's yes for the BDS binary): does BDS 1.26.66.1 accept a
   self-made offer identity and an unsigned chain with `online-mode=false`? Reopens T-M10-02 only if it does.

## Run log

Invocation for every run (Git Bash, from the repository root; the client is compiled by the script against the cached PowerNukkitX
jar, `/c/pw/nukkit-cache/3.0.6/powernukkitx.jar`, sha256 `10822d81...9539`, checked before every run; no new download):

```bash
PROOFSTONE_ACCEPT_EULA=true MSYS_NO_PATHCONV=1 \
  bash scripts/dockerlock.sh bash scripts/spike/nethernet/nethernet-spike.sh build/spike/nethernet/<run>
```

All runs: PowerNukkitX 3.0.6, `EGRESS=closed`, `SCHEME=AUTO`, `TRUST=1`; probe and signalling worked in every run (HTTPS refused,
plain HTTP `GET /v1/join` 200 with the status above, connect in about 200 ms). The only names looked up (`dns.log`) were the two
Microsoft discovery names, by the server at the first login, answered by the stand-in; the client looked up nothing.

| Run | Knobs | Result | Change after it |
|---|---|---|---|
| r1 | `JOINS=1` | rc 4, `NullPointerException` sending before the session existed | await the session after `connect()` |
| r2 | same | rc 3, `TIMEOUT stage=network-settings` after 45 s: `NullPointerException` in `setCompression` (no RakNet protocol option) | pin `getRakVersion()` to 11 |
| r3 | same | rc 3, `NetworkSettings` ok, then `DISCONNECT INVALID_PLATFORM_SKIN` | read PowerNukkitX `LoginHandler`, `ClientChainData` (javap) |
| r4 | same | rc 3, same refusal: `ClientChainData.from` returns null when a 1.26.60 client claim is missing; the reason sent is the skin one | add `CompatibleWithClientSideChunkGen`, `GraphicsMode`, `ClientIsEditorCapable`, `MaxViewDistance`, `MemoryTier`, `PartyId`, `PlatformType`, `ClientEditorConnectionIntent` (checked offline against the jar first) |
| r5 | same | rc 3, `PlayStatus LOGIN_FAILED_EDITION_MISMATCH_EDU_TO_VANILLA`: the client sent `IsEduMode: false`, and the server reads its presence | drop `IsEduMode` |
| r6 | same | rc 3, `LOGIN_SUCCESS`, `StartGame`, 49 `LEVEL_CHUNK` and about 15,000 packets over 45 s, no `PLAYER_SPAWN` (spawnThreshold 56) | chunk radius 4 to 8 |
| r7 | `JOINS=2` | **rc 0 twice**: `PLAYER_SPAWN` at 2,705 ms and 1,308 ms, hook `{"players":["PsNether1"]}` and `["PsNether2"]`, server log "logged in", agent lists nobody after each leave | |
| r8 | `XBOX_AUTH=true`, `JOINS=1` | rc 3 as expected: probe shows `onlineAuth:true, selfSignedAuth:false`, the NetherNet connect with the self-made identity still succeeds (token trust `ANY`), then `DISCONNECT NOT_AUTHENTICATED` at login: the refusal is at the login chain, as on RakNet | |

## Gotchas

- PowerNukkitX 3.0.6 serves signalling on **TCP** 19132 and Query on UDP 19132, so NetherNet media gets an ephemeral UDP port per
  peer (`network-settings.nethernet.udpPort` fixes it). A container network needs no published port; a host-side join would.
- PowerNukkitX writes a server identity to `/srv/keys/identity.pem` at the first start; replacing it re-prompts every retail player
  (trust on first use). A cell would generate it per run.
- The Bedrock session exists only once the NetherNet channel is active, after `connect()` returns (r1: a send before it was a
  `NullPointerException`); await it, bounded.
- `BedrockPeer.setCompression` reads the channel's RakNet protocol option, which a NetherNet channel does not have (r2: a
  `NullPointerException` at `NetworkSettings`); pin `getRakVersion()` to 11 as PowerNukkitX's server side does.
- PowerNukkitX 3.0.6 refuses a login whose client JWT lacks any claim of the 1.26.60 client (`ClientChainData.from` returns null)
  and reports it as `INVALID_PLATFORM_SKIN` (r3, r4), and it treats the mere presence of `IsEduMode` as an Education client (r5).
  The driver's T-M9-01 client data needs the new claims for PowerNukkitX whatever the transport.
- PowerNukkitX sends `PLAYER_SPAWN` only after `chunks.spawnThreshold` (56) chunks; a chunk radius of 4 never gets there (r6).
- The PowerNukkitX protocol fork (`org.powernukkitx.protocol:bedrock-connection` `3.0.0.Beta7-Debug-SNAPSHOT`) renamed packet
  accessors (`setClientNetworkVersion`, `setChain`, `getHandshakeWebToken`, `ResourcePackResponse`); the spike client compiles only
  against it, not against the driver's upstream Beta15.
