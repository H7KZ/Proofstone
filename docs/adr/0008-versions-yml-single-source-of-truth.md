# ADR-0008: `versions.yml` is the single source of truth for the matrix

- Status: Accepted
- Date: 2026-11-03

## Decision
- One file at the repository root lists, for each platform and version:
  - the build pin and checksum;
  - the host image digest;
  - the Java tag (from rules);
  - the protocol;
  - which drivers are native;
  - the status (`required`, `experimental` or `retired`);
  - notes.
- Core, CI matrices, docs tables and the GitHub Action all read this file. Nothing else hard-codes
  versions.
- The file is schema-validated in `check`. Spec: [versions-yml.md](../specs/versions-yml.md).
- The released `proofstone-core` jar embeds a copy. Users may pass an override file for unreleased
  versions.

## Consequences
- Adding a version is a data PR, mostly automated. A cheap model can review it, because the gates verify
  correctness.

## Alternatives considered
- **Versions in Java enums or annotations:** a code change for every release. Rejected.
