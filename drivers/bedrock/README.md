# drivers/bedrock

## Purpose

The Bedrock client driver host (ADR-0018, [client-driver.md](../../docs/specs/client-driver.md) section 6): a program on CloudburstMC Protocol that
joins a Bedrock server (Geyser on a Geyser cell) with an unsigned offline login and serves driver RPC v1 over HTTP and JSON. It runs in its own
container on the cell network, never in the harness JVM. `BedrockDriver implements ClientDriver` (`Edition.BEDROCK`) lives in
`proofstone-core` (`core.driver.bedrock`) and is a thin client of this host. Both sides compile against one contract class, `BedrockRpc`.

## Boundaries

- **No online auth.** No Xbox or Microsoft account, token or secret. The login chain is self-signed per join with a claimed name and a claimed
  numeric XUID, unique per join; only a Geyser with `validate-bedrock-login: false` accepts it (ADR-0018 section 4).
- **Never in the harness JVM**, and never publishes a UDP port. The host listens on loopback port 25590 of its container; the harness calls it with
  curl inside the container (`ExecCurlTransport`).
- **Observes and never predicts.** Every view (messages, inventory, position) is what the server sent. An unknown packet field, a text type, a play
  status, an item without a definition or a packet that does not decode fails the session with the field named, never a default.
- **v1 surface only:** join, chat, command, `awaitMessage`, inventory and position read, disconnect. Window clicks, `useBlock`, forms,
  resource packs (answered "have all" without downloading) and `awaitServerSwitch` are out: they throw `UnsupportedOperationException`.
- No sleeps and no retries (ADR-0007): waits are on a condition with the remaining time. A join that stalls is a `JoinTimeoutException` naming the stage.
- No dependency beyond Jackson, the Cloudburst graph and, for NetherNet, libdatachannel-java with its Linux natives (ADR-0021), all shaded into one jar and relocated, except `tel.schich.libdatachannel`, which JNI binds to its own name (`ShadedJarCheck.JNI_PACKAGES`). Nothing here may reach a user's Gradle or Maven graph.
- Core classes in the host jar are the `rpc` package only (`BoundaryTest`).

## Public surface

- Host program: `java -jar bedrock-host-all.jar <bearer token>` (`BedrockHost`), built by `:drivers:bedrock:shadowJar`; prints
  `bedrock driver host ready: RPC v1, ...` when it listens.
- Driver RPC v1 (`BedrockRpc`): bearer token, the agent's envelope `{ok, data}` or `{ok:false, error:{code, message, stage, lastPacket}}`,
  `X-Proofstone-Request-Id` echoed. `GET /rpc/v1/health`; `POST /rpc/v1/sessions` (host, port, name, xuid, joinTimeoutMillis); under
  `/rpc/v1/sessions/{id}`: `POST chat`, `POST command`, `POST await-message` (regex, timeout), `GET messages?since=`, `GET inventory`,
  `GET position`, `GET transcript`, `DELETE`. Errors: `BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `JOIN_TIMEOUT` (with the stage),
  `DISCONNECTED` (the server's own text), `TIMEOUT` (never a partial result), `PROTOCOL_ERROR`, `INTERNAL`.
- Join stages, each entered at its event: `RAKNET_CONNECT` (RakNet) or `SIGNALLING` then `NETHERNET_CONNECT` (NetherNet, ADR-0021), `NETWORK_SETTINGS` (the transport is up), `LOGIN_SENT` (network settings in, login sent),
  `ENCRYPTION_HANDSHAKE` (handshake answered), `RESOURCE_PACKS` (login success), `START_GAME` (packs answered), `SPAWN_STATUS` (start game in).
- Reads: the player inventory (container 0) as identifier, runtime id and count per slot; the position as the feet (Bedrock sends the eye, 1.62 higher).
- Raised read limit: the connection uses the library's client limit profile (the default list limit, 1536, is below the server's 2088 item
  registry entries) and installs the start game packet's item definitions before the creative content arrives (the spike's two decode errors).
- Pins: `gradle/libs.versions.toml` (`cloudburst-*`, exact timestamped builds), mirrored in `versions.yml` `drivers.bedrock`. No release of CloudburstMC
  Protocol exists (checked 2026-11-17: Maven Central and the OpenCollab releases repository have no `org.cloudburstmc.protocol`), so the
  OpenCollab snapshots repository is an `exclusiveContent` repository for `org.cloudburstmc.protocol`, `.netty` and `.math` only, and
  `cloudburst-artefacts.sha256` lists every resolved artefact. When a release carries codec `Bedrock_v2225`, move the pin and drop the snapshot rules.

## How to test

```bash
./gradlew :drivers:bedrock:test :proofstone-core:test    # no Docker: scripted packets, RPC contract over loopback HTTP, pin check
./gradlew :drivers:bedrock:check                          # also builds the shaded jar and checks it (no unrelocated class, no snapshot entry)
```

`test` runs `checkCloudburstPins` first: every resolved runtime artefact must match its line in `cloudburst-artefacts.sha256`, no resolved version may be a
snapshot or dynamic, and a stale line fails. To change a version, re-derive the lines from a fresh download of each jar, never from the Gradle cache.

NetherNet (T-M12-09, ADR-0021): `scripts/spike/nethernet/driver-host-join.sh` boots PowerNukkitX 3.0.6 (jar cached under `/c/pw/nukkit-cache/`, sha256 checked) and the shaded host jar on a closed cell network, joins through the RPC with `"transport":"nethernet"`, asks the agent for the player list, leaves and asks again (run it under `scripts/dockerlock.sh` with `PROOFSTONE_ACCEPT_EULA=true`; runs are recorded in [nethernet.md](../../docs/platforms/nethernet.md)). The two natives in the shaded jar are checked by `checkShadedJar` (right ELF machine, no other native code).

There is no other real-server test in this module: the real join runs in `:proofstone-core:geyserTest` (T-M9-06, `GeyserCellIT`), where it passed 3 of 3 on
`geyser@2.12.1+paper@26.3`: the exec-curl transport in a container, the encryption handshake against the stand-in and the spawn status. That run found
two decode bugs (the item definitions come in `ItemComponentPacket`; the creative content needs a block registry, `RuntimeIdBlocks`), fixed here. Still
**unverified**: chat, command and window round trips, two clients at once.
