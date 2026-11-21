# Fault injection: "the next inventory write fails" (spike T-M11-10)

Written 2026-11-21. **Verdict: No-go for an agent API now.** A narrow mechanism works in a JVM spike, but it is not generic or
robust enough, and an agent that makes a write fail interferes with the act phase, which [ADR-0002](../adr/0002-server-truth-via-agent.md)
forbids. No API, protocol or agent change was made. [ADR-0020](../adr/0020-agent-fault-injection.md) (Proposed) records the decision a
maintainer would have to take first.

## The need

Disenchantment commit `4352679` builds the delivered items, charges economy and levels, then writes the slots. If the write
throws, it refunds both and rethrows. Its e2e mutation M9 (remove the refund) stayed GREEN because no Proofstone API can make the
write fail (Disenchantment `docs/engineering/E2E.md`, "Proofstone gaps" item 3, branch `test/proofstone-e2e-mutation`). The write
the plugin makes, from the `InventoryClickEvent` handler (`DisenchantClickEvent`, `ShatterClickEvent`, `EraseClickEvent`):

```java
anvilInventory.setItem(0, newFirst);   // CraftInventory.setItem
p.setItemOnCursor(delivered);          // CraftHumanEntity.setItemOnCursor
```

## What was tried

| Candidate | Result | Evidence |
|-----------|--------|----------|
| A guard listener that throws from an event the write fires | **No.** Neither call fires any event. | `javap -c` on paper 26.3: `CraftInventory.setItem` is `asNMSCopy` then `Container.setItem`; `setItemOnCursor` is `asNMSCopy`, `AbstractContainerMenu.setCarried` (a bare `putfield carried`), `broadcastCarriedItem`. `CraftInventory`, `CraftInventoryAnvil` and `CraftResultInventory` contain no `callEvent` or `CraftEventFactory` call. |
| Throwing from an earlier event of the same click (`InventoryClickEvent` at a high priority) | **No.** It fails the click before the plugin charges, so it tests the "charge refused" path, not "write failed after charging". It also breaks ADR-0002 (listeners are read-only at MONITOR). | Order in `AnvilOperations.settle`: Pre event, build, `takePayment`, `applier.apply`. |
| Wrapping the `Player` or `Inventory` the plugin sees | **No.** The server hands the plugin its own `CraftPlayer` and `CraftInventory`; an agent cannot substitute them. | Bukkit event objects are built by the server. |
| Swap the delegate list inside the container's `NonNullList` for one whose `set` throws once | **Works mechanically, narrow.** See below. | Spike, paper 26.3 jar on Java 25. |
| Bytecode instrumentation (attach a Java agent, ByteBuddy) | **No.** A new runtime dependency, dynamic agent loading warns from Java 21, version-specific targets. | AGENTS.md section 3 (no new runtime dependency without an ADR). |

### The `NonNullList` spike

`net.minecraft.core.NonNullList` holds a `private final java.util.List<E> list`, and `NonNullList.set` is
`requireNonNull` then `list.set`. `SimpleContainer.setItem` and `ResultContainer.setItem` both write through it. The field can be
found by type, without a mapped name. A throwaway program on the paper 26.3 server jar (Java 25.0.2):

```text
delegate field by type: private final java.util.List net.minecraft.core.NonNullList.list
write 1 threw: java.lang.IllegalStateException: proofstone: injected write failure at slot 0
slot0 after failed write: empty
write 2 (one-shot disarmed): slot0=sword
restored, identity same: true, slot0=sword
```

The same output came from the folia 26.3 and spigot 26.3-R0.1 server jars. Only 26.x jars were tried; older rows (obfuscated
names, 1.11+) are inferred, not run. No server was booted: the spike proves the mechanism, not a click through it.

So an agent could arm "the next write to slot N of player X's open top inventory throws", and Disenchantment's first write
(`anvilInventory.setItem(0, …)`) would fail after the charge.

## Why that is still a No-go

- **ADR-0002.** Agents "observe and never interfere" and "are not called during the act phase". An armed guard changes what the
  server does during act. Only a maintainer can amend that (AGENTS.md section 2: do not re-decide an accepted ADR).
- **Not generic.** The cursor write (`setCarried`, a bare field store) cannot fail this way. Containers before 1.11 hold an
  `ItemStack[]`, so 1.8.8 to 1.10 rows have no hook. A plugin that writes only the cursor, or writes through the player's
  inventory, needs a different target per call site.
- **Not robust.** It mutates a `final` instance field. JEP 500 (Java 26) warns on that by default and a later release denies it
  without `--enable-final-field-mutation`, so future Java tags of the 26.x rows can break it. The guard sees vanilla's writes too:
  the anvil recomputes its result and the server applies the click through the same lists, so "next write" can be taken by the
  server instead of the plugin and then fails inside vanilla code, not the code under test. On Folia it must be armed and disarmed
  on the player's region thread.
- **Leaks across tests.** A cell serves several tests. A guard left armed (test aborted, window closed, wrong slot) corrupts the
  next test unless the lifecycle always restores it.

## What a suite can do today

A test support plugin of the suite itself is not bound by ADR-0002. Disenchantment already ships two (`*-vault.jar`, `*-hooks.jar`).
Its hooks plugin can arm the same `NonNullList` swap on the open anvil's input container from a `PreDisenchantEvent` listener (fired in
the same click, before the charge) and disarm it on the next tick. That closes M9 without any Proofstone change, and the suite owns
the fragility. Proofstone's part stays read-only: `Agent.logLines` sees the `Could not pass event InventoryClickEvent` stack trace,
the server oracle sees the balance and levels.

## If ADR-0020 is accepted

Then a follow-up card adds, as throwing default methods: `Agent.failNextSlotWrite(PlayerHandle, int rawSlot)` returning an
`AutoCloseable` armed guard, Bukkit-family only (1.11+), a capability token `fault-slot-write`, a new protocol message (version bump
and goldens), lifecycle restore on test end, and conformance on paper@26.3 and folia@26.3. Cursor writes stay out of scope.
