# Spec: public test API (`testificate-api`) v0

Status: draft. It is frozen at the end of M2. After that, changes need a card marked `api-change`.

## 1. Annotations

```java
@Testificate(
    plugins   = {"target/MyPlugin-*.jar"},     // globs relative to project dir; exactly one match each, else error
    mods      = {},                            // for mod packs
    datapacks = {},
    acceptEula = true,                         // REQUIRED true, or env TESTIFICATE_ACCEPT_EULA=true; else tests are skipped with reason
    config    = "src/e2e/resources/testificate.yml"   // optional per-project settings (version families, expectations)
)
@Matrix(platform = PAPER, versions = {"1.18.2", "1.21.11", "latest"})   // repeatable
@Matrix(platform = FOLIA, versions = {"latest"})
@Isolation(PER_CLASS)                          // or PER_TEST
class MyE2E { ... }

@CellTest                                      // = @TestTemplate, one invocation per cell
@Requires({Capability.BUKKIT_EVENTS})          // a cell without the capability is SKIPPED with a reason (never a pass)
void scenario(Cell cell, Agent agent, Client client) { ... }
```

- `versions` accepts exact versions, `latest` (the newest `required` row) and `all` (every row in range).
- `-Dtestificate.cells=paper@1.21.11,folia@latest` narrows the run at runtime.
- An unknown version is an error, not a skip.

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
}                                                // close() awaits server close + inventory resync
client.inventory();  client.experience();  client.position();
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
