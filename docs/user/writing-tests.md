# Writing tests

This page lists the API a test can use **today**: the frozen v0 contract in
[`proofstone-api/api-baseline.txt`](../../proofstone-api/api-baseline.txt) and [docs/specs/test-api.md](../specs/test-api.md).
Everything else in the spec's sketches is not available yet. Do not use it.

The shape to copy is [`AnvilMechanicE2E.java`](../../examples/gradle-paper-plugin/src/e2eTest/java/io/github/h7kz/proofstone/examples/gradle/AnvilMechanicE2E.java).

## Anatomy of a test class

```java
@Proofstone(plugins = "build/libs/anvil-mechanic.jar")          // exactly one jar must match the glob
@Matrix(platform = Platform.PAPER, versions = {"26.3"})         // repeatable; "latest" and "all" also work
class AnvilMechanicE2E {

    @CellTest                                                   // one invocation per selected cell
    void ironAndGoldOnTheAnvilMakeOneDiamond(Agent agent, Client client) {
        PlayerHandle player = agent.player(client).reset();
        player.give(Item.of("iron_ingot")).give(Item.of("gold_ingot"));

        try (Window anvil = client.openBlock(new BlockPos(0, 64, 2))) {
            anvil.moveFromInventory(item -> item.is("iron_ingot"), 0);
            anvil.moveFromInventory(item -> item.is("gold_ingot"), 1);
            ItemView result = anvil.awaitSlot(2, item -> item.is("diamond"));
            assertThat(result.amount()).isEqualTo(1);
        }
    }
}
```

- **`@Proofstone(plugins = ...)`** names the jar under test, relative to the test project directory. The glob must match
  exactly one file, or the run fails.
- **`acceptEula`** also exists on `@Proofstone`. This repository's examples set the EULA through the environment only
  (see [troubleshooting.md](troubleshooting.md#eula-not-accepted-tests-are-skipped)).
- **Test parameters** are injected: `Cell`, `Agent`, `Client`. Declare only what you use.

## Choosing servers: `@Matrix` and the selector

- `@Matrix(platform = ..., versions = {...})`. `Platform` has `PAPER`, `FOLIA`, `PURPUR` and `SPIGOT` only.
- `versions` takes exact versions, `latest` (the newest `required` row) or `all` (every row in scope).
- At run time, `-Dproofstone.cells=paper@26.3,folia@latest` **replaces** the `versions` of every matrix whose platform it
  names. A matrix whose platform is not named is not run, and the run prints `PROOFSTONE NOT SELECTED`. That is not a pass.
- The rows that are `required` today are listed in
  [docs/plan/tasks/M6-dogfood-release.md](../plan/tasks/M6-dogfood-release.md) and in `versions.yml`.

## Capabilities: `@Requires`

```java
@Requires(Capability.BUKKIT_EVENTS)          // on a class: every test in it
class EventsE2E {
    @CellTest
    @Requires({Capability.CONSOLE, Capability.FOLIA_REGIONS})   // on a method: added to the class's list
    void scenario(Cell cell, Agent agent) { ... }
}
```

A cell that lacks a required capability is **skipped with a reason**. The capabilities are `BUKKIT_EVENTS`, `CONSOLE`,
`FOLIA_REGIONS` and `SIMULATED_BEDROCK`. `SIMULATED_BEDROCK` is declared by the Paper pack on native cells only.

## The server oracle: `Agent`

All calls block until the agent confirms, and they return snapshots.

```java
PlayerHandle p = agent.player(client);              // or agent.player("Name"); both forms exist
p.reset();                                          // clears inventory, cursor and XP, resets to the fixture spot
p.level(30);
p.give(Item.of("diamond_sword").enchant("sharpness", 5));
PlayerState s = p.state();                          // level, exp, health, gamemode, inventory, cursor, name
PlayerState s2 = p.awaitState("book in inventory",
        st -> st.inventory().values().stream().anyMatch(i -> i.is("enchanted_book")));

agent.command("gamerule keepInventory true");       // setup only: returns raw text, never assert on it
agent.plugin("AnvilMechanic").patchConfig("bugs.double-output", true);   // restored after the test
String value = (String) agent.plugin("AnvilMechanic").config("some.key"); // reads a config value

EventProbe e = agent.events().subscribe("org.bukkit.event.player.PlayerJoinEvent");
CapturedEvent ev = e.awaitOne();
int xp = ev.getInt("xpCost");                        // an unknown getter is an error, never a default
```

- `Agent.command(String)` returns the raw console reply as a `String`. It is for setup. Never assert on it.
- Probes are read-only and listen at MONITOR priority.
- Not in v0: `agent.world()`, `agent.awaitTicks`, `agent.logLines`, `PlayerHandle.gamemode`, `PlayerState.location`,
  `PlayerState.openWindow`, `Item.pdc` and `Item.repairCost` setters, `Clients` (several players). Proxies are covered in [topologies](topologies.md) (experimental).
  Use `agent.command(...)` for setup instead of `world()`.

## The client oracle: `Client` and `Window`

```java
client.command("version MyPlugin");                                  // sends "/version MyPlugin"
client.awaitMessage(Pattern.compile("MyPlugin version"));           // returns the matched chat line
Position at = client.awaitPosition("teleported", p -> p.y() == 64.0);
int level = client.experienceLevel();                               // throws until the first XP sync
List<ItemView> items = client.inventory();

try (Window w = client.openBlock(new BlockPos(0, 64, 2))) {         // awaits the window opening
    w.type();                                                       // e.g. "minecraft:anvil"
    w.moveFromInventory(i -> i.is("diamond_sword"), 0);             // pick up and place, server-authoritative
    ItemView r = w.awaitSlot(2, i -> i.is("enchanted_book"));
    w.click(2, Click.LEFT);                                         // LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, DROP,
                                                                    // DOUBLE, SWAP_OFFHAND, NUMBER_1..NUMBER_9
    w.cursor();                                                     // ItemView (empty when nothing is held)
    w.dropCursorIntoFirstEmptySlot();
    w.rename("Blade");                                              // anvil only
}                                                                   // close() sends the close
```

Notes from the frozen API:

- `Window.close()` returns after sending the close. It does not wait for the server (a later change may add that).
- `Client.openBlock` opens on the block's top face.
- `ItemView.isEmpty()`, `is(String)`, `amount()`, `type()`, `name()`, `lore()`, `enchants()`, `storedEnchants()` and
  `damage()` read an item. `ItemView.pdc()` is server only and `customData()` is client only.

## Checking both oracles: `DualAssert`

```java
DualAssert.item(agent.player(client), client)
    .inInventory(i -> i.is("enchanted_book"))
    .storedEnchants(Map.of("sharpness", 5, "unbreaking", 3));  // checked on both oracles
DualAssert.level(agent.player(client), client).isEqualTo(17);
```

- Both sides are awaited with `DualAssert.DEFAULT_TIMEOUT` (10 s).
- A mismatch between the two oracles is `DISAGREE`. A failure where neither oracle matched is `FAIL`. An oracle that never
  answered is `ORACLE MISSING, not a pass`. Verdicts are explained in [troubleshooting.md](troubleshooting.md#verdicts).
- `serverOnly(reason).pdc(...)` checks a value on the server only, with a reason, for data clients never see.

## Extra checks that exist

- **`@ControlRun(ControlRun.Expectation.EXPECTED_PASS, reason)`** marks a test that should pass without the plugin, such
  as "vanilla behaviour is unchanged". It is rare and it is reported.
- **`@Expect(MyCheck.class)`** runs a `CellExpectation` after a green test, to check what the suite expects for that cell.
  The check must be read-only and have a public no-argument constructor (test-api spec section 9).
- **`@Isolation(PER_CLASS | PER_TEST)`** sets how often the server is reused. Before each test that declares a `Client`, a fresh
  client joins and the player is reset. After it, the log is checked for WARN and worse lines, configs are restored and the
  client disconnects.

## Rules that the harness enforces

- **No sleeps.** Wait with `awaitState`, `awaitSlot`, `awaitPosition` or `awaitMessage`, which take a description and a
  predicate. Waiting for a fixed time is wrong.
- **Never retry a test.** A failing test is reported as failing. A verdict that changes between runs is `FLAKY`.
- **Fail loudly.** An unknown getter, an unknown selector or an empty reply is an error, never a pass.
- **Keep the plugin test-only.** Plugin jars under test are never shipped.
