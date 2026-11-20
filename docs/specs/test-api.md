# Spec: public test API (`proofstone-api`) v0

Status: **v0 frozen at T-M2-99** (end of M2). The frozen contract is `proofstone-api/api-baseline.txt`, guarded by
`ApiSurfaceTest`; section 8 lists what is frozen, what is experimental, and where the sketches in sections 3 to 5 go
beyond v0. After the freeze, a change to the baseline needs a card marked `api-change` and a reviewed baseline diff.

## 1. Annotations

```java
@Proofstone(
    plugins   = {"target/MyPlugin-*.jar"},     // globs relative to project dir; exactly one match each, else error
    mods      = {},                            // loader cells only, copied to /mods (§13, ADR-0017)
    datapacks = {},                            // copied to the world's datapacks before start (§13)
    acceptEula = true,                         // REQUIRED true, or env PROOFSTONE_ACCEPT_EULA=true; else tests are skipped with reason
    config    = "src/e2e/resources/proofstone.yml"   // optional per-project settings (version families, expectations)
)
@Matrix(platform = PAPER, versions = {"1.18.2", "1.21.11", "latest"})   // repeatable
@Matrix(platform = FOLIA, versions = {"latest"})
@Isolation(PER_CLASS)                          // or PER_TEST
class MyE2E { ... }

@CellTest                                      // = @TestTemplate, one invocation per cell
@Requires({Capability.BUKKIT_EVENTS})          // a cell without the capability is SKIPPED with a reason (never a pass)
void scenario(Cell cell, Agent agent, Client client) { ... }
```

- `versions` accepts exact versions, `latest` (the newest `required` row) and `all` (every row in scope, see below).
- Run-time selector: `-Dproofstone.cells=paper@1.21.11,folia@latest` or the environment variable `PROOFSTONE_CELLS`
  (the property wins if both are set). Entries are `platform@version` with a lower-case platform and a version that is
  an exact version, `latest` or `all`. When set, it **replaces** the `versions` of every `@Matrix` whose platform it
  names, so `paper@1.8.8` runs even if the class says `latest`. A `@Matrix` whose platform it does not name is not run,
  and the run prints `PROOFSTONE NOT SELECTED` on stderr. That is not a JUnit skip, so a selector run over a multi-platform class
  stays `skipped="0"`. A class for which the selector names none of its `@Matrix` platforms fails by name, listing each row as
  `NOT SELECTED: ...`: it never vanishes silently, and is never a skip or a pass.
- Scope: `-Dproofstone.scope=pr|nightly` or `PROOFSTONE_SCOPE` (default `pr`). It decides what `all` takes:
  - `pr`: the `required` rows, plus the newest version of each version family (see below);
  - `nightly`: every `required` and `experimental` row, plus every family version; `retired` rows never, unless named.
  Explicit exact versions are not filtered by scope.
- Version families: a `proofstone.yml` in the test working directory (the project directory under Gradle) may map
  platform ids to named lists of exact versions. A missing file means none. In v0 it is read from that fixed path; the
  `config` attribute above is a post-v0 idea (section 8.4).

  ```yaml
  version-families:
    paper:
      netty: ["1.8.8", "1.12.2"]
      modern: ["1.20.4", "26.3"]
  ```
- Errors, never skips: an unknown selector form, an unknown platform or version, a `TODO` pin in a selected row, an
  `all` with no row in scope, and a family version that is not a row of its platform.

## 2. Injected parameters

| Type | Scope | Purpose |
|---|---|---|
| `Cell` | per invocation | `platform()`, `version()`, `javaTag()`, `bridged()`, `capabilities()`, `driver()` |
| `Agent` | per invocation | server oracle plus setup (§3) |
| `Client` | fresh per test | client oracle plus actions (§4). A unique name per test, offline mode. |
| `Clients` | per test | `clients.create("Alice")` for multi-player tests |
| `Topology` | per invocation | topology cells (proxy and backends): `topology.server("lobby")` and `topology.proxy()` return an `Agent`; `client.transferTo("survival")` is a server switch ([ADR-0016](../adr/0016-topology-cells.md), T-M7-10) |

## 3. `Agent` (server oracle and setup)

All calls block until the agent confirms, and all return immutable snapshots.

```java
PlayerHandle p = agent.player(client);          // or agent.player("name")
p.reset();                                       // inv clear, cursor clear, xp 0, survival, full health, tp to fixture spot
p.gamemode(SURVIVAL).level(30).give(Item.of("diamond_sword").enchant("sharpness", 5));
PlayerState s = p.state();                       // level, exp, gamemode, location, inventory, cursor, openWindow
PlayerState s2 = p.awaitState("book in inventory", st -> st.inventory().contains("enchanted_book"));

agent.world().setBlock(pos, "anvil");            // block ids are namespaced strings; agent maps per version
agent.world().block(pos);                        // BlockState snapshot
agent.command("gamerule keepInventory true");    // setup only; returns CommandResult(raw text) — never assert on it
agent.plugin("MyPlugin").patchConfig("path.to.key", value);   // snapshot is auto-restored after the test
agent.plugin("MyPlugin").reload();               // via disable/enable or a declared reload hook
EventProbe e = agent.events().subscribe("com.example.MyEvent"); // reflective, MONITOR, read-only
e.awaitOne().getInt("xpCost");                   // getters serialised to JSON
agent.awaitTicks(2);
agent.assertNoErrorsLogged();                    // pack patterns + allowlist
agent.logLines();                                // lines since the test started, as plain text (T-M11-01)
agent.bootLogLines();                            // lines from the agent's enable up to the test start (T-M11-03)
agent.logLines(Pattern.compile("NMS module: (\\S+)"), Duration.ofSeconds(10));   // waits for a match, returns the matching lines
```

## 4. `Client` (client oracle and actions)

```java
client.command("version MyPlugin");              // sends "/version MyPlugin"
client.awaitMessage(Pattern.compile("MyPlugin version"));
try (Window w = client.openBlock(pos)) {         // interacts with block; awaits window open
    w.type();                                    // "minecraft:anvil"
    w.moveFromInventory(i -> i.is("diamond_sword"), 0);   // pickup + place, server-authoritative
    ItemView r = w.awaitSlot(2, i -> i.is("enchanted_book"));
    w.click(2, Click.LEFT);                      // also SHIFT, RIGHT, NUMBER_KEY(n), DROP, DOUBLE, SWAP_OFFHAND
    w.cursor();                                  // ItemView or empty
    w.dropCursorIntoFirstEmptySlot();
    w.rename("Blade");                          // anvil only: types the rename box
}                                                // close() awaits server close + inventory resync
client.inventory();  client.experienceLevel();  client.position();
```

## 5. Dual-oracle assertions

```java
DualAssert.item(agent.player(client), client)
    .inInventory(i -> i.is("enchanted_book"))
    .storedEnchants(Map.of("sharpness", 5, "unbreaking", 3));   // checked on BOTH; mismatch → DISAGREE
DualAssert.level(agent.player(client), client).isEqualTo(17);
DualAssert.item(...).serverOnly("PDC is not sent to clients").pdc("myplugin:key", "v");
```

- Both sides are awaited, using the default timeout of 10 s.
- A failure prints the expected value, the server value, the client value and the elapsed time.

## 6. Item spec (`Item`) and views (`ItemView`)

These map 1:1 to the wire schema `item.json`:

| Field | Meaning |
|---|---|
| `type` | `"minecraft:diamond_sword"` |
| `amount` | stack size |
| `enchants` | map of enchantment to level |
| `storedEnchants` | map of enchantment to level, for enchanted books |
| `name` | display name |
| `lore` | lore lines |
| `damage` | durability damage |
| `repairCost` | anvil repair cost |
| `pdc` | persistent data container values |
| `raw` | the server's native serialisation, read-only, for debugging |

Enchantment keys are always namespaced lowercase strings. **Levels are never clamped** in transport.

## 7. Verdicts

The results are `PASS`, `FAIL`, `INVALID`, `VACUOUS`, `FLAKY`, `DISAGREE` and `SKIPPED(reason)`. Only
`PASS` and `SKIPPED` with an allowed reason count as green. The `SKIPPED` reasons are: capability
missing, Docker unavailable, EULA not accepted, the cell was not selected, or the test aborted itself with a reason
(`TEST_ABORTED`, T-M9-08: `Assumptions.abort(reason)` in the body; the report says `SKIPPED` with that reason, as JUnit does).

## 8. The v0 contract (frozen at T-M2-99)

**Rule.** The sketches in sections 1 to 6 describe the target API. The v0 contract is exactly what
`proofstone-api/api-baseline.txt` lists (every public member of `io.github.h7kz.proofstone.api`). `ApiSurfaceTest` fails on any
difference, so adding, removing or changing a member needs a card marked `api-change`, a regenerated baseline
(`./gradlew :proofstone-api:test -Dproofstone.updateApiBaseline=true`) and a reviewed diff. There is no separate version
constant: the baseline file plus this status line are the v0 marker. A card that breaks v0 must say so and bump to v1.

### 8.1 Frozen in v0
- Annotations `@Proofstone`, `@Matrix` (with `Matrices`), `@CellTest`, `@Isolation` and `Platform`.
- Injected `Cell` (`platform`, `version`, `javaTag`, `bridged`), `Agent` and `Client`.
- `PlayerHandle` (`name`, `reset`, `level`, `give`, `state`, `awaitState`), `PluginHandle` (`name`, `config`,
  `patchConfig`), `Events` and `EventProbe`.
- `Window` (`type`, `title`, `slots`, `slot`, `awaitSlot`, `cursor`, `click`, `moveFromInventory`,
  `dropCursorIntoFirstEmptySlot`, `rename`, `close`), `Click`, `Item` (build), `ItemView` (read), `BlockPos`, `Position`.
- `DualAssert` (`item`, `level`) and `DisagreeException`.

### 8.2 Experimental: in the baseline, but allowed to change in a v0.x step
Every item here is marked in the Javadoc or this list, and a change still needs a reviewed baseline diff.
- `io.github.h7kz.proofstone.api.spi.OracleWaiter` and its records: a service provider interface for `proofstone-core`, not for
  test authors.
- `DualAssert.serverOnlyReasons()` and `ServerOnly.reason()`: a stop-gap until reports exist (M4).
- Constructors of the records `PlayerState`, `CapturedEvent`, `ItemView`: they will gain components (`location`).
  Test authors read them and never construct them. Accessors that exist today are frozen.
- `CapturedEvent.getInt` (accepts `xpCost` and `getXpCost`) and `fields()`: the value types are whatever the agent's
  JSON serialisation gives. Typed accessors may be added; the existing ones stay.
- `Window.close()` returns after sending the close. It does not yet await the server close or the inventory resync,
  as the sketch in section 4 says. Waiting longer is a compatible change.

### 8.3 Deviations from the sketches (the baseline wins)
- `Click`: `NUMBER_1` to `NUMBER_9` and `Click.number(n)` replace `NUMBER_KEY(n)`, because an enum has no parameters.
- `Agent.command(String)` returns the raw output as a `String`, not a `CommandResult`. Never assert on it.
- `Cell` has no `driver()`. `Cell.capabilities()` was added in T-M5-01 (section 10); `Agent.capabilities()` stays the raw
  list the agent reports.
- `Item` has no `repairCost` or `pdc` setter, and `ItemView.repairCost()` reads 0 from the agent, because the agent's
  `give` and item read do not carry them yet. `ItemView` has `pdc()` (server only) and `customData()` (client only).
- `Client.experienceLevel()` replaces `experience()` (it returns the level only) and throws until the server's first
  experience sync. `Client` has `openBlock` (top face), `inventory`, `position`, `awaitPosition`.
- `Agent.logLines()` and `Agent.logLines(Pattern, Duration)` (T-M11-01, api-change, additions only, default methods that throw): the server
  log since the test started, one `LEVEL logger: message` line per record (a multi-line message or stack trace adds a line per
  line). The window is the log cursor the lifecycle takes before the client joins, so boot lines are not in it. The second form
  waits (through `Await`) for a line that contains a match of the pattern and returns every matching line. A ring buffer that
  dropped records since the cursor is an `IllegalStateException`, never a short list. Backed by `GET /v1/log`, so no protocol
  change and no capability; a proxy `Agent` and every backend `Agent` of a topology read their own container.
- `Agent.bootLogLines()` (T-M11-03, api-change, addition only, a default method that throws): the lines before the test window,
  `GET /v1/log?since=0` cut at the lifecycle's log cursor, so it and `logLines()` are disjoint and together are every captured line.
  Covered: from the agent's own `onEnable` (it loads at STARTUP) on, which holds the enable lines of every ordinary plugin and
  the `Done` line. Not covered: what the server logged before the agent enabled (banner, libraries, the plugin loading list,
  and the agent's own "Enabling" line). The agent ring holds 5000 records; a dropped record is an `IllegalStateException`.
  No protocol change, no version bump, no capability.
- `PlayerState.openWindow()` and `ServerWindow` (T-M11-02, api-change, additions only): `Optional<ServerWindow>` as a new last component of
  `PlayerState` (the 7-argument constructor stays). It is the server's view of the container the player has open, from the
  `openWindow` field `player-state.json` already has, so no protocol change and no capability. `ServerWindow` has `type` (the
  platform's name as the agent sends it: the Bukkit family sends the lower-cased `InventoryType`, such as `chest`, not the
  client's `minecraft:generic_9x3`), `title` (as the server holds it) and `slots` (the container's own slots in server order,
  without the player's 36; an empty slot is `ItemView.EMPTY`). Empty when only the player's own inventory is open. The
  Fabric and NeoForge agents cannot read a window title, so reading the state of a player with a window open fails there
  (`NOT_SUPPORTED`, [fabric](../platforms/fabric.md)). The window is a snapshot: wait with `awaitState`.
- `Events.subscribe(className)` gives an `EventProbe` (`awaitOne`, `events`, `close`). A `CapturedEvent` has
  `className`, `tick`, `thread`, `fields` and `getInt`; an unknown getter is an error, never a default.
- `DualAssert.item` offers `inInventory`, `amount`, `enchants`, `storedEnchants`, `name` and `serverOnly(reason).pdc`.
  A failure is `DisagreeException` when exactly one oracle matched, `AssertionError` starting `FAIL` when neither did,
  and `AssertionError` starting `ORACLE MISSING` when an oracle never answered. Waits go through `OracleWaiter`.
- `@Isolation(PER_CLASS|PER_TEST)` exists. Before each test whose method declares a `Client`, a fresh client joins and
  the player is reset; after it the log since the pre-test cursor is checked (WARN and worse, minus the allowlist),
  patched configs are restored and the client disconnects.

### 8.4 Not in v0 (post-v0 backlog)
These appear in the sketches and are **not** part of the contract. Each needs an `api-change` card.
- `Agent.world()` (`setBlock`, `block`): blocks are placed with `command` today, and the agent has the endpoints.
- `Agent.plugin(..).reload()`, `Agent.awaitTicks` and the explicit `Agent.assertNoErrorsLogged()`
  (the per-test log check runs automatically; the explicit call is not exposed).
- `PlayerHandle.gamemode` and `PlayerState.location`.
- Experience points and the progress bar (`PlayerHandle.exp`, `Client.experience`).
- `Item.repairCost`, `Item.pdc`, and a real `ItemView.repairCost`.
- `Clients` (multi-player). `Topology` moved out of this list in T-M7-10 (section 12).
- Test hygiene: the world fixture region reset and the leak detector (T-M2-08 left them out).
- `Cell.driver()`.
- Verdict reporting (`serverOnlyReasons` is its stand-in).

`Cell.capabilities()`, `@Requires` and `Capability` moved out of this list in T-M5-01 (section 10).

## 9. Per-cell expectations and error patterns (T-M4-15, post-v0 additive)

Added by T-M4-15 as an `api-change`: `CellExpectation` and `Expect` are new members of `io.github.h7kz.proofstone.api`, and
`api-baseline.txt` gains exactly those four lines. No frozen v0 signature changed.

```java
public class SmokeE2E {
    public static final class NmsModuleOnThisVersion implements CellExpectation {
        @Override public void check(Cell cell, Agent agent) {
            // read the server (observe only), then assert on what the suite expects for cell.version()
        }
    }

    @CellTest
    @Expect(NmsModuleOnThisVersion.class)       // or on the class: every @CellTest in it
    void scenario(Agent agent) { ... }
}
```

- **Where it runs.** After the test body and the log check, on each cell the test runs on. It runs only when the test
  is otherwise green, and before the leak detector. A class-level and a method-level `@Expect` both run, class first.
- **Verdict.** A failure throws, and any exception counts. The test is reported `FAIL` with the message
  `FAIL expect <class> on <platform>@<version>: <message>`. A class that cannot be created (it needs a public
  no-argument constructor) is also a `FAIL`, never a pass. Every expectation runs, so the report names each failing one.
- **Read only.** The check observes after the test's actions and never changes the server. Its message is the reason a
  wrong version mapping (robustness R7) fails the cell.
- **Error patterns per pack (robustness R8, R9).** `ErrorLogGuard` takes its lists from the cell's platform pack. A pack
  supplies an allowlist (known vanilla noise, each with a reason) and an error-pattern list: a record that matches an
  error pattern fails the test even when the allowlist also matches it. Paper supplies the allowlist of
  `docs/platforms/paper.md` and no error patterns yet. A platform with no list fails loudly, never passes silently.
  Folia's thread-check patterns come with the Folia row (before M5).
- **Bad test.** `examples/bad-tests/.../expect/ExpectFailsE2E` holds one wrong expectation (it must produce `FAIL`, asserted
  by `metaExpect` in `metaTest`) and one holding expectation (it must stay `PASS`).

## 10. Capabilities and `@Requires` (T-M5-01, post-v0 additive)

Added by T-M5-01 as an `api-change`: `Capability`, `Requires` and `Cell.capabilities()` are new members of
`io.github.h7kz.proofstone.api`, and `api-baseline.txt` gains exactly those lines. No frozen v0 signature changed.

```java
@Requires(Capability.BUKKIT_EVENTS)            // on a class: every test in it
class EventsE2E {
    @CellTest
    @Requires({Capability.CONSOLE, Capability.FOLIA_REGIONS})   // on a method: added to the class's requirements
    void scenario(Cell cell, Agent agent) { ... }
}
```

- **What a cell holds.** `Cell.capabilities()` is the set of capabilities that the cell's platform pack declares **and**
  the agent reports. The agent's report is the token list of `GET /v1/health`. Two rules are in code
  (`CapabilityModel`): a pack declares a fixed set (Paper: `BUKKIT_EVENTS`, `CONSOLE`, `SIMULATED_BEDROCK`), and a
  declared capability is held only when its token is reported (`BUKKIT_EVENTS` = `events`, `CONSOLE` = `command`).
  A platform with no pack declares nothing. Version strings never imply a capability.
- **Where it runs.** After the server boots and before conformance, so the check reads the agent's report. It is judged
  per cell and per test, from the class's and the method's `@Requires` together.
- **Verdict.** A cell missing a required capability is `SKIPPED` with reason `CAPABILITY_MISSING` and the detail
  `missing capability X on cell <platform>@<version>` (several names are joined by `, `). The test body, the conformance
  verdict and the log check do not run, and no bundle is written. JUnit reports the test as skipped with the same message.
  A skip is never a pass (AGENTS.md section 3).
- **Removed.** `NMS` and `DATAPACKS` were declared with no agent token, so a `@Requires` on either skipped every cell.
  The fix PR (`fix/m5-capability-api`, api-change, M5 review decision 4) removed both. A capability is re-added only together
  with the agent token that reports it: NMS checks belong in the per-cell `expect` hook (section 9), and `DATAPACKS_VANILLA`
  returns in M8 with the token `datapacks` (ADR-0017 §8, T-M8-09). **Re-added by T-M8-09** as `DATAPACKS_VANILLA` (last
  constant of the enum, additive): the Paper and Fabric packs declare it and it is held when the agent reports `datapacks`
  (`GET /v1/datapacks`). Folia, Purpur, Spigot and NeoForge declare nothing, so a data-pack test skips there with
  `missing capability DATAPACKS_VANILLA`.
- **`Cell.capabilities()` is a default method (fix PR, M5 review decision 2).** It throws
  `UnsupportedOperationException` unless overridden, so a custom `Cell` that is used with `@Requires` fails loudly. The core
  `CellView` overrides it. Source-compatible for existing implementers; the baseline line changes from abstract to default.
- **Bad test.** `examples/bad-tests/.../requires/RequiresSkipsE2E` requires `FOLIA_REGIONS` on a Paper cell (its body
  fails if it runs) and requires held capabilities (it passes). `metaRequires` asserts the skip, its reason in
  `proofstone-report.json`, and the pass.

## 11. `Clients` and the simulated Bedrock tier (T-M5-06, post-v0 additive)

Added by T-M5-06 as an `api-change`: `Clients` is a new member of `io.github.h7kz.proofstone.api`, `Capability.SIMULATED_BEDROCK`
is a new constant, and `api-baseline.txt` gains exactly those lines. No frozen v0 signature changed. This is the single-
client slice of the `Clients` item in section 8.4: a test joins extra Java clients under names it chooses. Multi-player
topologies stay in the backlog.

```java
@Proofstone(plugins = "build/bedrock-prefix-plugins/*.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@Requires(Capability.SIMULATED_BEDROCK)
class BedrockPathE2E {
    @CellTest
    void bedrockOnlyPath(Client client, Clients clients) {
        Client bedrock = clients.create(".BedrockBot");
        // ... drive the plugin's Bedrock-only branch with `bedrock` and assert on `client` ...
    }
}
```

- **What it is.** `clients.create(name)` logs a Java client in through the cell's driver (offline mode) and returns it
  once it is in the play state. The name is 1 to 16 characters from `A-Z a-z 0-9 _`, optionally with one leading `.`. A
  name that does not fit is an `IllegalArgumentException` before anything connects. The `.` prefix is the name-prefix
  detection that Geyser and Floodgate use, so a plugin's Bedrock branch can run.
- **What it is not.** It is a simulation over the Java protocol. No Bedrock client joins, and no test may say that a
  plugin supports Bedrock because it passed here (docs/platforms.md, Bedrock Edition).
- **Lifecycle.** Clients created by `Clients` are disconnected after the test, with the test's own client. Nothing is reset
  for them: `reset` is for the test's `Client` only.
- **Where a cell holds it.** `SIMULATED_BEDROCK` is declared by the Paper pack and held when the agent reports the token
  `bedrock-prefix` (offline servers only) on a **native** cell. A bridged (ViaProxy) cell does not hold it: on
  paper@1.21.11 the prefixed login stalled at the proxy and never reached the server (docs/platforms/paper.md, Gotchas).
  Paper needs no username setting for the native login (verified on 26.3). Elsewhere `@Requires` skips with
  `CAPABILITY_MISSING`, and the reason names the cell.
- **Bad test.** None yet. Conformance scenario `C-B01` (`ConformanceBedrockPrefixTest`) asserts the example plugin's
  detection on a prefixed client and on a normal one.

## 12. Topologies: `Topology`, `@Topology.Backends`, `Client.transferTo` (T-M7-10, post-v0 additive)

Added by T-M7-10 as an `api-change` ([ADR-0016](../adr/0016-topology-cells.md)): `Topology` and its nested annotation
`Topology.Backends` are new members of `io.github.h7kz.proofstone.api`, `Client.transferTo(String)` is a new **default**
method, `Platform.VELOCITY`, `Platform.BUNGEECORD`, `Capability.PROXY` and `Capability.TOPOLOGY` are new constants, and
`api-baseline.txt` gains exactly those 11 lines. No frozen v0 signature changed: every addition is a new type, a new
constant or a default method, so existing callers and implementers compile and link unchanged.

```java
@Proofstone(plugins = "build/libs/MyProxyAwarePlugin.jar")
@Matrix(platform = Platform.VELOCITY, versions = {"latest"})
@Topology.Backends({"lobby", "survival"})            // optional; these are the defaults
class SwitchE2E {
    @CellTest
    void survivalGivesItsOwnKit(Topology topology, Client client) {
        client.transferTo("survival");                // types /server survival, awaits both oracles
        topology.server("survival").player(client).give(Item.of("minecraft:stick"));
        DualAssert.item(topology.server("survival").player(client), client).inInventory(i -> i.is("stick"));
    }
}
```

- **Planning.** A `@Matrix` naming a proxy platform plans topology cells: its versions paired with `paper@latest`, which must
  be a pairing listed in `versions.yml` (an unlisted one is an error). A selector entry such as
  `-Dproofstone.cells=velocity@4.3.1+paper@26.3` replaces them. A differential or flake-detector run on a topology cell is
  refused with an error in M7.
- **Deviation from the card.** The card names the annotation `@Topology`. Java cannot have an annotation and an interface of
  the same name in one package, and the injected type is `Topology` (section 2), so the annotation is `@Topology.Backends`.
- **Injected.** `Topology` (`server(name)`, `proxy()`, `servers()`); the plain `Agent` is the default backend's (the first
  name, where the client lands); `Cell` describes the backend row (`paper`, `26.3`) and holds `TOPOLOGY` and `PROXY`.
  `topology.proxy().player(..)` fails loudly: a proxy holds no game state.
- **`transferTo(name)`.** A server switch, never the vanilla transfer packet. An unknown backend or the current one is an
  `IllegalArgumentException` before anything is sent. The client types `/server <name>` once, the driver awaits the
  switch (a timeout names the stalled stage and the hop), then the agents are awaited: the proxy agent says `name`, that
  backend lists the player and the source does not. A disagreement is `DisagreeException` (`DISAGREE ...`, every agent and
  its value named), no oracle on the target is `FAIL`, an agent that never answers is `ORACLE MISSING`. Nothing is
  retried. On a single-server cell `transferTo` is an `IllegalStateException`.
- **Single-server cells.** `@Topology.Backends` and `@Requires(TOPOLOGY)` (or `PROXY`) skip with `CAPABILITY_MISSING`
  (`missing capability TOPOLOGY on cell paper@26.3`) before any server boots. A test that declares a `Topology` parameter
  without either is misdeclared: it fails with `INVALID test declaration`, never a pass.
- **Lifecycle.** Per class by default. Before each test the client joins through the proxy, the proxy agent and the default
  backend list it, and the player is reset there; the leak baseline is read on every backend. After it the log guard
  runs per container with its pack's list (Velocity and BungeeCord packs have their own, empty until a reviewed run adds an
  entry), configs are restored per agent, the client leaves (awaited on the proxy agent) and the leak detector compares
  every backend.
- **Conformance.** Until P01 to P05 exist (T-M7-11, T-M7-12), the cached per-cell check is `T00`: the proxy agent lists
  exactly the topology's backends, each online. A red makes every test on the cell `INVALID`.
- **Bundle.** `build/proofstone/<identity>/<Test>.<method>/` has `proxy/` and `server-<name>/` (log, agent transcript, and a
  snapshot per backend), plus the client transcript, `cell.json`, `host.json` and `rerun.txt`. Every agent token and the
  forwarding secret are redacted, and every file is read back to check.
- **Bad test.** `examples/bad-tests/.../requires/TopologySkipsE2E` (`metaRequires`) asserts the `TOPOLOGY` skip.

## 13. Mods, data packs and resource packs (ADR-0017, design for T-M8-07, T-M8-09, T-M8-10)

Design from [ADR-0017](../adr/0017-mod-platform-packs.md). In `api-baseline.txt` so far: `Platform.FABRIC` and `Platform.NEOFORGE`
(T-M8-08), `Proofstone.datapacks` and `Capability.DATAPACKS_VANILLA` (T-M8-09), the resource-pack types below (T-M8-10), and
`Proofstone.mods` and `Capability.MODS` (T-M8-13), each `api-change`, additions only. Each card adds its part
as an `api-change`, additions only: new annotation elements with defaults, new types, new constants, default methods.

```java
@Proofstone(mods = "build/libs/my-mod-*.jar", datapacks = "src/e2e/datapacks/my-pack")
@Matrix(platform = Platform.FABRIC, versions = {"26.3"})
class MyModE2E {
    @CellTest
    void commandWorks(Agent agent, Client client) { ... }       // server-only mod: commands, events, data
}

@Proofstone(plugins = "build/libs/MyPlugin.jar")
@Matrix(platform = Platform.PAPER, versions = {"latest"})
@ServerResourcePack(value = "src/e2e/packs/required.zip", required = true, prompt = "{\"text\":\"Please\"}")
@ResourcePackAnswer(ResourcePackAnswer.Answer.ACCEPT)
class PackE2E {
    @CellTest
    void accepted(Client client) {
        ResourcePackPush push = client.resourcePacks().get(0);  // url, hash, required, prompt, answer, statuses sent
    }
}
```

- **Loader cells (T-M8-08).** A `@Matrix` naming `Platform.FABRIC` or `Platform.NEOFORGE` plans a loader cell on a `loaders` row.
  Loader rows are `experimental`, so the matrix plans a cell only when `-Dproofstone.cells` (or `PROOFSTONE_CELLS`) names it, for
  example `fabric@26.3`; without it the class prints `NOT SELECTED (not run, not a pass)` on stderr for that platform (not a JUnit skip) and runs its
  other matrices; a class with no other matrix fails by name with `NOT SELECTED: ...` for each row, never a skip or a pass. Loader cells run with the native `mcpl` driver only, never with `-Dproofstone.differential` or `-Dproofstone.repeat`,
  and `plugins` on one is an `INVALID test declaration`. A loader pack holds `CONSOLE` (verified by C03) and no other
  capability yet.
- **New constants:** `Platform.FABRIC`, `Platform.NEOFORGE` (in the API since T-M8-08); `Capability.MODS`, `Capability.DATAPACKS_VANILLA`,
  `Capability.RESOURCE_PACKS` (tokens `mods`, `datapacks`, `resource-pack-status`; ADR-0017 §8).
- **`@Proofstone(mods, datapacks)`:** globs relative to the project directory, exactly one match each, as `plugins`. Mods go to
  `/mods`, data packs (a `.zip` or a directory with `pack.mcmeta`) to the world's `datapacks` before start. Declaring either
  implies `@Requires(MODS)` or `@Requires(DATAPACKS_VANILLA)`: a cell without it is `SKIPPED` with `CAPABILITY_MISSING`.
  `plugins` on a loader cell is `INVALID test declaration`. A test mod with the id `fabric-api` is refused (the Fabric cell
  provides the pinned Fabric API).
- **Mods, as built (T-M8-13).** `@Proofstone(mods = ...)` and `Capability.MODS` (token `mods`, declared by the Fabric and NeoForge
  packs) are in the API. Each glob matches exactly one jar (none, or more than one, is `INVALID test declaration` naming the
  glob), read from the project directory; the jar is copied to `/mods` before start. Its ids come from its own descriptor
  (`fabric.mod.json`, `META-INF/neoforge.mods.toml`), and after the capability check each must be in `GET /v1/mods`: a mod the
  agent does not list is `INVALID test declaration` naming it, with the agent's list and the log tail. A mod that crashes the
  loader is a failed boot, `INVALID` naming the mod and quoting the loader's line (T-M8-11). A jar with the id `fabric-api` (or
  one that is no Fabric or NeoForge mod) is `INVALID test declaration` before any container starts. A cell without `MODS` (every
  Bukkit-family cell) skips with `missing capability MODS`, `CAPABILITY_MISSING`. A control run installs no declared mod. A
  topology cell holds no `MODS`, so it skips. The mod config is not declarable: a mod under test runs with the defaults it has.
  Fixtures: the example mod (`platforms/{fabric,neoforge}/agent/src/exampleMod`, built by `exampleModJar`) passes in
  `examples/loader-mod` (`ServerOnlyModE2E`); its crashing variant (`exampleCrashModJar`, the same mod with one marker resource)
  is the mod of the `examples/bad-tests` `mod/Broken{Fabric,NeoForge}ModE2E`. The test-only `-Dproofstone.injectModFault` switch
  is gone: no test needs it.
- **Data packs, as built (T-M8-09).** `@Proofstone(datapacks = ...)` is in the API. Each glob matches exactly one `.zip` (with
  `pack.mcmeta` at its top) or one directory holding `pack.mcmeta`; a directory is zipped deterministically and named
  `<directory>.zip`. The zip is copied into the container before start and installed by the image's `DATAPACKS` setting into
  `world/datapacks`; nothing reloads it while a test runs. The server lists it as `file/<name>` and the extension checks that
  id in `GET /v1/datapacks` `enabled` after the capability check (a cell without the capability skips first): a missing one
  is `INVALID test declaration` naming the pack, with the log tail. A control run installs no data pack. A topology cell
  refuses a declared data pack (`INVALID test declaration`).
- **Loaded or loud.** A declared mod missing from the agent's mod list, or a declared data pack not enabled, makes the class's
  tests on that cell `INVALID`, naming the artefact. A mod that crashes the loader is a failed boot, `INVALID` naming the
  mod. A data pack that loads but logs an error fails the test through the log guard (`FAIL`).
- **`@ServerResourcePack(value, required = false, prompt = "", sha1 = "")`** (class, T-M8-10): `value` is a glob relative to the
  project directory matching exactly one `.zip`. The harness serves its bytes from the test JVM on `http://127.0.0.1:<free
  port>/proofstone-pack/<sha1>.zip` for the life of the server, and the image writes `resource-pack`, `resource-pack-sha1`,
  `resource-pack-id` (derived from the bytes), `require-resource-pack` and `resource-pack-prompt` into `server.properties`
  (ADR-0017 §7). The server never fetches the pack; the client, in the same JVM, does, so no port reaches a container. `prompt`
  must be a JSON text component (a plain string is an `INVALID test declaration`). `sha1` is the hash the server pushes
  instead of the file's, only for testing a wrong hash (40 hex digits, else `INVALID test declaration`). Implies
  `@Requires(RESOURCE_PACKS)`. A control run serves no pack; a differential run of such a test is refused (`INVALID`).
- **`@ResourcePackAnswer(ACCEPT | DECLINE | FAIL_DOWNLOAD)`** (class or method, the method wins): how every client of the test
  answers a push, its own `Client` and those from `Clients`. With no answer declared, a push ends the join at once with an error
  naming the URL; the driver never accepts silently. Statuses per answer: [client-driver.md](client-driver.md) §2b.
- **`Client.resourcePacks()`** (default method) returns `ResourcePackPush` records (`id`, `url`, `hash`, `required`, `prompt`,
  `answer`, `statuses`, `problem`, `popped`): the pushes this client saw, in order, in configuration and in play. Packs a plugin
  pushes are recorded the same way. A wrong hash is `problem` = `hash mismatch: expected <h>, got <h>`.
- **`Capability.RESOURCE_PACKS`** (token `resource-pack-status`): declared by the Paper pack and held on native cells only, never
  bridged and never on a topology (no run showed a push through ViaProxy or a proxy). The Bukkit agent reports the token where
  `PlayerResourcePackStatusEvent` exists.
- **The server's view** is read with the event probe on `org.bukkit.event.player.PlayerResourcePackStatusEvent` (fields
  `getPlayer`, `getStatus`, `getID`, `getHash`). **Paper 26.3 fires it for a play-phase answer only**, not for the configuration-
  phase push from `server.properties` (C-R01 dev run: the probe stayed empty for accept, decline and a wrong hash). A test
  therefore reads the server's view of a pack a plugin pushes in play; the configuration-phase answer is shown by the client's
  record and by the join's outcome (play, or the server's kick message). C-R01 and C-R02 do exactly that.
- **Content mods.** On Fabric a mod that adds a synced registry entry gets the client kicked at join: the test fails with the
  kick message. On NeoForge it is `unverified`; a mod-added item reaching the client is an error naming the item id.

## 14. Bedrock clients on a Geyser cell (ADR-0018, T-M9-07)

From [ADR-0018](../adr/0018-bedrock-driver-geyser-cells.md). Built by T-M9-07 as an `api-change`, **additions only** to
`api-baseline.txt`: `Platform.GEYSER`, `Capability.BEDROCK_CLIENT`, and `Clients.bedrock(String)` as a **default** method (it throws
`UnsupportedOperationException` unless overridden, as `Client.transferTo` does; core overrides it). No frozen v0 signature changes
and there is no public `Edition`.

```java
@Proofstone(plugins = "build/libs/MyPlugin.jar")
@Matrix(platform = Platform.GEYSER, versions = {"latest"})
@Requires(Capability.BEDROCK_CLIENT)
class BedrockE2E {
    @CellTest
    void bedrockOnlyBranch(Agent agent, Clients clients) {
        Client bedrock = clients.bedrock("PsBed1");          // joins through Geyser; the server sees ".PsBed1"
        agent.player(bedrock).give(Item.of("minecraft:stick"));
        DualAssert.item(agent.player(bedrock), bedrock).inInventory(i -> i.is("stick"));
    }
}
```

- **Planning.** A `@Matrix` naming `Platform.GEYSER` plans Geyser cells from the `geyser` rows in scope (versions.yml, schema 5).
  Rows are `experimental`, so a class plans one only when `-Dproofstone.cells` (or `PROOFSTONE_CELLS`) names it, for example
  `geyser@2.12.1+paper@26.3`; otherwise it prints `NOT SELECTED (not run, not a pass)` on stderr (not a JUnit skip) and, if the class has no other matrix,
  fails by name with `NOT SELECTED: ...` for each row, never a skip or a pass. A differential or flake-detector run on a
  Geyser cell is refused with an error. `plugins` works as on Paper; `mods` and `datapacks` are refused (`INVALID test declaration`).
- **Injected.** `Agent` is the Paper server's (the Bukkit agent). `Client` is the Java `mcpl` client, joined as an ordinary Java
  player. The `Cell` describes `paper@26.3` and holds `BEDROCK_CLIENT`; it does **not** hold `PROXY` or `TOPOLOGY`.
- **`clients.bedrock(name)`.** Joins through the driver host, offline, and returns a `Client` once the spawn status arrived and the
  agent lists the player. The name is 1 to 15 characters from `A-Za-z0-9_` (a bad name is an `IllegalArgumentException` before
  anything connects). Floodgate prefixes one `.`, so the `Client` reports `.PsBed1` and `agent.player(client)` works. Clients from
  `bedrock` are disconnected after the test with the test's own client (an await on the agent, never a sleep). Nothing is reset for
  them.
- **Supported in v1:** `chat`, `command`, `awaitMessage`, `inventory`, `position`, disconnect. Others (`useBlock`, windows and clicks,
  `transferTo`, `resourcePacks`) throw `UnsupportedOperationException` naming the edition and ADR-0018.
- **Capability.** `BEDROCK_CLIENT` is declared by the Geyser pack and held when the Bukkit agent reports the token `floodgate`.
  `@Requires(BEDROCK_CLIENT)` on any other cell, including `paper@26.3` alone, is `SKIPPED` with `CAPABILITY_MISSING`
  (`missing capability BEDROCK_CLIENT on cell paper@26.3`). `clients.bedrock(..)` there without the declaration is an
  `IllegalStateException` naming the cell and the capability. A bridged cell is never a Geyser cell. `SIMULATED_BEDROCK` is
  unchanged and means a Java client under a `.` name.
- **Dual oracle.** Server: the agent's view of the player under the Floodgate name. Client: the Bedrock driver's inventory view from
  server-sent packets. `DualAssert.item` compares id and count; a difference Geyser's translation causes is asserted as it is
  and recorded in `geyser.md`, never weakened (T-M9-08). Disagreement is `DISAGREE`, as everywhere.
- **Auth label.** The cell is *offline-stub* (`cell.json`: `"bedrockAuth": "offline-stub"`): Xbox login validation is off. A plugin
  that depends on Xbox-verified identity cannot be tested here.
- **Refusals, as built.** Before anything starts, each names the Geyser cell and is an `IllegalStateException` (never a skip):
  a differential run, `-Dproofstone.repeat` above 1, a driver other than `mcpl`, a Java driver whose protocol differs from the
  row (a bridged join: ViaProxy is never put in front of a Geyser cell), and `mods` or `datapacks` on the class (`INVALID test
  declaration`). A bridged, loader or topology cell never holds `BEDROCK_CLIENT` whatever its agent reports, so
  `clients.bedrock(..)` there is the `IllegalStateException` above and `@Requires(BEDROCK_CLIENT)` skips.
- **Not held on a Geyser cell.** `SIMULATED_BEDROCK` (a Java client under a `.` name is not what a Geyser cell tests), `RESOURCE_PACKS`
  and `DATAPACKS_VANILLA` (no run showed them behind Geyser), `PROXY` and `TOPOLOGY`. A cell is reported and bundled as
  `geyser@2.12.1+paper@26.3`; a failing test writes `build/proofstone/<identity>/<Class.method>/` through `GeyserBundle`.
- **Waits.** `clients.bedrock(name)` first waits on the agent until `.name` is no longer listed (the server notices a disconnect about
  10 s late), joins, then waits until the agent lists it; after the test's close it waits until the agent forgets it, so the leak
  detector sees the state it started with. Each wait is an `await` of at most 30 s, never a sleep. A join timeout prints
  `PROOFSTONE BEDROCK JOIN TIMEOUT ... stage=<stage>` with the server, stand-in and driver-host logs: Bedrock join timeouts are
  counted apart from Paper, topology and loader numbers.
- **Bad test.** T-M9-11 adds the pinned faults (stand-in absent, and the others the card names).
