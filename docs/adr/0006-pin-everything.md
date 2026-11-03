# ADR-0006: Pin everything; upgrades only through gated PRs

- Status: Accepted
- Date: 2026-11-03

## Decision
- Every runtime input is pinned:
  - images by digest;
  - server builds by number and SHA-256;
  - Java tags;
  - driver libraries;
  - ViaProxy;
  - the Mineflayer reference version.
- Pins live in `versions.yml` and the Gradle version catalog.
- Pins change only through automated PRs (`version-watch` or Renovate) that must pass conformance.
- Runtime code never resolves `latest`, ranges or `+`. The test API word `latest` resolves to a pinned row.

## Consequences
- Identical inputs give identical verdicts, and a repro bundle fully describes a run.
- More PR traffic, which is mostly automated and small enough for a cheap model to handle.

## Alternatives considered
- **Floating versions:** a non-reproducible red build at 3 a.m. Rejected.
