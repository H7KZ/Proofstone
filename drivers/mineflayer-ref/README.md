# drivers/mineflayer-ref

## Purpose

The reference client driver (ADR-0004): Mineflayer in a sidecar container, independent of MCProtocolLib, for
differential runs on rows where it is native. The Java side (`MineflayerRefDriver`, `MineflayerRefSession`) implements
the client SPI from `proofstone-core`. The sidecar (`images/mineflayer-ref`) runs one bot per WebSocket connection and
answers JSON-RPC 2.0. Its protocol and the protocol finding are in [mineflayer-ref](../../docs/platforms/mineflayer-ref.md).

## Boundaries

- Never the primary driver. `mcpl` is (ADR-0004).
- Speaks only the protocol `MC_VERSION` in `images/mineflayer-ref/server.js` (26.1, protocol 775). Older rows run
  bridged through ViaProxy, and the sidecar never talks to a server directly when a bridge is needed.
- Replies only with what the server sent. Nothing is predicted.
- Windows, blocks, the player inventory and items are read from the server's syncs only. A click the server
  does not answer fails, rather than passing.
- Waits go through `Await` (AGENTS.md section 3). The one timed wait outside it is the sidecar's own `connect` reply, which
  the Java side bounds with the connect timeout.

## Public surface

- `io.github.h7kz.proofstone.driver.mineflayer.MineflayerRefDriver(URI sidecar)`: `ClientDriver` with id `mineflayer-ref` and
  protocol `26.1.2` (the native row). `sidecar` is the WebSocket address as the test JVM sees it.
- `connect(ConnectSpec)`: `host` and `port` are as the sidecar sees them, on the cell network (the server alias
  `server:25565`, or `viaproxy:25568` on a bridged cell).
- Sessions offer `chat`, `command`, `awaitMessage`, `position`, `experienceLevel`, `transcript` and `close`.
- Test-only fault (T-M4-06): `MineflayerRefDriver.FAULT_PROPERTY` (`proofstone.injectDriverFault`). With `true` the
  player inventory reports one extra damage point on its first non-empty item. Nothing else changes, so only a
  differential run sees it. The bad-tests build sets it for its differential run and nothing else does.

## How to test

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew :drivers:mineflayer-ref:integrationTest --no-parallel
```

Docker is needed. The integration test builds the sidecar image from `images/mineflayer-ref`, joins the native row
26.1.2 directly and the bridged row 1.21.11 through ViaProxy, and checks the join message, a `/version` reply and the
disconnect report.

The conformance scenarios C02 to C06 run on the reference driver with `-Dproofstone.driver=mineflayer-ref`:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh ./gradlew --no-daemon --no-parallel \
  -Dproofstone.driver=mineflayer-ref -Dproofstone.cells=paper@26.1.2,paper@1.21.11 :conformance:run \
  --tests '*ConformanceSmokeTest*' --tests '*C03*' --tests '*C04*' --tests '*C05*' --tests '*C06*'
```
