# Spec: public test API (`proofstone-api`) v0

Status: **v0 frozen at T-M2-99** (end of M2). The frozen contract is `proofstone-api/api-baseline.txt`, guarded by
`ApiSurfaceTest`; section 8 lists what is frozen, what is experimental, and where the sketches in sections 3 to 5 go
beyond v0. After the freeze, a change to the baseline needs a card marked `api-change` and a reviewed baseline diff.

## 1. Annotations

```java
@Proofstone(
    plugins   = {"target/MyPlugin-*.jar"},     // globs relative to project dir; exactly one match each, else error
    mods      = {},                            // for mod packs
    datapacks = {},
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
| `Topology` | per invocation | multi-server cells (proxy and backends): `topology.server("lobby").agent()` |

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
`proofstone-api/api-baseline.txt` lists (every public member of `dev.proofstone.api`). `ApiSurfaceTest` fails on any
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
- `dev.proofstone.api.spi.OracleWaiter` and its records: a service provider interface for `proofstone-core`, not for
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
- `Cell` has no `capabilities()` or `driver()`; `Agent.capabilities()` is the capability list.
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
- `Clients` (multi-player) and `Topology` (proxies).
- Test hygiene: the world fixture region reset and the leak detector (T-M2-08 left them out).
- `Cell.capabilities()`, `Cell.driver()`, `@Requires` and `Capability`.
- Verdict reporting (`serverOnlyReasons` is its stand-in).
