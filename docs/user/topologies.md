# Topologies: Velocity or BungeeCord in front of Paper backends

**Status: experimental.** A topology cell is a proxy container plus N Paper backends on one Docker network
([ADR-0016](../adr/0016-topology-cells.md)). Rows in `versions.yml` `proxies` are `experimental`, not `required`: the
rule in [ADR-0015](../adr/0015-local-evidence-row-promotion.md) needs more runs than M7 has. What ran is listed under
[What was run](#what-was-run). Anything not listed there has not been run.

## What you get

- A test class that declares `@Topology.Backends` (or a `Topology` parameter) runs on a topology cell. The client joins
  through the proxy and lands on the first backend.
- `Topology.server("lobby")` is the server oracle of that backend, an ordinary `Agent` (inventory, config, events).
- `Topology.proxy()` is the proxy's agent. It holds no game state: `proxy().player(..)` fails loudly.
- `client.transferTo("survival")` is a **server switch**: the client types `/server survival`, and the call returns when the
  client's driver saw the switch and the agents agree (the proxy lists the target, the target lists the player, the source
  does not). It is not the vanilla 1.20.5 transfer packet. Nothing is retried.
- The injected plain `Agent` is the default backend's.

## A Velocity example

The example is [`ServerSwitchE2E`](../../examples/smoke/src/test/java/io/github/h7kz/proofstone/examples/smoke/topology/ServerSwitchE2E.java):

```java
@Proofstone
@Matrix(platform = Platform.VELOCITY, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})          // optional: these are the defaults
class ServerSwitchE2E {

    @CellTest
    void eachBackendIsTheOracleWhileTheClientIsOnIt(Topology topology, Client client) {
        topology.server("lobby").player(client).give(Item.of("minecraft:apple").amount(3));
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);

        client.transferTo("survival");
        topology.server("survival").player(client).give(Item.of("minecraft:stick").amount(2));
        DualAssert.item(topology.server("survival").player(client), client).inInventory(i -> i.is("stick")).amount(2);
        // each backend keeps its own inventory: nothing from lobby shows here
        assertThat(client.inventory()).noneMatch(i -> i.is("apple"));

        client.transferTo("lobby");
        DualAssert.item(topology.server("lobby").player(client), client).inInventory(i -> i.is("apple")).amount(3);
    }
}
```

- Backend names match `[a-z][a-z0-9]{0,15}`, are unique and are not `proxy`. The first is the proxy's default server. Without
  `@Topology.Backends` a topology cell runs `lobby` and `survival`.
- `transferTo` with an unknown backend, or the one the client is on, is an `IllegalArgumentException` before anything is sent.
- Plugins under test go in `@Proofstone(plugins = ...)` as on a single server; they are installed on every backend.

## Select a topology cell

A `@Matrix` that names a proxy platform plans topology cells: `Platform.VELOCITY` or `Platform.BUNGEECORD`, paired with
`paper@latest` as listed in `versions.yml`. To run one cell, name it with `+` in the selector, one cell per invocation:

```bash
PROOFSTONE_ACCEPT_EULA=true bash scripts/dockerlock.sh \
  ./gradlew :examples:smoke:topologyExample -Dproofstone.cells='velocity@4.3.1+paper@26.3' --no-parallel --max-workers=1
```

Read <https://aka.ms/MinecraftEULA> first (the harness never accepts it). The `topologyExample` task exists because
`:examples:smoke:test` leaves the topology class out. Differential runs and the flake detector on a topology cell are refused
with an error. Rows today are `velocity@4.3.1+paper@26.3` and `bungeecord@2111+paper@26.3`.

On a single-server cell (`paper@26.3`), a test class with `@Topology.Backends` is `SKIPPED (not a pass)` with
`missing capability TOPOLOGY on cell paper@26.3`, before any server boots. A test with a `Topology` parameter but neither
`@Topology.Backends` nor `@Requires(TOPOLOGY)` fails with `INVALID test declaration`.

## Conformance first

Before the first test on a topology cell, P01 to P05 run once and are cached: join through the proxy, forwarding, a server
switch, plugin messaging and a proxy command ([self-tests spec](../specs/self-tests.md)). A red one makes every test on the
cell `INVALID`, and your test body never runs. See
[troubleshooting](troubleshooting.md#topology-join-or-switch-timeout-names-a-hop).

## What was run

- The example above: **3 of 3 green** invocations on `velocity@4.3.1+paper@26.3`, one cell each, `tests=1 skipped=0
  failures=0 errors=0` in each XML (T-M7-14).
- The same body, through the public API, on both proxies: Velocity 3 of 3 green, BungeeCord 2 of 3 green with one red from a
  Mojang profile lookup, fixed afterwards (ROADMAP "M7 findings", T-M7-10).
- Topology conformance P01 to P05 on both proxies (T-M7-11, T-M7-12). Faults that must turn a topology red
  (T-M7-13) are in `examples/bad-tests`.
- Not run: BungeeCord through this example, more than one proxy-version row, Waterfall (end of life, not a row), Velocity
  `none` or `bungeeguard` forwarding, Bedrock behind a proxy, a flake rate for topology joins.

Platform notes: [velocity](../platforms/velocity.md), [bungeecord](../platforms/bungeecord.md). Spec:
[test API section 12](../specs/test-api.md).
