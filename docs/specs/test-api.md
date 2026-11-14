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
  and the run prints `PROOFSTONE NOT SELECTED`. The selector is not a skip: nothing is reported for those cells.
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
agent.logLines(Pattern.compile("NMS module: (\\S+)"));   // for per-cell expectations
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
missing, Docker unavailable, EULA not accepted, or the cell was not selected.

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
- Constructors of the records `PlayerState`, `CapturedEvent`, `ItemView`: they will gain components (`location`,
  `openWindow`). Test authors read them and never construct them. Accessors that exist today are frozen.
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
- `Agent.plugin(..).reload()`, `Agent.awaitTicks`, `Agent.logLines` and the explicit `Agent.assertNoErrorsLogged()`
  (the per-test log check runs automatically; the explicit call is not exposed).
- `PlayerHandle.gamemode`, `PlayerState.location`, `PlayerState.openWindow` and a server view of an open window (the
  agent has no `openWindow` endpoint, so the chest contents have no server oracle).
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
  returns in M8 with the token `datapacks` (ADR-0017 §8, T-M8-09).
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

Design from [ADR-0017](../adr/0017-mod-platform-packs.md); nothing here is in `api-baseline.txt` yet. Each card adds its part
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

- **New constants:** `Platform.FABRIC`, `Platform.NEOFORGE`; `Capability.MODS`, `Capability.DATAPACKS_VANILLA`,
  `Capability.RESOURCE_PACKS` (tokens `mods`, `datapacks`, `resource-pack-status`; ADR-0017 §8).
- **`@Proofstone(mods, datapacks)`:** globs relative to the project directory, exactly one match each, as `plugins`. Mods go to
  `/mods`, data packs (a `.zip` or a directory with `pack.mcmeta`) to the world's `datapacks` before start. Declaring either
  implies `@Requires(MODS)` or `@Requires(DATAPACKS_VANILLA)`: a cell without it is `SKIPPED` with `CAPABILITY_MISSING`.
  `plugins` on a loader cell is `INVALID test declaration`. A test mod with the id `fabric-api` is refused (the Fabric cell
  provides the pinned Fabric API).
- **Loaded or loud.** A declared mod missing from the agent's mod list, or a declared data pack not enabled, makes the class's
  tests on that cell `INVALID`, naming the artefact. A mod that crashes the loader is a failed boot, `INVALID` naming the
  mod. A data pack that loads but logs an error fails the test through the log guard (`FAIL`).
- **`@ServerResourcePack`** (class): the pack is served by the harness on loopback and pushed by `server.properties`
  (ADR-0017 §7). Implies `@Requires(RESOURCE_PACKS)`.
- **`@ResourcePackAnswer`** (class or method; `ACCEPT`, `DECLINE`, `FAIL_DOWNLOAD`): how every client of the test answers a push.
  With no answer declared, a push ends the join with an error naming the URL; the driver never accepts silently.
- **`Client.resourcePacks()`** (default method): the pushes this client saw, in order, with what it answered. Packs pushed by a
  plugin are recorded the same way. The server's view is read with the event probe on
  `org.bukkit.event.player.PlayerResourcePackStatusEvent` (Bukkit agent).
- **Content mods.** On Fabric a mod that adds a synced registry entry gets the client kicked at join: the test fails with the
  kick message. On NeoForge it is `unverified`; a mod-added item reaching the client is an error naming the item id.
