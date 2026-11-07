# Spec: self-tests (conformance, control run, differential run, flake detector)

These are what make a green result trustworthy ([robustness.md](../robustness.md)).

## 1. Conformance suite (per pack, per cell, before plugin tests)

It is cached per cell per CI run. If it fails, every plugin test on that cell gets the verdict `INVALID`.

Minimum scenarios for Java packs. Each one uses the dual oracle, with vanilla behaviour and no plugin:

| ID | Scenario | Proves |
|---|---|---|
| C01 | Agent `/v1/health`, plus the protocol version matches | agent loaded, protocol compatible |
| C02 | Client joins, runs `/help`, receives a system message | login, configuration and chat path |
| C03 | `give` round trip: agent gives an enchanted item, and agent and client both read the same enchants | item schema on both oracles |
| C04 | Chest: move a stack from slot A to slot B | window click and resync |
| C05 | Vanilla anvil: rename an item, check the result name and XP cost | anvil window, result slot, XP sync |
| C06 | Vanilla anvil: combine sword + enchanted book, check the vanilla result | result-slot semantics |
| C07 | Grindstone: remove enchants from an item | grindstone window |
| C08 | Shift-click result from a crafting table | non-standard click types |
| C09 | Event probe captures `PlayerJoinEvent` with getters | event subscription |
| C10 | Config patch + restore round trip (on a test fixture plugin shipped with the conformance suite) | config snapshots |
| C11 | Log capture: the fixture plugin logs a SEVERE, `assertNoErrorsLogged` fails, the allowlist works | error oracle |
| C12 | Reset leaves no residue: state before and after compares equal | isolation |

v0 limit for C04: a chest's own contents have no server oracle yet (there is no `openWindow` endpoint on the agent),
so C04 checks the move through server-answered clicks on the client window and dual-checks only the player inventory
(the stack must leave it on both oracles). A server-side chest check arrives with that endpoint.

v0 limit for C07: the grindstone arrived in 1.14, so 1.12.2 has no grindstone and cannot cover C07. The scenario
skips there with that reason (a visible skip, never a pass). Every row from 1.14 on runs it, and it is part of the
conformance evidence only on those rows (docs/platforms/paper.md, T-M3-05).

Each pack can add its own scenarios. For example, Folia adds cross-region teleport and region-thread
assertions.

## 2. Control run (nightly)

- Run the user's suite with every `plugins` jar **removed**. The agent and fixtures stay.
- Every test that is `PASS` in the control run gets the verdict `VACUOUS` and fails the nightly run.
- A test may declare `@ControlRun(EXPECTED_PASS, reason)` for genuine invariants, such as "vanilla
  behaviour unchanged when the feature is disabled". This is rare, and it is reported.

## 3. Differential run (nightly)

- For each cell where the reference driver is native, run the suite with `mcpl` (bridged or native) and
  with `mineflayer-ref`.
- Compare the client-oracle observations at each assertion point. Any difference gets the verdict
  `DISAGREE`, with both transcripts attached.

## 4. Flake detector (nightly)

- Repeat each test N=5 times per cell. N can be configured, and must be at least 3.
- Any verdict change gets the verdict `FLAKY` and fails the run, with all transcripts attached.

## 5. Mutation check (release only)

- The example projects contain toggleable injected bugs, such as an anvil-dupe flag or a wrong NMS
  mapping.
- The release pipeline enables each bug, and the suite **must fail**. If any mutation survives, the
  release is blocked.
