# ADR-0007: No sleeps, no auto-retries; flakes are bugs

- Status: Accepted
- Date: 2026-11-03

## Context
- The prototype failed with a fixed 500 ms sleep after `closeWindow` on 1.18.2 (a stale inventory), and
  once hit a 20 s `windowOpen` timeout.
- Replacing sleeps with predicate polling fixed both.

## Decision
- All synchronisation goes through `Await.until(description, predicate, timeout)` in core, or the agent's
  `awaitTicks`.
- A failure message includes the description, the elapsed time and the last value from each oracle.
- CI lint forbids `Thread.sleep`, `TimeUnit.*.sleep` and `setTimeout`-based waits outside `Await`.
- **No retry on failure.** The nightly flake detector repeats each test N=5 times on purpose, and any
  change in verdict is reported as `FLAKY`, which fails the run.
- Default timeouts are generous: 10 s for client sync and 60 s for boot. Speed is not the goal.

## Consequences
- Slower tests, and flakes surface instead of hiding.

## Alternatives considered
- **Retry-until-green (common in e2e frameworks):** hides real races in plugins. Rejected.
