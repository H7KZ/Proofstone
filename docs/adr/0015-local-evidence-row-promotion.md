# ADR-0015: A row is promoted to `required` on local per-row evidence

- Status: Accepted
- Date: 2026-11-07
- Deciders: maintainer (delegated the decision to the M3 follow-up agent, card `card/m3-followups`)

## Context
- `docs/versioning.md` section 2, step 4 said a row is promoted to `required` after 3 green nightly runs in a row.
- Nightly is manual-only since PR #40 (billed CI minutes). A scheduled run never happens, so the 3-run rule cannot be
  met. No `promote` workflow exists.
- The M3 review (ROADMAP, "M3 review") already holds full evidence for the Paper span: each row 1.8.8 to 26.3 ran
  C01 to C08 in its own `:conformance:run`, with XML checked for `failures="0" errors="0"`, and skips were reasoned
  (C07 on 1.8.8 and 1.12.2: no grindstone before 1.14).
- ADR-0008 says core, CI and docs read `versions.yml`, and that `required` rows block a PR. The status meaning is
  unchanged by this ADR.

## Decision
- Amends [ADR-0008](0008-versions-yml-single-source-of-truth.md) for the promotion rule only. ADR-0008's other
  decisions stand.
- A row moves from `experimental` to `required` when a full per-row `./gradlew :conformance:run` with the cell
  selector (`-Dproofstone.cells=<platform>@<mc>`) passes locally on that row, with:
  - C01 to C08 all run (no missing check);
  - no failure and no error in the JUnit XML (`failures="0" errors="0"`);
  - every skip carrying a reason in the XML, and the reason allowed by the spec (for example, C07 before 1.14);
  - the run repeated only as the row requires, never to turn red into green (AGENTS.md section 3).
- The evidence is recorded as an XML-based table (row, bridged, C01 to C08, skip reasons) in the ROADMAP review or in
  the PR that changes the rows. The XML files themselves are not committed.
- Nightly, when run again manually, is an extra signal. It is not a promotion gate.
- A `required` row that later goes red blocks PRs like any other. Demotion to `experimental` is a `versions.yml` PR
  with the failing run recorded; it does not need an ADR.

## Consequences
- Promotion needs a maintainer or agent with Docker and the EULA in the command environment. The cost moves from
  billed CI to local time, which is the cheaper resource here.
- The evidence is a human-read table, not a CI check. The `versions.yml` diff and the PR text are the audit trail. A
  future `promote` workflow may automate this only through a new card.
- The PR matrix now plans a cell per `required` row (CI matrix, `printMatrix --scope=pr`). The PR job still runs only
  on manual dispatch (PR #40), so no billed run starts.

## Alternatives considered
- **Keep the 3-nightly rule and schedule nightly again:** rejected, billed minutes (PR #40).
- **Promote on a single green run:** rejected, one run does not show flake freedom (ROADMAP M3 review, item 6: C06 on
  1.8.8 failed about 1 run in 4 before PR #39).
- **Promote only through an automated workflow now:** rejected for this milestone; no card approves it, and it would
  need CI minutes to run the Docker suites.
