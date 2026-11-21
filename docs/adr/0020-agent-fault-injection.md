# ADR-0020: Agent fault injection for "a write fails" scenarios

- Status: Proposed
- Date: 2026-11-21
- Deciders: maintainer

## Context
A plugin's error path ("the write of the result failed, so refund the payment") cannot be reached from a real-server test: no
public API makes a server-side write fail. Disenchantment commit `4352679` has such a refund, and its mutation check M9 (remove the
refund) stayed green. Spike T-M11-10 ([fault-injection.md](../platforms/fault-injection.md)) found:

- The Bukkit write calls the plugin uses (`Inventory.setItem`, `HumanEntity.setItemOnCursor`) fire no event, so no listener can
  make them throw.
- Swapping the delegate list inside a container's `NonNullList` for one that throws once works on paper 26.3, but not for the
  cursor, not before 1.11, needs a write to a `final` field (JEP 500 restricts it from Java 26), and also catches vanilla's writes.
- [ADR-0002](0002-server-truth-via-agent.md) says agents observe and never interfere and are not called during act.

## Decision
Proposed, not taken: **keep ADR-0002 as is. Agents do not inject faults.** Suites that need a failing write use a test support
plugin of their own (as Disenchantment's `*-hooks.jar`), and Proofstone only reads the outcome (log lines, balance, levels, items).

If the maintainer instead wants fault injection in Proofstone, this ADR becomes an amendment of ADR-0002 with these limits:
one-shot guards armed in *arrange*, scoped to one player and one raw slot of the open window, restored by the lifecycle at test end
whatever happens, reported through a capability token so unsupported cells skip with a reason, and never used by the server oracle.

## Consequences
- Rejected amendment (the proposal): no agent change; the gap stays closed by suite-side plugins; Proofstone docs point there.
- Accepted amendment: a protocol message and version bump, an `api-change` card, per-version reflection in the Bukkit agent that
  has to be re-verified on every new Java tag, and a lifecycle obligation to disarm.

## Alternatives considered
- **Event-driven guard:** impossible, the writes fire no event.
- **Throwing earlier in the same click:** tests "charge refused", not "write failed after charge".
- **Bytecode instrumentation (attached Java agent):** a new runtime dependency, version-specific, warns on dynamic loading.
