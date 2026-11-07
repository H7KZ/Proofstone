# ADR-0013: Host image digest is pinned per Java tag

- Status: Accepted (amends [ADR-0008](0008-versions-yml-single-source-of-truth.md); not a full supersession)
- Date: 2026-11-07
- Deciders: maintainer (resolution of ADR challenge [#23](https://github.com/H7KZ/Proofstone/issues/23))

## Context
ADR-0008 and `docs/specs/versions-yml.md` give each platform one `host.digest`. The `itzg/minecraft-server` image is
published once per Java tag (`java8`, `java17`, `java21`, `java25`, ...), and each tag has its own digest. With one
digest, `versions.yml` pinned `java25`, so only the Paper 26.x rows could run, and `PaperHost` correctly failed on the
rest (T-M1-07, issue #23).

## Decision
- `platforms.<id>.host.digest` is replaced by `host.digests`, a map from Java tag to digest. A cell uses the digest of
  its row's Java tag (the `java` override, else the first matching `java-rules` entry).
- A tag the rules can assign must have a key. A missing or `TODO` digest for a tag in use is an error that names the
  tag and row. There is no fallback to another tag.
- Everything else in ADR-0008 stands: one file, schema-validated, read by core, CI and docs.

## Consequences
- Every Paper row can run on the Java it needs, so the span (1.8.8 to newest) becomes reachable.
- `versions.yml`, the schema, the validator, `VersionsCatalog`, `CellPlanner` and `PaperHost` change together.
- Digests move with every image rebuild, so Renovate (base images by digest) or the version-watch task keeps them fresh.

## Alternatives considered
- **A multi-arch manifest list digest per tag only for the newest tag:** still one tag. Rejected.
- **Hard-code a Java tag per row:** the rules already express it (ADR-0008). Rejected.
